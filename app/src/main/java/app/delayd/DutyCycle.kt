package app.delayd

import android.location.LocationManager

/** How the service samples location: the cheapest setting that still catches a train leaving. */
object DutyCycle {
    data class Plan(val gps: Boolean, val intervalMs: Long)

    const val WATCH_MS = 8 * 60_000L

    /**
     * riding: GPS every 10 s. watching (the phone just moved): network fixes every 15 s for [WATCH_MS].
     * idle: a slow safety poll, 5 min when the motion sensor wakes us, else 60 s.
     */
    fun plan(riding: Boolean, watching: Boolean, motionSensor: Boolean) = when {
        riding -> Plan(true, 10_000)
        watching -> Plan(false, 15_000)
        else -> Plan(false, if (motionSensor) 300_000 else 60_000)
    }

    /** Fall back to the other provider when one is switched off. With none on, GPS: it costs nothing and reports when enabled. */
    fun provider(wantGps: Boolean, gpsOn: Boolean, networkOn: Boolean) =
        if ((wantGps && gpsOn) || !networkOn) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
}
