package app.delayd.debug

import android.content.Context
import app.delayd.Departure
import app.delayd.Fix
import app.delayd.Pipeline
import app.delayd.Ride
import app.delayd.Stop
import app.delayd.Transitous
import app.delayd.TripDetector
import java.time.Duration
import java.time.Instant

/** Debug-only: replays a Berlin Hbf to Potsdam Hbf ride so QA and demos need no train. */
object DebugTools {
    private const val FROM_LAT = 52.5251
    private const val FROM_LON = 13.3694
    private const val TO_LAT = 52.3918
    private const val TO_LON = 13.0668

    /**
     * Feeds synthetic fixes through the real [TripDetector], the persistent ride queue, the train matcher
     * ([Transitous.match] on a canned departure instead of the network), [app.delayd.TripStore] and the notifications.
     * The ride is placed in the past so the queue's settle time has already passed.
     */
    fun simulateRide(context: Context, delayMin: Int = 75) {
        val queue = Pipeline.queue(context)
        val arrival = Instant.now().minus(Duration.ofMinutes(40))
        var detected: Ride? = null
        val detector = TripDetector { detected = it; queue.add(it) }
        fun at(t: Instant, f: Double) = Fix(t, FROM_LAT + (TO_LAT - FROM_LAT) * f, FROM_LON + (TO_LON - FROM_LON) * f)

        val depart = arrival.minus(Duration.ofMinutes(15))
        repeat(3) { detector.add(at(depart.minusSeconds(180L - it * 60), 0.0)) }  // waiting on the platform
        repeat(91) { detector.add(at(depart.plusSeconds(it * 10L), it / 90.0)) }  // 15 min at ~30 m/s
        repeat(12) { detector.add(at(arrival.plusSeconds(it * 60L), 1.0)) }        // standing at the destination

        val ride = checkNotNull(detected) { "simulated ride was not detected" }
        val train = Departure("ICE 999 (simulated)", "Berlin Hbf", ride.start.time.plusSeconds(60), realtime = true, next = listOf(
            Stop("Potsdam Hbf", TO_LAT, TO_LON, ride.end.time.minus(Duration.ofMinutes(delayMin.toLong())), ride.end.time)))
        Pipeline.process(context, null) { Transitous.match(it, listOf(train)) }
    }
}
