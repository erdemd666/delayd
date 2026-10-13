package app.delayd

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant

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
) {
    val id: String get() = "$train@$scheduledArrival"
    /** True when the arrival time comes from the phone, not from live train data. */
    val measured: Boolean get() = !realtime
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
)

/** HTTP trouble worth retrying later; [retryAfterSec] is the server's own hint (429/503). */
class LookupException(message: String, val retryAfterSec: Long? = null) : IOException(message)

/**
 * Finds the train behind a ride via the Transitous API (MOTIS, open European transit data).
 * Usage policy: open-source + non-commercial only, send a User-Agent, credit transitous.org/sources.
 * shortcut: going commercial means self-hosting MOTIS or the DB API Marketplace; only this file changes.
 */
object Transitous {
    private const val BASE = "https://api.transitous.org/api/v6/stoptimes"
    private const val RAIL = "HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,REGIONAL_RAIL,SUBURBAN"
    private const val USER_AGENT = "Delayd/0.1 (github.com/erdem61/delayd)"
    private const val STOP_RADIUS_M = 800.0
    private val EARLY: Duration = Duration.ofMinutes(10)
    private val LATE: Duration = Duration.ofMinutes(15)
    // a cancelled train can be planned well before the replacement we actually took
    private val CANCELLED_LOOKBACK: Duration = Duration.ofMinutes(30)

    fun findTrip(ride: Ride): Trip? = match(ride, parse(fetchDepartures(ride.start)))

    /** Departures near where the ride started, querying the start keeps the window small however late the train is. */
    private fun fetchDepartures(start: Fix): String {
        val window = CANCELLED_LOOKBACK.plus(LATE).seconds
        val url = URL(
            "$BASE?center=${start.lat},${start.lon}&radius=500&time=${start.time.minus(CANCELLED_LOOKBACK)}" +
                "&window=$window&fetchStops=true&withAlerts=false&mode=$RAIL"
        )
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
                        (!d.realtime || Duration.between(it.arrival, ride.end.time).abs() <= LATE)
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
