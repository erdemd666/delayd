package app.delayd

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONException
import java.io.IOException
import java.time.Instant
import kotlin.concurrent.thread

/**
 * Foreground service that watches location with a battery-aware duty cycle:
 * idle = cheap network fixes once a minute, riding = GPS every 10 s to pin down the arrival.
 */
class TrackingService : Service(), LocationListener {
    private lateinit var lm: LocationManager
    private val detector = TripDetector(::onRide)
    private var gpsMode: Boolean? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        TripStore.init(this)
        lm = getSystemService(LocationManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TRACK, "Ride detection", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_DELAY, "Delays", NotificationManager.IMPORTANCE_DEFAULT))
        val n = notification(CH_TRACK, "Watching for train rides", "Delayd notices delays for you")
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, n)
        running.value = true
        updateRate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        lm.removeUpdates(this)
        running.value = false
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        if (location.accuracy > 500) return
        detector.add(Fix(Instant.ofEpochMilli(location.time), location.latitude, location.longitude))
        updateRate()
    }

    // API 26-29 have no default implementations
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    @SuppressLint("MissingPermission") // MainActivity only starts us after the grant
    private fun updateRate() {
        val gps = detector.moving
        if (gps == gpsMode) return
        gpsMode = gps
        lm.removeUpdates(this)
        val provider = if (!gps && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
        try {
            lm.requestLocationUpdates(provider, if (gps) 10_000L else 60_000L, 0f, this, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w(TAG, "location permission revoked", e)
            stopSelf()
        }
    }

    private fun onRide(ride: Ride) = thread {
        // shortcut: retries live in memory, a ride is lost if the process dies offline; persist pending rides if that bites
        repeat(3) {
            try {
                val trip = Transitous.findTrip(ride) ?: return@thread
                TripStore.add(trip)
                if (trip.delayMin >= 60) notifyDelay(trip)
                return@thread
            } catch (e: IOException) {
                Log.w(TAG, "lookup failed, retrying", e)
                Thread.sleep(60_000)
            } catch (e: JSONException) {
                Log.e(TAG, "unexpected API response", e)
                return@thread
            }
        }
    }

    private fun notifyDelay(trip: Trip) {
        val n = notification(CH_DELAY, "${trip.train} was ${trip.delayMin} min late",
            "${trip.from} → ${trip.to}. You can claim compensation.")
        getSystemService(NotificationManager::class.java).notify(trip.hashCode(), n)
    }

    private fun notification(channel: String, title: String, text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(channel == CH_DELAY)
            .build()
    }

    companion object {
        private const val TAG = "Delayd"
        private const val CH_TRACK = "tracking"
        private const val CH_DELAY = "delays"
        val running = MutableStateFlow(false)
    }
}
