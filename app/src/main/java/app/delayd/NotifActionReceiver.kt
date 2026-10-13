package app.delayd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** "Not relevant" on a delay notification: marks the trip dismissed without opening the app. */
class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifier.ACTION_DISMISS) return
        val id = intent.getStringExtra(TrackingService.EXTRA_TRIP_ID) ?: return
        TripStore.init(context)
        TripStore.update(id) { it.copy(status = ClaimStatus.DISMISSED) }
        Notifier.cancel(context, id)
    }
}
