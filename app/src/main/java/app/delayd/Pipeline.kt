package app.delayd

import android.content.Context
import java.io.File
import java.time.Instant

/** Rides in, trips and notifications out. The service and the debug simulator share this path. */
object Pipeline {
    private var queue: RideQueue? = null

    @Synchronized
    fun queue(c: Context) = queue ?: RideQueue(File(c.applicationContext.filesDir, "pending.json")).also { queue = it }

    /** Looks up due rides; returns when to run again (see [RideQueue.drain]). */
    fun process(c: Context, inProgress: Fix?, find: (Ride) -> Trip? = Transitous::findTrip): Instant? {
        TripStore.init(c)
        return queue(c).drain(inProgress, find) { trip ->
            if (TripStore.add(trip) && trip.delayMin >= 60) Notifier.delay(c, trip)
        }
    }
}
