package app.delayd

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Foreground service that watches location with a battery-aware duty cycle (see [DutyCycle]).
 * Finished rides go to a persistent queue and are looked up on a worker thread with backoff.
 */
class TrackingService : Service(), LocationListener {
    private lateinit var lm: LocationManager
    private val detector = TripDetector(::onRide)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var drainTask: ScheduledFuture<*>? = null
    private var current: Pair<DutyCycle.Plan, String>? = null
    private var watchUntil = 0L
    private var motion: Sensor? = null
    private var foreground = false
    private var stopping = false
    private val endWatch = Runnable { updateRate() }
    private val onMotion = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            main.post {
                watchUntil = SystemClock.elapsedRealtime() + DutyCycle.WATCH_MS
                main.removeCallbacks(endWatch)
                main.postDelayed(endWatch, DutyCycle.WATCH_MS)
                armMotion() // trigger sensors fire once
                updateRate()
            }
        }
    }
    private val online = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = drainSoon(0)
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(LocationManager::class.java)
        TripStore.init(this)
        Notifier.channels(this)
        try {
            val n = Notifier.tracking(this)
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notifier.ID_TRACKING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(Notifier.ID_TRACKING, n)
        } catch (e: RuntimeException) { // not allowed to start unseen (Android 12+/14+): ask for a tap instead of crashing
            Log.w(TAG, "foreground start refused", e)
            Notifier.resume(this)
            stopSelf()
            return
        }
        foreground = true
        setWanted(this, true)
        Notifier.clearResume(this)
        running.value = true
        motion = getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        armMotion()
        try {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(online)
        } catch (e: RuntimeException) {
            Log.w(TAG, "no network callback", e)
        }
        updateRate()
        drainSoon(0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        lm.removeUpdates(this)
        stopping = true
        detector.flush() // a ride in progress is closed at the last fix, not lost
        inProgress = null
        Pipeline.arm(this) // nobody drains once we are gone, so the job does
        main.removeCallbacksAndMessages(null)
        if (foreground) {
            // only a deliberate stop gets here; a kill by the system or an update skips onDestroy, so we come back
            setWanted(this, false)
            motion?.let { getSystemService(SensorManager::class.java).cancelTriggerSensor(onMotion, it) }
            try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(online) } catch (_: RuntimeException) {}
        }
        worker.shutdownNow()
        running.value = false
        riding.value = false
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        if (location.accuracy > 500) return
        detector.add(Fix(Instant.ofEpochMilli(location.time), location.latitude, location.longitude))
        updateRate()
    }

    // a switched-off provider is replaced by the other one; API 26-29 have no default implementations
    override fun onProviderEnabled(provider: String) = updateRate()
    override fun onProviderDisabled(provider: String) = updateRate()
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun armMotion() {
        motion?.let { getSystemService(SensorManager::class.java).requestTriggerSensor(onMotion, it) }
    }

    @SuppressLint("MissingPermission") // MainActivity only starts us after the grant
    private fun updateRate() {
        if (!foreground) return
        val plan = DutyCycle.plan(detector.moving, SystemClock.elapsedRealtime() < watchUntil, motion != null)
        riding.value = detector.moving
        inProgress = detector.current
        val provider = DutyCycle.provider(plan.gps, lm.isProviderEnabled(LocationManager.GPS_PROVIDER),
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        if (current == plan to provider) return
        current = plan to provider
        lm.removeUpdates(this)
        try {
            lm.requestLocationUpdates(provider, plan.intervalMs, 0f, this, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w(TAG, "location permission revoked", e)
            stopSelf()
        }
    }

    private fun onRide(ride: Ride) {
        if (stopping) return Pipeline.queue(this).add(ride) // the worker is going away; the queue write is small
        worker.execute {
            Pipeline.queue(this).add(ride)
            drain()
        }
    }

    @Synchronized
    private fun drainSoon(delayMs: Long) {
        try {
            drainTask?.cancel(false)
            drainTask = worker.schedule(::drain, delayMs, TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {} // service already gone
    }

    private fun drain() {
        val wake = try {
            Pipeline.process(this, detector.current)
        } catch (e: Exception) { // whatever one ride does, the service and the other rides go on
            Log.e(TAG, "lookup failed", e)
            null
        }
        checkMonthly()
        drainSoon(wake?.let { Duration.between(Instant.now(), it).toMillis().coerceAtLeast(1_000) } ?: IDLE_CHECK_MS)
    }

    private fun checkMonthly() {
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dTicket", true)) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        Compensation.monthlyClaim(TripStore.trips.value, Instant.now(), ZONE, prefs.getString("claimNotified", null))?.let { (month, ct) ->
            Notifier.monthlyClaim(this, month, ct)
            prefs.edit().putString("claimNotified", month.toString()).apply()
        }
    }

    companion object {
        private const val TAG = "Delayd"
        private const val PREFS = "core"
        private const val IDLE_CHECK_MS = 6 * 3_600_000L
        private val ZONE = ZoneId.of("Europe/Berlin")
        const val EXTRA_TRIP_ID = "tripId"
        val running = MutableStateFlow(false)
        /** True while a ride is in progress. */
        val riding = MutableStateFlow(false)
        /** Start of the ride in progress; a journey it may continue is held back from the lookup, also when the app drains. */
        @Volatile var inProgress: Fix? = null

        /** The user had detection on and the system, not the user, stopped it (reboot, update). */
        fun wanted(c: Context) = c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("detectionOn", false)
        private fun setWanted(c: Context, on: Boolean) = c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("detectionOn", on).apply()
    }
}
