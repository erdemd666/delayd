package app.delayd.ui

import android.content.Context
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
    /** Android only reports "don't ask again" after we asked at least once. */
    fun asked(permission: String) = sp.getBoolean("asked.$permission", false)
    fun markAsked(permissions: Array<String>) = sp.edit().apply { permissions.forEach { putBoolean("asked.$it", true) } }.apply()
}
