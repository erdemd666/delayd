package app.delayd.sim

import app.delayd.Departure
import app.delayd.Transitous
import java.io.File
import java.util.Locale
import java.time.Duration
import java.time.Instant

/** Real station coordinates (Transitous fixtures where they exist, else the station building to ~100 m). */
object Geo {
    val hbf = Pt(52.524998, 13.369061)
    val alex = Pt(52.52152, 13.411226)
    val ostbahnhof = Pt(52.51052, 13.435342)
    val zoo = Pt(52.50707, 13.331736)
    val charlottenburg = Pt(52.505135, 13.304349)
    val wannsee = Pt(52.421665, 13.179986)
    val potsdam = Pt(52.39185, 13.066763)
    val brandenburg = Pt(52.4010, 12.5326)
    val spandau = Pt(52.5348, 13.1977)
    val hamburg = Pt(53.5526, 10.0066)
}

val T: Instant = Instant.parse("2026-10-13T08:00:00Z")
fun min(m: Number): Duration = Duration.ofSeconds((m.toDouble() * 60).toLong())

/** A train of a timetable: [stops] hold the real (live) times; [planned] the scheduled arrival per stop. */
fun train(name: String, stops: List<Wp>, planned: List<Instant> = stops.map { it.arrive }, cancelled: Boolean = false, realtime: Boolean = true) =
    Departure(name, stops[0].name, stops[0].depart, realtime, cancelled = cancelled, next = stops.drop(1).mapIndexed { i, w ->
        stop(w.name, w.pt, planned[i + 1], w.arrive)
    })

/** Same train [offset] later or earlier, on time: a decoy. */
fun List<Wp>.shifted(offset: Duration) = map { it.copy(arrive = it.arrive + offset, depart = it.depart + offset) }

/** Timetable from (name, place, minutes after [t0] of arrival); dwell [dwellSec] at every stop. */
fun timetable(t0: Instant, dwellSec: Long, vararg s: Triple<String, Pt, Number>) = s.map {
    val at = t0 + min(it.third)
    Wp(it.first, it.second, at, at.plusSeconds(dwellSec))
}

/**
 * A person walking [approachM] to the first platform, waiting there, riding the timetable, walking away from the last stop
 * and standing still. [holds] are signal stops (see [TruthBuilder.ride]).
 */
fun commute(stops: List<Wp>, holds: Map<Int, Pair<Double, Int>> = emptyMap(), approachM: Double = 600.0, waitMin: Int = 8,
            vLine: Double = 30.0, accel: Double = 0.7): Truth {
    val platform = stops[0].pt
    val begin = stops[0].depart.minusSeconds(waitMin * 60L + (approachM / 1.3).toLong())
    return TruthBuilder(begin, platform.offset(0.0, -approachM)).apply {
        walk(platform, 1.3)
        ride(stops, holds, vLine, accel)
        walk(stops.last().pt.offset(250.0, 250.0))
        stand(40 * 60)
    }.build()
}

/** One line of results.md. */
data class Row(val name: String, val rides: Int, val startErrS: Long?, val endErrS: Long?, val trips: List<String>, val gpsMin: Double, val idealGpsMin: Double, val note: String = "")

object Report {
    val rows = mutableListOf<Row>()

    fun add(name: String, run: PhoneRun, board: Instant?, alight: Instant?, note: String = ""): Row {
        val first = run.rides.minByOrNull { it.start.time }
        val last = run.rides.maxByOrNull { it.end.time }
        val row = Row(name, run.rides.size,
            if (first != null && board != null) Duration.between(board, first.start.time).seconds else null,
            if (last != null && alight != null) Duration.between(alight, last.end.time).seconds else null,
            run.trips.map { "${it.second.train} to ${it.second.to} (${it.second.delayMin} min)" }, run.gpsMin,
            if (board != null && alight != null) Duration.between(board, alight).seconds / 60.0 else 0.0, note)
        synchronized(rows) { rows.removeAll { it.name == name }; rows += row }
        return row
    }

    fun write() {
        val f = File("build/sim/results.md")
        f.parentFile.mkdirs()
        fun err(s: Long?) = s?.let { "%+.1f".format(Locale.ROOT, it / 60.0) } ?: "-"
        f.writeText(buildString {
            appendLine("| Scenario | Rides | Start err (min) | End err (min) | Trips stored | GPS min (ideal) | Note |")
            appendLine("|---|---|---|---|---|---|---|")
            rows.sortedBy { it.name }.forEach {
                appendLine("| ${it.name} | ${it.rides} | ${err(it.startErrS)} | ${err(it.endErrS)} | ${it.trips.joinToString("; ").ifEmpty { "none" }} | " +
                    "${"%.0f".format(Locale.ROOT, it.gpsMin)} (${"%.0f".format(Locale.ROOT, it.idealGpsMin)}) | ${it.note} |")
            }
        })
    }
}

fun fixtureBoard(): List<Departure> = Transitous.parse(Departure::class.java.getResource("/departures-berlin-hbf.json")!!.readText())
