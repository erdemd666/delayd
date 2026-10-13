package app.delayd.sim

import app.delayd.sim.Geo.alex
import app.delayd.sim.Geo.hamburg
import app.delayd.sim.Geo.hbf
import app.delayd.sim.Geo.ostbahnhof
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** Writes demo routes for the emulator (Extended controls, Location, Routes). SIM_GPX_DIR=docs/simulation/gpx ./gradlew testDebugUnitTest --tests '*GpxExport*' */
class GpxExport {
    private fun gpx(name: String, truth: Truth, from: Instant, to: Instant, everySec: Int = 10): String {
        val a = (java.time.Duration.between(truth.start, from).seconds.toInt()).coerceAtLeast(0)
        val b = java.time.Duration.between(truth.start, to).seconds.toInt().coerceAtMost(truth.seconds - 1)
        val pts = (a..b step everySec).joinToString("\n") {
            val p = truth.at(it)
            """      <trkpt lat="%.6f" lon="%.6f"><ele>40</ele><time>%s</time></trkpt>""".format(java.util.Locale.ROOT, p.lat, p.lon, truth.instant(it))
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Delayd simulation" xmlns="http://www.topografix.com/GPX/1/1">
  <trk>
    <name>$name</name>
    <trkseg>
$pts
    </trkseg>
  </trk>
</gpx>
"""
    }

    @Test fun export() {
        val dir = System.getenv("SIM_GPX_DIR")
        assumeTrue("set SIM_GPX_DIR to write the files", dir != null)
        val out = File(dir!!).apply { mkdirs() }
        // 1. the S7 Hbf - Ostbahnhof of the fixture, 4 min standing, the ride, 8 min standing at the end
        val s7 = fixtureBoard().first { it.train == "S7" && it.time == Instant.parse("2026-10-13T10:49:00Z") }
        val stops = listOf(Wp("Hbf", hbf, s7.time.minusSeconds(60), s7.time)) + s7.next.take(s7.next.indexOfFirst { it.name.contains("Ostbahnhof") } + 1)
            .map { Wp(it.name, Pt(it.lat, it.lon), it.arrival!!) }
        val t1 = commute(stops, vLine = S_V, accel = S_A, approachM = 100.0, waitMin = 4)
        File(out, "s-bahn-hbf-ostbahnhof.gpx").writeText(gpx("S7 Berlin Hbf - Ostbahnhof (10 min)", t1, t1.departed!!.minusSeconds(240), t1.arrived!!.plusSeconds(600)))
        // 2. RE1 Brandenburg - Berlin Hbf with a 14 min stop in the open between Wannsee and Charlottenburg
        val re1 = lineTimetable(T, 60, RE_V, RE_A, "Brandenburg Hbf" to Geo.brandenburg, "Potsdam Hbf" to Geo.potsdam, "Wannsee" to Geo.wannsee,
            "Berlin-Charlottenburg" to Geo.charlottenburg, "Berlin Zoologischer Garten" to Geo.zoo, "Berlin Hbf" to hbf)
        val late = re1.mapIndexed { i, w -> if (i >= 3) w.copy(arrive = w.arrive + min(14), depart = w.depart + min(14)) else w }
        val t2 = commute(late, holds = mapOf(2 to (0.5 to 14 * 60)), approachM = 100.0, waitMin = 4)
        File(out, "re1-brandenburg-berlin-signal-stop.gpx").writeText(gpx("RE1 Brandenburg - Berlin Hbf with a 14 min signal stop", t2, t2.departed!!.minusSeconds(240), t2.arrived!!.plusSeconds(600)))
        // 3. ICE Berlin Hbf - Hamburg Hbf
        val ice = lineTimetable(T, 120, ICE_V, ICE_A, "Berlin Hbf" to hbf, "Berlin-Spandau" to Geo.spandau, "Hamburg Hbf" to hamburg)
        val t3 = commute(ice, approachM = 100.0, waitMin = 4, vLine = ICE_V, accel = ICE_A)
        File(out, "ice-berlin-hamburg.gpx").writeText(gpx("ICE Berlin Hbf - Hamburg Hbf", t3, t3.departed!!.minusSeconds(240), t3.arrived!!.plusSeconds(600)))
    }
}
