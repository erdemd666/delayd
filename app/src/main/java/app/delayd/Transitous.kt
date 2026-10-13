package app.delayd

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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
) {
    val id: String get() = "$train@$scheduledArrival"
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
)

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

    fun findTrip(ride: Ride): Trip? = match(ride, parse(fetchDepartures(ride.start)))

    /** Departures near where the ride started, querying the start keeps the window small however late the train is. */
    private fun fetchDepartures(start: Fix): String {
        val window = EARLY.plus(LATE).seconds
        val url = URL(
            "$BASE?center=${start.lat},${start.lon}&radius=500&time=${start.time.minus(EARLY)}" +
                "&window=$window&fetchStops=true&withAlerts=false&mode=$RAIL"
        )
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (c.responseCode != 200) throw IOException("Transitous HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parse(json: String): List<Departure> =
        JSONObject(json).getJSONArray("stopTimes").objects().mapNotNull { st ->
            val p = st.getJSONObject("place")
            if (st.optBoolean("cancelled") || st.optBoolean("tripCancelled")) return@mapNotNull null
            Departure(
                train = st.getString("displayName"),
                from = p.getString("name"),
                time = p.instant("departure") ?: return@mapNotNull null,
                realtime = st.optBoolean("realTime"),
                next = st.optJSONArray("nextStops")?.objects()?.map {
                    Stop(it.getString("name"), it.getDouble("lat"), it.getDouble("lon"),
                        it.instant("scheduledArrival"), it.instant("arrival"))
                }.orEmpty(),
            )
        }

    /** The departure that left around the ride's start and stopped closest in time to where the ride ended. */
    fun match(ride: Ride, departures: List<Departure>): Trip? {
        val (dep, stop) = departures
            .filter { it.time >= ride.start.time.minus(EARLY) && it.time <= ride.start.time.plus(LATE) }
            .flatMap { d ->
                d.next.filter {
                    it.arrival != null && it.scheduledArrival != null &&
                        distanceM(it.lat, it.lon, ride.end.lat, ride.end.lon) <= STOP_RADIUS_M &&
                        // live times must agree with the phone; without them a delayed train can't be told apart
                        (!d.realtime || Duration.between(it.arrival, ride.end.time).abs() <= LATE)
                }.map { d to it }
            }
            .minByOrNull { (_, s) -> Duration.between(s.arrival, ride.end.time).abs() }
            ?: return null
        // Without live data the phone is the witness: the observed arrival is the actual one.
        val arrival = if (dep.realtime) stop.arrival!! else ride.end.time
        return Trip(dep.train, dep.from, stop.name, stop.scheduledArrival!!, arrival, dep.realtime)
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.instant(key: String) = optString(key).takeIf { it.isNotEmpty() }?.let(Instant::parse)
}
