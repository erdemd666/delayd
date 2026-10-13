package app.delayd.sim

import app.delayd.ClaimStatus
import app.delayd.Compensation
import app.delayd.Departure
import app.delayd.Trip
import app.delayd.sim.Geo.alex
import app.delayd.sim.Geo.brandenburg
import app.delayd.sim.Geo.charlottenburg
import app.delayd.sim.Geo.hamburg
import app.delayd.sim.Geo.hbf
import app.delayd.sim.Geo.ostbahnhof
import app.delayd.sim.Geo.potsdam
import app.delayd.sim.Geo.spandau
import app.delayd.sim.Geo.wannsee
import app.delayd.sim.Geo.zoo
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Realistic simulated rides through the real TripDetector, RideQueue, Journeys and Transitous.match. Offline and deterministic. */
class ScenarioTest {
    companion object {
        @AfterClass @JvmStatic fun writeReport() = Report.write()
        private val ALL_STATIONS = listOf(hbf, alex, ostbahnhof, zoo, charlottenburg, wannsee, potsdam, brandenburg, spandau, hamburg)
    }

    private fun board(origin: Pt, vararg d: Departure) = listOf(origin to d.toList())

    private fun sim(name: String, truth: Truth, boards: List<Pair<Pt, List<Departure>>>, cfg: PhoneCfg = PhoneCfg(), note: String = ""): PhoneRun {
        val run = runPhone(truth, cfg.copy(stations = ALL_STATIONS), boards)
        Report.add(name, run, truth.departed, truth.arrived, note)
        return run
    }

    /** One train, one trip, with the delay and destination expected; detection errors within what polling allows. */
    private fun assertTrip(run: PhoneRun, truth: Truth, train: String, to: String, delayMin: Long): Trip {
        val board = truth.departed!!
        val alight = truth.arrived!!
        assertEquals("trips ${run.trips}", 1, run.trips.size)
        val trip = run.trips.single().second
        assertEquals(train, trip.train)
        assertTrue(trip.to, trip.to.contains(to))
        assertEquals(delayMin, trip.delayMin)
        val first = run.rides.minOf { it.start.time }
        val last = run.rides.maxOf { it.end.time }
        assertTrue("start error ${Duration.between(board, first)}", Duration.between(board, first).seconds in -360..60)
        assertTrue("end error ${Duration.between(alight, last)}", abs(Duration.between(alight, last).seconds) <= 180)
        return trip
    }

    private val re1 = lineTimetable(T + min(10), 60, RE_V, RE_A, "Brandenburg Hbf" to brandenburg, "Potsdam Hbf" to potsdam,
        "Wannsee" to wannsee, "Berlin-Charlottenburg" to charlottenburg, "Berlin Zoologischer Garten" to zoo, "Berlin Hbf" to hbf)
    private val ice = lineTimetable(T + min(30), 120, ICE_V, ICE_A, "Berlin Hbf" to hbf, "Berlin-Spandau" to spandau, "Hamburg Hbf" to hamburg)

    private fun List<Wp>.delayedFrom(index: Int, by: Number) = mapIndexed { i, w -> if (i >= index) w.copy(arrive = w.arrive + min(by), depart = w.depart + min(by)) else w }

    // 1. S-Bahn short hop on the real S7 of the fixture: Hbf to Ostbahnhof, ~10 min
    @Test fun sBahnHop() {
        val d = fixtureBoard().first { it.train == "S7" && it.time == Instant.parse("2026-10-13T10:49:00Z") }
        val stops = listOf(Wp("Hbf", hbf, d.time.minusSeconds(60), d.time)) + d.next.take(d.next.indexOfFirst { it.name.contains("Ostbahnhof") } + 1)
            .map { Wp(it.name, Pt(it.lat, it.lon), it.arrival!!) }
        val truth = commute(stops, vLine = S_V, accel = S_A)
        val run = sim("S-Bahn hop Hbf-Ostbahnhof (real S7)", truth, board(hbf, *fixtureBoard().toTypedArray()))
        assertTrip(run, truth, "S7", "Ostbahnhof", 0)
    }

