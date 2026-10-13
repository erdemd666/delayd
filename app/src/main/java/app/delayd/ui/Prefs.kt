package app.delayd.ui

import android.content.Context
import app.delayd.Trip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The few UI settings, observable by Compose. Key "dTicket" predates the redesign. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var dTicket by mutableStateOf(sp.getBoolean("dTicket", true)); private set
    var onboarded by mutableStateOf(sp.getBoolean("onboarded", false)); private set

    fun chooseDTicket(v: Boolean) { dTicket = v; sp.edit().putBoolean("dTicket", v).apply() }
    fun finishOnboarding() { onboarded = true; sp.edit().putBoolean("onboarded", true).apply() }
    private fun editor() = sp.edit()
    fun fareFor(trip: Trip) = FareMemory.lookup(sp.getString("fares", null), trip.from, trip.to)
    fun rememberFare(trip: Trip, ct: Int) {
        val e = editor()
        e.putString("fares", FareMemory.remember(sp.getString("fares", null), trip.from, trip.to, ct))
        e.apply()
    }
    /** Android only reports "don't ask again" after we asked at least once. */
    fun asked(permission: String) = sp.getBoolean("asked.$permission", false)
    fun markAsked(permissions: Array<String>) = sp.edit().apply { permissions.forEach { putBoolean("asked.$it", true) } }.apply()
}
