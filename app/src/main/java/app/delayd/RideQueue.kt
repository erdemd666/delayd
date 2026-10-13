package app.delayd

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant

/**
 * Rides waiting for a train lookup, kept in a JSON file so none is lost when the process dies or the network is gone.
 * A ride waits [SETTLE] after it ended (a connecting ride may follow), failures back off up to an hour.
 */
class RideQueue(private val file: File, private val clock: () -> Instant = Instant::now) {
    data class Item(val ride: Ride, val attempts: Int, val notBefore: Instant)

    private var items: List<Item> = load()

    val size get() = items.size

    @Synchronized
    fun add(ride: Ride) {
        if (items.any { it.ride == ride }) return
        save(items + Item(ride, 0, ride.end.time.plus(SETTLE)))
    }

    /**
     * Looks up every journey that is due and hands the trips to [out]. Returns when to call again, or null if nothing waits.
     * [inProgress] is the start of a ride happening right now; a journey it may continue is held back.
     */
    fun drain(inProgress: Fix?, find: (Ride) -> Trip?, out: (Trip) -> Unit): Instant? {
        val now = clock()
        val waiting = synchronized(this) { items }
        val byRide = waiting.associateBy { it.ride }
        for (chain in Journeys.chains(waiting.map { it.ride })) {
            val chainItems = chain.map { byRide.getValue(it) }
            val due = chainItems.maxOf { it.notBefore }
            if (due > now) continue
            if (inProgress != null && Journeys.connects(chain.last().end, inProgress)) continue
            val ageOut = chain.last().end.time.plus(MAX_AGE) < now
            try {
                Journeys.resolve(chain, find).forEach(out)
                remove(chainItems)
            } catch (e: IOException) {
                fail(chainItems, 1, (e as? LookupException)?.retryAfterSec, ageOut)
                break // network or server trouble: don't try the next journey, don't hammer
            } catch (e: JSONException) {
                fail(chainItems, BAD_DATA_WEIGHT, null, ageOut)
            } catch (e: DateTimeException) {
                fail(chainItems, BAD_DATA_WEIGHT, null, ageOut)
            }
        }
        return synchronized(this) { items.filter { it.notBefore > now || it.attempts > 0 }.minOfOrNull { it.notBefore } }
    }

    @Synchronized
    private fun remove(done: List<Item>) = save(items - done.toSet())

    @Synchronized
    private fun fail(failed: List<Item>, weight: Int, retryAfterSec: Long?, ageOut: Boolean) {
        val now = clock()
        save(items.mapNotNull {
            if (it !in failed) return@mapNotNull it
            val attempts = it.attempts + weight
            if (attempts >= MAX_ATTEMPTS || ageOut) null
            else Item(it.ride, attempts, now.plus(backoff(attempts, retryAfterSec)))
        })
    }

    private fun save(all: List<Item>) {
        items = all
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONArray(all.map {
            JSONObject().put("start", fixJson(it.ride.start)).put("end", fixJson(it.ride.end))
                .put("attempts", it.attempts).put("notBefore", it.notBefore.toString())
        }).toString())
        if (!tmp.renameTo(file)) file.writeText(tmp.readText())
    }

    private fun load(): List<Item> = try {
        if (!file.exists()) emptyList() else JSONArray(file.readText()).let { a ->
            (0 until a.length()).mapNotNull {
                try {
                    val o = a.getJSONObject(it)
                    Item(Ride(fix(o.getJSONObject("start")), fix(o.getJSONObject("end"))), o.optInt("attempts"),
                        Instant.parse(o.getString("notBefore")))
                } catch (e: JSONException) { null } catch (e: DateTimeException) { null }
            }
        }
    } catch (e: Exception) { emptyList() } // a damaged queue file must not keep the service from starting

    private fun fixJson(f: Fix) = JSONObject().put("t", f.time.toString()).put("lat", f.lat).put("lon", f.lon)
    private fun fix(o: JSONObject) = Fix(Instant.parse(o.getString("t")), o.getDouble("lat"), o.getDouble("lon"))

    companion object {
        val SETTLE: Duration = Journeys.MAX_GAP // a connecting ride starts within this long after the previous one ended
        val MAX_AGE: Duration = Duration.ofHours(48)
        const val MAX_ATTEMPTS = 10
        const val BAD_DATA_WEIGHT = 4 // three unreadable responses and the ride is dropped

        /** 1 min, 2 min, 4 min ... capped at 1 h; a server's Retry-After wins when longer. */
        fun backoff(attempts: Int, retryAfterSec: Long? = null): Duration {
            val own = Duration.ofMinutes(1L shl (attempts - 1).coerceIn(0, 6)).coerceAtMost(Duration.ofHours(1))
            return maxOf(own, Duration.ofSeconds(retryAfterSec ?: 0).coerceAtMost(Duration.ofHours(6)))
        }

        private fun Duration.coerceAtMost(max: Duration) = if (this > max) max else this
    }
}