    // 2. regional commute with 4 intermediate stops and decoys: the next RE1, an earlier one
    @Test fun regionalCommute() {
        val b = board(brandenburg, train("RE1", re1), train("RE1 (next)", re1.shifted(min(12))), train("RE1 (prev)", re1.shifted(min(-8))))
        val truth = commute(re1)
        assertTrip(sim("RE1 commute, 4 stops (synthetic board)", truth, b), truth, "RE1", "Hbf", 0)
    }

    // 3. ICE Berlin Hbf - Hamburg Hbf; no fixes from 2 min before to 4 min after departure (underground platform)
    @Test fun iceWithTunnelGap() {
        val truth = commute(ice, vLine = ICE_V, accel = ICE_A)
        val dep = truth.departed!!
        val cfg = PhoneCfg(gaps = listOf(dep.minusSeconds(120) to dep.plusSeconds(240)))
        val run = sim("ICE Hbf-Hamburg, tunnel gap at Hbf (synthetic board)", truth, board(hbf, train("ICE 1", ice), train("ICE 3", ice.shifted(min(6)))), cfg)
        assertTrip(run, truth, "ICE 1", "Hamburg", 0)
    }

    // 4. 75 min late: the train stays at the platform, the phone idles for 75 min
    @Test fun delay75() {
        val late = re1.delayedFrom(0, 75)
        val b = board(brandenburg, train("RE1", late, planned = re1.map { it.arrive }), train("RE1 (next)", re1.shifted(min(30))))
        val truth = commute(late, waitMin = 8 + 75)
        val trip = assertTrip(sim("75 min delay, waiting on the platform", truth, b), truth, "RE1", "Hbf", 75)
        assertEquals(1250, Compensation.singleTicketCt(trip.delayMin, 5000))
    }

    // 5. 130 min late: an hour late at the origin, then 70 min stuck in the open 80 % of the way
    @Test fun delay130WithLongBlockage() {
        val late = ice.delayedFrom(0, 60).let { l -> l.mapIndexed { i, w -> if (i == 2) w.copy(arrive = w.arrive + min(70)) else w } }
        val b = board(hbf, train("ICE 1", late, planned = ice.map { it.arrive }))
        val truth = commute(late, holds = mapOf(1 to (0.8 to 70 * 60)), waitMin = 8 + 60, vLine = ICE_V, accel = ICE_A)
        val trip = assertTrip(sim("130 min delay, 70 min blockage in the open", truth, b), truth, "ICE 1", "Hamburg", 130)
        assertEquals(2500, Compensation.singleTicketCt(trip.delayMin, 5000))
    }

    // 6. signal stops
    private fun signalStop(name: String, fromStop: Int, leg: Int, holdMin: Int): Pair<PhoneRun, Truth> {
        val late = re1.delayedFrom(fromStop, holdMin)
        val truth = commute(late, holds = mapOf(leg to (0.5 to holdMin * 60)))
        return sim(name, truth, board(brandenburg, train("RE1", late, re1.map { it.arrive }))) to truth
    }

    @Test fun signalStopSixMinutesMidRoute() {
        val (run, truth) = signalStop("Signal stop 6 min mid-route", 3, 2, 6)
        assertTrip(run, truth, "RE1", "Hbf", 6)
    }

    @Test fun signalStopFourteenMinutesMidRoute() {
        val (run, truth) = signalStop("Signal stop 14 min mid-route", 3, 2, 14)
        assertTrip(run, truth, "RE1", "Hbf", 14)
        assertTrue(run.trips.single().first > truth.arrived!!) // never a "trip" before the train got there
    }

    @Test fun waitingOutsideTheDestinationFor12Minutes() {
        val (run, truth) = signalStop("Held 12 min, 1.5 km before the destination", 5, 4, 12)
        assertTrip(run, truth, "RE1", "Hbf", 12)
    }

