package app.delayd

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Rides in, trips and notifications out. The service, the app and the wake-up job share this path. */
object Pipeline {
    private var queue: RideQueue? = null
    private val lock = Any()
    private val busy = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor()
    private const val JOB_ID = 7301
    private val MIN_WAIT: Duration = Duration.ofSeconds(30)

    @Synchronized
    fun queue(c: Context) = queue ?: RideQueue(File(c.applicationContext.filesDir, "pending.json")).also { queue = it }

    /** Looks up due rides; returns when to run again (see [RideQueue.drain]). One run at a time, and the next wake-up is armed. */
    fun process(c: Context, inProgress: Fix?, find: (Ride) -> Trip? = Transitous::findTrip): Instant? = synchronized(lock) {
        TripStore.init(c)
        queue(c).drain(inProgress, find) { trip ->
            if (TripStore.add(trip) && trip.delayMin >= 60) Notifier.delay(c, trip)
        }.also { arm(c, it) }
    }

    /**
     * Drains the queue on a background thread, with detection on or off. Safe to call from any thread and as often as
     * you like: a call while a run is going does nothing. [done] runs when the call is over (also when it was skipped).
     */
    fun drainAsync(c: Context, done: () -> Unit = {}) {
        if (!busy.compareAndSet(false, true)) return done()
        val app = c.applicationContext
        io.execute {
            try { process(app, TrackingService.inProgress) } catch (e: Exception) { Log.e("Delayd", "drain failed", e) } finally { busy.set(false); done() }
        }
    }

    /**
     * Wakes the app at [wake] once the network is up, without a service. JobScheduler: the platform's own "when connected and
     * not before" and it survives reboots; an exact alarm would fire offline and need a permission. Null cancels.
     */
    fun arm(c: Context, wake: Instant? = queue(c).next()) {
        try {
            val js = c.getSystemService(JobScheduler::class.java)
            if (wake == null) return js.cancel(JOB_ID)
            js.schedule(JobInfo.Builder(JOB_ID, ComponentName(c, DrainJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(wait(wake, Instant.now()).toMillis()).setPersisted(true).build())
        } catch (e: RuntimeException) {
            Log.w("Delayd", "could not schedule the drain", e)
        }
    }

    fun wait(wake: Instant, now: Instant): Duration = Duration.between(now, wake).let { if (it < MIN_WAIT) MIN_WAIT else it }
}

/** The wake-up of [Pipeline.arm]. */
class DrainJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Pipeline.drainAsync(this) { jobFinished(params, false) }
        return true
    }

    override fun onStopJob(params: JobParameters) = false // the next arm() is set by the run itself
}
