package app.delayd.sim

import app.delayd.DutyCycle
import app.delayd.Departure
import app.delayd.Fix
import app.delayd.Ride
import app.delayd.RideQueue
import app.delayd.Stop
import app.delayd.Transitous
import app.delayd.Trip
import app.delayd.TripDetector
import app.delayd.distanceM
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Track generator: a ground truth path (what the train really did) and a phone model on top of it
 * (what Android hands the app: duty-cycled fixes with noise, jumps and gaps).
 */

const val M_PER_DEG = 111_195.0

data class Pt(val lat: Double, val lon: Double) {
    fun offset(northM: Double, eastM: Double) = Pt(lat + northM / M_PER_DEG, lon + eastM / (M_PER_DEG * cos(Math.toRadians(lat))))
    fun distTo(o: Pt) = distanceM(lat, lon, o.lat, o.lon)
    fun lerp(o: Pt, f: Double) = Pt(lat + (o.lat - lat) * f, lon + (o.lon - lon) * f)
}

/** A stop of a timetable: the train stands from [arrive] to [depart]. */
data class Wp(val name: String, val pt: Pt, val arrive: Instant, val depart: Instant = arrive.plusSeconds(30))

/** One position per second. */
class Truth(val start: Instant, private val lat: DoubleArray, private val lon: DoubleArray,
            /** When the train really left the first stop and reached the last one (null: no train ride). */
            val departed: Instant? = null, val arrived: Instant? = null) {
    val seconds get() = lat.size
    fun at(s: Int) = Pt(lat[min(s, seconds - 1)], lon[min(s, seconds - 1)])
    fun speed(s: Int) = if (s <= 0 || s >= seconds) 0.0 else at(s).distTo(at(s - 1))
    fun instant(s: Int): Instant = start.plusSeconds(s.toLong())
}

/** Time to cover [d] meters from standstill to standstill: accelerate to line speed [v], cruise, brake. */
fun physSeconds(d: Double, v: Double, a: Double) = if (d > v * v / a) d / v + v / a else 2 * sqrt(d / a)

class TruthBuilder(val start: Instant, from: Pt) {
    private val lat = ArrayList<Double>()
    private val lon = ArrayList<Double>()
    var here = from
        private set
    init { push(from) }

    val now: Instant get() = start.plusSeconds((lat.size - 1).toLong())
    private fun push(p: Pt) { lat += p.lat; lon += p.lon }

    fun stand(sec: Int) = repeat(sec) { push(here) }
    fun standUntil(t: Instant) = stand(max(0, Duration.between(now, t).seconds.toInt()))

    /** Smooth trapezoid move that takes exactly [sec]: accelerate, cruise, brake. */
    fun move(to: Pt, sec: Int, accel: Double = 0.8) {
        val d = here.distTo(to)
        if (d < 1.0 || sec <= 0) return stand(sec)
        val a = max(accel, 4 * d / (sec.toDouble() * sec)) // too short for [accel]: a triangle profile
        val v = a * (sec - sqrt(max(0.0, sec.toDouble() * sec - 4 * d / a))) / 2
        val ta = v / a
        fun s(t: Double) = when {
            t <= ta -> 0.5 * a * t * t
            t <= sec - ta -> 0.5 * a * ta * ta + v * (t - ta)
            else -> d - 0.5 * a * (sec - t) * (sec - t)
        }
        for (i in 1..sec) push(here.lerp(to, min(1.0, s(i.toDouble()) / d)))
        here = to
    }

    fun walk(to: Pt, mps: Double = 1.2) = move(to, (here.distTo(to) / mps).toInt() + 2, accel = 1.0)

    /** A constant-speed drive along [path] (car, bus): short accel and brake, no stops. */
    fun drive(path: List<Pt>, mps: Double) = path.forEach { move(it, (here.distTo(it) / mps).toInt() + 3, accel = 1.5) }

    /**
     * Follows a timetable. [holds] maps a leg index (stop i to i+1) to (fraction of the way, seconds standing there):
     * a signal stop in the open. The hold time is part of the leg's timetable time.
     */
    fun ride(stops: List<Wp>, holds: Map<Int, Pair<Double, Int>> = emptyMap(), vLine: Double = 30.0, accel: Double = 0.7) {
        for (i in 0 until stops.size - 1) {
            val a = stops[i]
            val b = stops[i + 1]
            standUntil(a.depart)
            val total = Duration.between(a.depart, b.arrive).seconds.toInt()
            val hold = holds[i]?.second ?: 0
            // A train runs at line speed and waits at the station; the timetable's slack is standing, not crawling.
            val slack = max(0, total - hold - physSeconds(a.pt.distTo(b.pt), vLine, accel).toInt())
            stand(slack)
            if (departedAt == null) departedAt = now
            val run = total - hold - slack
            val h = holds[i]
            if (h == null) move(b.pt, run, accel) else {
                val (f, sec) = h
                move(a.pt.lerp(b.pt, f), (run * f).toInt(), accel)
                stand(sec)
                move(b.pt, (run * (1 - f)).toInt(), accel)
            }
        }
        standUntil(stops.last().arrive)
        arrivedAt = now
    }

    var departedAt: Instant? = null
        private set
    var arrivedAt: Instant? = null
        private set