    // 7. connection with a 12 min and with a 6 min change at Potsdam; delay counted at the final destination
    private fun transfer(name: String, changeMin: Int) {
        val leg1 = re1.take(2) // Brandenburg - Potsdam, arrives 08:35
        val dep2 = leg1.last().arrive + min(changeMin)
        val onTime = lineTimetable(dep2, 60, RE_V, RE_A, "Potsdam Hbf" to potsdam, "Berlin Hbf" to hbf)
        val leg2 = onTime.delayedFrom(1, 8) // arrives 8 min late
        val planned2 = onTime.map { it.arrive }
        val platform2 = potsdam.offset(-60.0, 90.0)
        val truth = TruthBuilder(leg1[0].depart.minusSeconds(8 * 60 + 460), brandenburg.offset(0.0, -600.0)).apply {
            walk(brandenburg, 1.3)
            ride(leg1)
            walk(platform2, 1.3)
            ride(listOf(leg2[0].copy(pt = platform2), leg2[1]))
            walk(hbf.offset(250.0, 250.0))
            stand(40 * 60)
        }.build()
        val b = board(brandenburg, train("RE1", leg1)) + board(potsdam, train("RE7", leg2, planned2))
        val run = sim(name, truth, b)
        assertEquals("trips ${run.trips}", 1, run.trips.size)
        val trip = run.trips.single().second
        assertEquals("RE1 + RE7", trip.train)
        assertEquals(8, trip.delayMin)
        assertEquals(2, trip.legs)
    }
    @Test fun transfer12Min() = transfer("Transfer, 12 min change", 12)
    @Test fun transfer9Min() = transfer("Transfer, 9 min change", 9)
    @Test fun transfer5Min() = transfer("Transfer, 5 min change", 5)

    // 8. cancelled train and its replacement 30 min / 60 min later (an hourly line)
    private fun replacement(name: String, laterMin: Int) {
        val replacement = re1.shifted(min(laterMin))
        val b = board(brandenburg, train("RE1", re1, cancelled = true), train("RE1", replacement))
        val truth = commute(replacement, waitMin = 8 + laterMin)
        val trip = assertTrip(sim(name, truth, b), truth, "RE1", "Hbf", laterMin.toLong())
        assertEquals("RE1", trip.replaced)
    }
    @Test fun cancelledAndReplacement() = replacement("Cancelled train + replacement 30 min later", 30)
    @Test fun cancelledAndHourlyReplacement() = replacement("Cancelled train + replacement 60 min later", 60)

    // 9. a car at 120 km/h along the A115 / rails: from a street 5 km away from any station, nothing may be stored
    private fun carDrive(from: Pt, via: List<Pt>, start: Instant, mps: Double = 30.0): Truth = TruthBuilder(start, from).apply {
        stand(5 * 60)
        drive(via, mps)
        stand(40 * 60)
    }.build()

    @Test fun carAlongTheRails() {
        val truth = carDrive(Pt(52.470, 13.300), listOf(Pt(52.445, 13.200), Pt(52.420, 13.100), Pt(52.405, 13.030)), T)
        val run = sim("Car, home to work along the rails", truth, board(hbf, *fixtureBoard().toTypedArray()))
        assertEquals("trips ${run.trips}", 0, run.trips.size)
    }

    // 9b. the same from the station car park to the station, at 16 different start times: how often does a car look like a train?
    private val s7Board = board(hbf, *fixtureBoard().toTypedArray())

    private fun lookalikes(name: String, truthAt: (Instant) -> Truth): Int {
        val starts = (0 until 16).map { Instant.parse("2026-10-13T10:38:00Z") + min(it * 2) }
        val runs = starts.mapIndexed { i, at -> runPhone(truthAt(at), PhoneCfg(seed = i.toLong(), stations = ALL_STATIONS), s7Board) }
        val wrong = runs.count { it.trips.isNotEmpty() }
        Report.add(name, runs.first(), null, null, "$wrong of ${runs.size} start times matched a train")
        return wrong
    }

    @Test fun carFromStationToStation() {
        lookalikes("Car, Hbf to Potsdam Hbf at 108 km/h") { at ->
            carDrive(hbf.offset(100.0, 100.0), listOf(Pt(52.49, 13.26), Pt(52.43, 13.17), potsdam.offset(150.0, 50.0)), at)
        }
    }

