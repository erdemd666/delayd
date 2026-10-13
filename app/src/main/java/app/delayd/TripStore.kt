package app.delayd

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Detected trips, newest first, kept in one JSON file. Service and UI share it in-process. */
object TripStore {
    private lateinit var file: File
    private val state = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = state

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "trips.json")
        if (file.exists()) state.value = fromJson(file.readText())
    }

    @Synchronized
    fun add(trip: Trip) {
        if (state.value.any { it.id == trip.id }) return
        save(listOf(trip) + state.value)
    }

    @Synchronized
    fun update(id: String, change: (Trip) -> Trip) = save(state.value.map { if (it.id == id) change(it) else it })

    @Synchronized
    fun delete(id: String) = save(state.value.filterNot { it.id == id })

    private fun save(all: List<Trip>) {
        // write-then-rename so a crash mid-write never loses the history
        val tmp = File(file.parentFile, "trips.json.tmp")
        tmp.writeText(toJson(all))
        tmp.renameTo(file)
        state.value = all
    }

    fun toJson(trips: List<Trip>): String = JSONArray(trips.map {
        JSONObject()
            .put("train", it.train).put("from", it.from).put("to", it.to)
            .put("scheduledArrival", it.scheduledArrival.toString())
            .put("arrival", it.arrival.toString())
            .put("realtime", it.realtime)
            .put("fareCt", it.fareCt ?: JSONObject.NULL)
            .put("status", it.status.name)
    }).toString()

    fun fromJson(json: String): List<Trip> = JSONArray(json).let { a ->
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Trip(o.getString("train"), o.getString("from"), o.getString("to"),
                Instant.parse(o.getString("scheduledArrival")), Instant.parse(o.getString("arrival")),
                o.getBoolean("realtime"),
                fareCt = if (o.isNull("fareCt")) null else o.getInt("fareCt"),
                status = ClaimStatus.valueOf(o.optString("status", ClaimStatus.OPEN.name)))
        }
    }
}
