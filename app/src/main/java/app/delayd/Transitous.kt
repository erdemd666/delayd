package app.delayd

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.hypot

enum class ClaimStatus { OPEN, CLAIMED, PAID, DISMISSED }

data class Trip(
    val train: String,
    val from: String,
    val to: String,
    val scheduledArrival: Instant,
    val arrival: Instant,
    val realtime: Boolean,
    val fareCt: Int? = null,
    val status: ClaimStatus = ClaimStatus.OPEN,
    /** The cancelled train this ride replaced; [scheduledArrival] is then that train's plan. */
    val replaced: String? = null,
    /** Rides joined into this trip (a connection counts as one journey). */
    val legs: Int = 1,
    /** Added by hand with an arrival the user typed (no live data was left). */
    val userReported: Boolean = false,
) {
    val id: String get() = "$train@$scheduledArrival"
    /** True when the arrival time comes from the phone, not from live train data. */
    val measured: Boolean get() = !realtime && !userReported
    val delayMin: Long get() = Duration.between(scheduledArrival, arrival).toMinutes().coerceAtLeast(0)
}

data class Stop(
    val name: String,
    val lat: Double,
    val lon: Double,
    val scheduledArrival: Instant?,
    val arrival: Instant?,
)

data class Departure(
    val train: String,
    val from: String,
    val time: Instant,
    val realtime: Boolean,
    val next: List<Stop>,
    val cancelled: Boolean = false,
    val headsign: String = "",
)

/** A station from the geocoder; [area] (city/region) tells same-named stops apart. */
data class Place(val name: String, val id: String, val area: String = "")

/** HTTP trouble worth retrying later; [retryAfterSec] is the server's own hint (429/503). */
class LookupException(message: String, val retryAfterSec: Long? = null) : IOException(message)

/**
 * Finds the train behind a ride via the Transitous API (MOTIS, open European transit data).
 * Usage policy: open-source + non-commercial only, send a User-Agent, credit transitous.org/sources.
 * shortcut: going commercial means self-hosting MOTIS or the DB API Marketplace; only this file changes.
 */
object Transitous {
    private const val API = "https://api.transitous.org/api"
    private const val BASE = "$API/v6/stoptimes"
    private const val RAIL = "HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,REGIONAL_RAIL,SUBURBAN"
    private const val USER_AGENT = "Delayd/${BuildConfig.VERSION_NAME} (github.com/erdem61/delayd)"
    private const val STOP_RADIUS_M = 800.0
    private const val M_PER_DEG = 111_195.0
    private const val OFF_ROUTE_M = 2_500.0
    private val SLACK: Duration = Duration.ofSeconds(90) // stop times are rounded to the minute
    private val EARLY: Duration = Duration.ofMinutes(10)
    private val LATE: Duration = Duration.ofMinutes(15)
    // a cancelled train can be planned well before the replacement we actually took: an hourly line's is an hour later
    private val CANCELLED_LOOKBACK: Duration = Duration.ofMinutes(75)

    fun findTrip(ride: Ride): Trip? = match(ride, parse(fetchDepartures(ride.start)))

    /** Departures near where the ride started, querying the start keeps the window small however late the train is. */
    private fun fetchDepartures(start: Fix): String {
        val window = CANCELLED_LOOKBACK.plus(LATE).seconds
        val url = URL(
            "$BASE?center=${start.lat},${start.lon}&radius=500&time=${start.time.minus(CANCELLED_LOOKBACK)}" +
                "&window=$window&fetchStops=true&withAlerts=false&mode=$RAIL"
        )
        return get(url)
    }