    fun build() = Truth(start, lat.toDoubleArray(), lon.toDoubleArray(), departedAt, arrivedAt)
}

/** What the phone model is allowed to do wrong. */
data class PhoneCfg(
    val seed: Long = 1,
    /** No fixes at all in these windows (tunnels, underground platforms). */
    val gaps: List<Pair<Instant, Instant>> = emptyList(),
    /** Places where the sky is partly blocked: accuracy is twice as bad within 400 m. */
    val stations: List<Pt> = emptyList(),
    /** Whether the device has the significant-motion sensor (else idle polling is every 60 s). */
    val motionSensor: Boolean = true,
    /** Chance per network fix of a cell-tower jump of 300-1500 m. */
    val jumpProb: Double = 0.03,
    /** Seconds after the last truth sample the phone keeps running, enough for the detector to close the ride. */
    val tailMin: Int = 25,
)

class PhoneRun(
    val fixes: List<Fix>,
    val rides: List<Ride>,
    /** Trips the real [RideQueue] produced, and when. */
    val trips: List<Pair<Instant, Trip>>,
    val gpsSeconds: Int,
) {
    val gpsMin get() = gpsSeconds / 60.0
}

/**
 * Drives the real TripDetector and RideQueue the way [app.delayd.TrackingService] does: the [DutyCycle] plan decides the
 * fix rate, a motion trigger opens a watch window, finished rides are queued and drained.
 */
fun runPhone(truth: Truth, cfg: PhoneCfg, boards: List<Pair<Pt, List<Departure>>>): PhoneRun {
    val rng = Random(cfg.seed)
    fun gauss() = sqrt(-2 * kotlin.math.ln(1 - rng.nextDouble())) * cos(2 * Math.PI * rng.nextDouble())
    var now = truth.start
    val queue = RideQueue(File.createTempFile("pending", ".json").also { it.deleteOnExit(); it.delete() }) { now }
    val rides = mutableListOf<Ride>()
    val detector = TripDetector { rides += it; queue.add(it) }
    val trips = mutableListOf<Pair<Instant, Trip>>()
    val fixes = mutableListOf<Fix>()
    // like Transitous.findTrip, a board only answers for rides that started within 500 m of its station
    val find = { r: Ride -> Transitous.match(r, boards.filter { it.first.distTo(Pt(r.start.lat, r.start.lon)) <= 500 }.flatMap { it.second }) }
    fun drain(inProgress: Fix?) { queue.drain(inProgress, find) { trips += now to it } }

    var plan: DutyCycle.Plan? = null
    var nextFix = 0
    var watchUntil = -1
    var stillFor = 0
    var gps = 0
    val total = truth.seconds + cfg.tailMin * 60
    for (s in 0 until total) {
        now = truth.instant(s)
        // significant motion: fires on movement after the phone lay still for 30 s, then re-arms
        stillFor = if (truth.speed(s) < 0.3) stillFor + 1 else stillFor
        if (truth.speed(s) > 1.0) {
            if (cfg.motionSensor && stillFor >= 30) watchUntil = s + (DutyCycle.WATCH_MS / 1000).toInt()
            stillFor = 0
        }
        val want = DutyCycle.plan(detector.moving, s < watchUntil, cfg.motionSensor)
        if (want != plan) { plan = want; nextFix = min(nextFix, s + if (want.gps) 8 else 3).let { if (it < s) s else it } }
        if (plan!!.gps) gps++
        if (s >= nextFix) {
            nextFix = s + (plan.intervalMs / 1000).toInt()
            val inGap = cfg.gaps.any { now >= it.first && now < it.second }
            if (!inGap) phoneFix(truth, s, plan.gps, cfg, rng, ::gauss)?.let {
                fixes += it
                detector.add(it)
            }
        }
        if (s % 120 == 0) drain(detector.current)
    }
    now = now.plus(Duration.ofHours(3))
    drain(null)
    return PhoneRun(fixes, rides, trips, gps)
}

private fun phoneFix(truth: Truth, s: Int, gps: Boolean, cfg: PhoneCfg, rng: Random, gauss: () -> Double): Fix? {
    val p = truth.at(s)
    val crowded = if (cfg.stations.any { it.distTo(p) < 400 }) 2.0 else 1.0
    val acc = if (gps) rng.nextDouble(5.0, 30.0) * crowded else rng.nextDouble(25.0, 120.0) * crowded
    var q = p.offset(gauss() * acc / 2, gauss() * acc / 2)
    var reported = acc
    if (!gps && rng.nextDouble() < cfg.jumpProb) {
        val ang = rng.nextDouble(0.0, 2 * Math.PI)
        val d = rng.nextDouble(300.0, 1500.0)
        q = q.offset(d * cos(ang), d * sin(ang))
        if (rng.nextBoolean()) reported = rng.nextDouble(800.0, 3000.0) // many jumps admit a poor accuracy, many don't
    }
    if (reported > 500) return null // TrackingService.onLocationChanged drops these
    return Fix(truth.instant(s), q.lat, q.lon)
}

fun stop(name: String, pt: Pt, scheduled: Instant, actual: Instant = scheduled) = Stop(name, pt.lat, pt.lon, scheduled, actual)
