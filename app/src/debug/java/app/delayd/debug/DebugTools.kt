package app.delayd.debug

import android.content.Context
import app.delayd.Departure
import app.delayd.Fix
import app.delayd.Pipeline
import app.delayd.Ride
import app.delayd.Stop
import app.delayd.Transitous
import app.delayd.TripStore
import android.util.Log
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

    /**
     * Replays a train that really ran in the last 2 h from [station] (live data, 3 requests: geocode, departures, the
     * pipeline's own lookup) through the real detector, queue, Transitous lookup, trip store and notifications.
     * Progress goes to logcat under "DelaydReplay".
     */
    fun replayReal(context: Context, station: String) {
        fun say(m: String) = Log.i("DelaydReplay", m)
        val queue = Pipeline.queue(context)
        val place = Transitous.searchStations(station).firstOrNull { it.lat != 0.0 } ?: run { say("no station for '$station'"); return }
        val now = Instant.now()
        val deps = Transitous.departuresAt(place.id, now.minus(Duration.ofHours(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS), n = 80)
        val pick = Replay.pick(deps, place.lat to place.lon, now) ?: run { say("no suitable train in ${place.name}: ${deps.size} departures"); return }
        say("replaying ${pick.dep.train} ${place.name} ${pick.dep.time} -> ${pick.dest.name} arr ${pick.dest.arrival} (planned ${pick.dest.scheduledArrival})")
        var rides = 0
        val detector = TripDetector { rides++; queue.add(it) }
        Replay.fixes(pick).forEach(detector::add)
        val before = TripStore.trips.value.size
        Pipeline.process(context, null) // the live lookup
        val stored = TripStore.trips.value.take(TripStore.trips.value.size - before)
        say("rides=$rides stored=${stored.map { "${it.train} -> ${it.to} delay ${it.delayMin} min realtime=${it.realtime}" }}")
    }
}
