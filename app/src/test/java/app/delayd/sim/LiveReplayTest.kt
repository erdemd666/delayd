package app.delayd.sim

import app.delayd.Journeys
import app.delayd.Ride
import app.delayd.Transitous
import app.delayd.TripDetector
import app.delayd.debug.Replay
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Manual, not part of the default run: replays trains that really ran in the last 2 h against the live Transitous API.
 * DELAYD_LIVE=1 [DELAYD_STATIONS="Berlin Hbf;Hamburg Hbf"] ./gradlew testDebugUnitTest --tests '*LiveReplayTest*'
 * Three requests per station (geocode, departures, the lookup). Results: app/build/sim/live.md.
 */
class LiveReplayTest {
    @Test fun replayRealTrains() {
        assumeTrue("set DELAYD_LIVE=1 to call the real API", System.getenv("DELAYD_LIVE") == "1")
        val out = StringBuilder("| Station | Train | Ride | Planned arrival | Replay end err | Result |\n|---|---|---|---|---|---|\n")
        for (station in (System.getenv("DELAYD_STATIONS") ?: "Berlin Hbf").split(";")) {
            val place = Transitous.searchStations(station).first { it.lat != 0.0 }
            val now = Instant.now()
            val deps = Transitous.departuresAt(place.id, now.minus(Duration.ofHours(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS), 80)
            val pick = Replay.pick(deps, place.lat to place.lon, now)
            if (pick == null) { out.appendLine("| ${place.name} | - | - | - | - | no suitable train |"); continue }
            val rides = mutableListOf<Ride>()
            val detector = TripDetector { rides += it }
            Replay.fixes(pick).forEach(detector::add)
            // what the queue does: chain the pieces, try the whole ride first
            val trip = Journeys.chains(rides).flatMap { Journeys.resolve(it, Transitous::findTrip) }.firstOrNull()
            val err = rides.maxOfOrNull { it.end.time }?.let { Duration.between(pick.dest.arrival, it).seconds }
            val gaps = rides.zipWithNext { a, b -> Duration.between(a.end.time, b.start.time).seconds }
            out.appendLine("| ${place.name} | ${pick.dep.train} | ${pick.dep.time} to ${pick.dest.name} ${pick.dest.arrival} | ${pick.dest.scheduledArrival} | " +
                "${err ?: "-"} s | rides=${rides.size} gaps=$gaps s; " + (trip?.let { "${it.train} to ${it.to}, delay ${it.delayMin} min, realtime=${it.realtime}" } ?: "NO MATCH") + " |")
        }
        File("build/sim").mkdirs()
        File("build/sim/live.md").writeText(out.toString())
        println(out)
    }
}
