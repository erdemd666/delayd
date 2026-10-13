package app.delayd

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Quick Settings tile: detection on/off without opening the app. */
class DetectionTile : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        scope = CoroutineScope(Dispatchers.Main.immediate).also { s -> TrackingService.running.onEach(::render).launchIn(s) }
    }

    override fun onStopListening() { scope?.cancel(); scope = null }

    override fun onClick() {
        if (TrackingService.running.value) { stopService(Intent(this, TrackingService::class.java)); return }
        // Android 14+ only lets a *visible* app start a location service, and a tile tap is not on the exemption list: go through the activity.
        val open = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_START_DETECTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated") startActivityAndCollapse(open) // below 34 only the Intent form exists
    }

    private fun render(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_delayd)
        tile.label = getString(R.string.detect_title)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(if (on) R.string.detect_on else R.string.detect_off)
        tile.updateTile()
    }
}