    private fun get(url: URL): String {
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (c.responseCode != 200) throw LookupException("Transitous HTTP ${c.responseCode}",
                c.getHeaderField("Retry-After")?.trim()?.toLongOrNull())
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Manual add: stations whose name matches [text] and that rail runs through. One request, the caller debounces. */
    fun searchStations(text: String): List<Place> =
        parsePlaces(get(URL("$API/v1/geocode?text=${URLEncoder.encode(text, "UTF-8")}&type=STOP&mode=$RAIL")))

    /** Manual add: rail departures of one stop from [from] on, with their following stops. */
    fun departuresAt(stopId: String, from: Instant, n: Int = 20): List<Departure> = parse(get(URL(
        "$BASE?stopId=${URLEncoder.encode(stopId, "UTF-8")}&time=$from&n=$n&fetchStops=true&withAlerts=false&mode=$RAIL")))

    /** Throws [JSONException] when the response isn't an array; entries without name or id are skipped. */
    fun parsePlaces(json: String): List<Place> = JSONArray(json).objects().mapNotNull { o ->
        val name = o.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val areas = o.optJSONArray("areas")?.objects().orEmpty()
        Place(name, id, areas.firstOrNull { it.optBoolean("default") && it.optInt("adminLevel") >= 4 }?.optString("name").orEmpty())
    }

    /** Throws [JSONException] when the response isn't a stop-time list; single malformed entries are skipped. */
    fun parse(json: String): List<Departure> =
        JSONObject(json).getJSONArray("stopTimes").objects().mapNotNull { st ->
            try {
                val p = st.getJSONObject("place")
                Departure(
                    train = st.getString("displayName"),
                    from = p.getString("name"),
                    time = p.instant("departure") ?: return@mapNotNull null,
                    realtime = st.optBoolean("realTime"),
                    next = st.optJSONArray("nextStops")?.objects()?.map {
                        Stop(it.getString("name"), it.getDouble("lat"), it.getDouble("lon"),
                            it.instant("scheduledArrival"), it.instant("arrival"))
                    }.orEmpty(),
                    cancelled = st.optBoolean("cancelled") || st.optBoolean("tripCancelled"),
                    headsign = st.optString("headsign"),
                )
            } catch (e: JSONException) {
                null
            } catch (e: DateTimeException) {
                null
            }
        }

    private fun reaches(s: Stop, ride: Ride) = s.scheduledArrival != null &&
        distanceM(s.lat, s.lon, ride.end.lat, ride.end.lon) <= STOP_RADIUS_M

    /**
     * Did the phone travel with this train? A bus or car from station to station at the same time starts and ends in the
     * right places, but at a quarter, half and three quarters of the way it is not where the train is. Each [Ride.via]
     * fix is placed on the straight lines between the train's stops; the train must have been there within a few minutes
     * (a stop in the open, which makes the delay grow along that leg, is allowed for). Two of three off: not this train.
     * Only live times can be checked, and a ride without checkpoints passes.
     */
    private fun onTheTrain(ride: Ride, dep: Departure, last: Stop): Boolean {
        if (ride.via.isEmpty() || !dep.realtime) return true
        val stops = dep.next.takeWhile { it !== last } + last
        if (stops.any { it.arrival == null || it.scheduledArrival == null }) return true
        // nodes: where, when, how late (the start has no scheduled time, so the delay counts from there)
        class Node(val lat: Double, val lon: Double, val at: Instant, val late: Duration)
        val nodes = listOf(Node(ride.start.lat, ride.start.lon, dep.time, Duration.ZERO)) +
            stops.map { Node(it.lat, it.lon, it.arrival!!, Duration.between(it.scheduledArrival!!, it.arrival)) }
        val off = ride.via.count { v ->
            val (a, b) = nodes.zipWithNext().minByOrNull { (a, b) -> alongM(v, a.lat, a.lon, b.lat, b.lon).first }!! // the leg it is on
            val (dist, f) = alongM(v, a.lat, a.lon, b.lat, b.lon)
            val leg = Duration.between(a.at, b.at)
            val allowance = SLACK.plus(leg.dividedBy(5)).plus((b.late - a.late).coerceAtLeast(Duration.ZERO))
            dist > OFF_ROUTE_M + distanceM(a.lat, a.lon, b.lat, b.lon) / 10 ||
                Duration.between(a.at.plus(leg.multipliedBy((f * 1000).toLong()).dividedBy(1000)), v.time).abs() > allowance
        }
        return off * 2 < ride.via.size.coerceAtLeast(2)
    }

    /** Distance of [p] from the segment a-b and how far along it the closest point is (0..1), flat-earth approximation. */
    private fun alongM(p: Fix, aLat: Double, aLon: Double, bLat: Double, bLon: Double): Pair<Double, Double> {
        val k = cos(Math.toRadians(aLat))
        val bx = (bLon - aLon) * k * M_PER_DEG
        val by = (bLat - aLat) * M_PER_DEG
        val px = (p.lon - aLon) * k * M_PER_DEG
        val py = (p.lat - aLat) * M_PER_DEG
        val len2 = bx * bx + by * by
        val f = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        return hypot(px - f * bx, py - f * by) to f
    }

    /**
     * The departure that left around the ride's start and stopped closest in time to where the ride ended.
     * If a train cancelled before it served the same destination, that plan is what the delay counts against.
     */
    fun match(ride: Ride, departures: List<Departure>): Trip? {
        val (dep, stop) = departures
            .filter { !it.cancelled && it.time >= ride.start.time.minus(EARLY) && it.time <= ride.start.time.plus(LATE) }
            .flatMap { d ->
                d.next.filter {
                    it.arrival != null && reaches(it, ride) &&
                        // live times must agree with the phone; without them a delayed train can't be told apart
                        (!d.realtime || Duration.between(it.arrival, ride.end.time).abs() <= LATE) &&
                        onTheTrain(ride, d, it)
                }.map { d to it }
            }
            .minByOrNull { (_, s) -> Duration.between(s.arrival, ride.end.time).abs() }
            ?: return null
        val planned = departures
            .filter { it.cancelled && it.time < dep.time && it.time >= ride.start.time.minus(CANCELLED_LOOKBACK) }
            .mapNotNull { c -> c.next.firstOrNull { reaches(it, ride) }?.let { c to it } }
            .filter { (_, s) -> s.scheduledArrival!! < stop.scheduledArrival!! }
            .maxByOrNull { (_, s) -> s.scheduledArrival!! }
        // Without live data the phone is the witness: the observed arrival is the actual one.
        val arrival = if (dep.realtime) stop.arrival!! else ride.end.time
        return Trip(dep.train, dep.from, stop.name, (planned?.second ?: stop).scheduledArrival!!, arrival, dep.realtime,
            replaced = planned?.first?.train)
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.instant(key: String) = optString(key).takeIf { it.isNotEmpty() }?.let(Instant::parse)
}
