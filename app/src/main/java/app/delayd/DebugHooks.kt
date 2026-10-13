package app.delayd

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Bridge to the debug-only ride simulator in src/debug. Release builds don't contain the receiver,
 * so [available] is false there and [simulateRide] does nothing.
 */
object DebugHooks {
    private const val ACTION = "app.delayd.debug.SIMULATE_RIDE"
    const val EXTRA_DELAY_MIN = "delayMin"

    private fun intent(c: Context) = Intent(ACTION).setPackage(c.packageName)

    @SuppressLint("QueryPermissionsNeeded") // own package is always visible
    fun available(c: Context) = c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
        c.packageManager.queryBroadcastReceivers(intent(c), 0).isNotEmpty()

    /** Replays a recorded Berlin Hbf to Potsdam Hbf ride that ended [delayMin] minutes late through the real pipeline. */
    fun simulateRide(c: Context, delayMin: Int = 75) {
        if (available(c)) c.sendBroadcast(intent(c).putExtra(EXTRA_DELAY_MIN, delayMin))
    }
}