    // 10. a bus on the main road next to the S-Bahn: 480 m between stops, 45 km/h bursts, 40 s at stops and lights (~19 km/h average)
    @Test fun cityBus() {
        val path = (1..10).map { hbf.lerp(ostbahnhof, it / 10.0).offset(150.0, 0.0) }
        lookalikes("City bus Hbf to Ostbahnhof, ~19 km/h") { at ->
            TruthBuilder(at, hbf.offset(150.0, 250.0)).apply {
                stand(4 * 60)
                path.forEach { move(it, (here.distTo(it) / 12.5 + 12.5).toInt(), accel = 1.0); stand(40) }
                stand(40 * 60)
            }.build()
        }
    }

    // 11. cyclist at 25 km/h and a brisk walker: no ride, and no GPS
    @Test fun cyclistAndWalker() {
        val bike = TruthBuilder(T, hbf).apply {
            stand(10 * 60)
            drive(listOf(hbf.offset(0.0, 2500.0), hbf.offset(300.0, 5000.0), hbf.offset(300.0, 8000.0), hbf.offset(0.0, 10000.0)), 6.9)
            stand(20 * 60)
        }.build()
        val run = sim("Cyclist 25 km/h, 10 km", bike, board(hbf, *fixtureBoard().toTypedArray()))
        assertEquals(0, run.rides.size)
        assertTrue("gps ${run.gpsMin}", run.gpsMin < 12)
        val walk = TruthBuilder(T, hbf).apply { stand(600); walk(hbf.offset(0.0, 6000.0), 2.0); stand(1200) }.build()
        val w = sim("Brisk walk 7 km/h, 6 km", walk, board(hbf, *fixtureBoard().toTypedArray()))
        assertEquals(0, w.rides.size)
    }

    // 12. night at home: noisy network fixes only
    @Test fun nightAtHome() {
        val night = TruthBuilder(T, Pt(52.48, 13.40)).apply { stand(8 * 3600) }.build()
        val run = sim("Night at home, 8 h, motion sensor", night, emptyList(), PhoneCfg(tailMin = 0))
        assertEquals(0, run.rides.size)
        assertTrue("gps ${run.gpsMin}", run.gpsMin < 3)
    }

    @Test fun nightAtHomeWithoutMotionSensor() {
        val night = TruthBuilder(T, Pt(52.48, 13.40)).apply { stand(8 * 3600) }.build()
        val run = sim("Night at home, 8 h, no motion sensor (60 s poll)", night, emptyList(), PhoneCfg(motionSensor = false, tailMin = 0))
        assertEquals(0, run.rides.size)
        assertTrue("gps ${run.gpsMin}", run.gpsMin < 3)
    }

    // 13. a month of Deutschlandticket commuting: 40 S-Bahn rides, 3 of them over an hour late
    @Test fun deutschlandticketMonth() {
        val delays = List(40) { i -> when (i) { 6 -> 65L; 19 -> 80L; 31 -> 130L; else -> (i % 7).toLong() } }
        val zone = ZoneId.of("Europe/Berlin")
        val trips = delays.mapIndexed { i, delay ->
            val day = Instant.parse("2026-09-01T06:00:00Z").plus(Duration.ofDays(i % 28L)).plus(Duration.ofHours(i / 28 * 2L))
            val sched = lineTimetable(day, 30, S_V, S_A, "Berlin Hbf" to hbf, "Alexanderplatz" to alex, "Ostbahnhof" to ostbahnhof)
            val late = sched.delayedFrom(0, delay)
            val run = runPhone(commute(late, vLine = S_V, accel = S_A), PhoneCfg(seed = i.toLong(), stations = ALL_STATIONS), board(hbf, train("S7", late, sched.map { it.arrive })))
            assertEquals("ride $i: ${run.trips}", 1, run.trips.size)
            run.trips.single().second
        }
        assertEquals(delays, trips.map { it.delayMin })
        assertEquals(450, Compensation.dTicketMonthCt(trips.map { it.delayMin }))
        val claim = Compensation.monthlyClaim(trips, Instant.parse("2026-10-02T08:00:00Z"), zone, null)
        assertEquals(450, claim!!.second)
        assertTrue(trips.all { it.status == ClaimStatus.OPEN })
    }
}
