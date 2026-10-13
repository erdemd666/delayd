package app.delayd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.delayd.ui.appLocale
import app.delayd.ui.money
import java.time.YearMonth
import java.time.format.TextStyle

/** Every notification Delayd shows, one channel each so users can mute them separately. */
object Notifier {
    private const val CH_TRACK = "tracking"
    private const val CH_DELAY = "delays"
    private const val CH_CLAIM = "claims"
    private const val CH_RESUME = "resume"
    const val ID_TRACKING = 1
    const val EXTRA_CLAIM = "claim"
    const val ACTION_DISMISS = "app.delayd.DISMISS_TRIP"
    private const val ID_RESUME = 2
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        listOf(
            Triple(CH_TRACK, R.string.core_ch_tracking, NotificationManager.IMPORTANCE_MIN),
            Triple(CH_DELAY, R.string.core_ch_delays, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_CLAIM, R.string.core_ch_claims, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_RESUME, R.string.core_ch_resume, NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach { (id, name, importance) -> nm.createNotificationChannel(NotificationChannel(id, c.getString(name), importance)) }
    }

    fun tracking(c: Context) = build(c, CH_TRACK, c.getString(R.string.core_tracking_title), c.getString(R.string.core_tracking_text), open(c, 0, null))

    fun delay(c: Context, trip: Trip): Unit {
        val code = trip.id.hashCode()
        // request codes differ per action: PendingIntents that only differ in extras would share one
        val claim = action(c, R.string.notif_claim, open(c, code + 1, trip.id, claim = true))
        val dismiss = action(c, R.string.notif_dismiss, PendingIntent.getBroadcast(c, code + 2,
            Intent(c, NotifActionReceiver::class.java).setAction(ACTION_DISMISS).putExtra(TrackingService.EXTRA_TRIP_ID, trip.id), FLAGS))
        show(c, code, build(c, CH_DELAY, c.getString(R.string.core_delay_title, trip.train, trip.delayMin),
            c.getString(R.string.core_delay_text, trip.from, trip.to), open(c, code, trip.id), claim, dismiss))
    }

    /** Clears the delay notification of [tripId], e.g. after "Not relevant". */
    fun cancel(c: Context, tripId: String) = c.getSystemService(NotificationManager::class.java).cancel(tripId.hashCode())

    fun monthlyClaim(c: Context, month: YearMonth, ct: Int) {
        val code = "claim-$month".hashCode()
        show(c, code, build(c, CH_CLAIM, c.getString(R.string.core_claim_title, month.month.getDisplayName(TextStyle.FULL_STANDALONE, appLocale())),
            c.getString(R.string.core_claim_text, money(ct)), open(c, code, null)))
    }

    /** After a reboot or update Android 14+ won't start location tracking unseen; a tap on this does. */
    fun resume(c: Context) = show(c, ID_RESUME, build(c, CH_RESUME, c.getString(R.string.core_resume_title), c.getString(R.string.core_resume_text),
        PendingIntent.getForegroundService(c, ID_RESUME, Intent(c, TrackingService::class.java), FLAGS)))

    fun clearResume(c: Context) = c.getSystemService(NotificationManager::class.java).cancel(ID_RESUME)

    /** [requestCode] must differ per extra, or PendingIntents with equal intents share (and overwrite) one extra. */
    private fun open(c: Context, requestCode: Int, tripId: String?, claim: Boolean = false): PendingIntent {
        val intent = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        tripId?.let { intent.putExtra(TrackingService.EXTRA_TRIP_ID, it) }
        if (claim) intent.putExtra(EXTRA_CLAIM, true)
        return PendingIntent.getActivity(c, requestCode, intent, FLAGS)
    }

    private fun action(c: Context, label: Int, pi: PendingIntent) =
        Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_stat_delayd), c.getString(label), pi).build()

    private fun build(c: Context, channel: String, title: String, text: String, tap: PendingIntent, vararg actions: Notification.Action) =
        Notification.Builder(c, channel).setSmallIcon(R.drawable.ic_stat_delayd).setContentTitle(title).setContentText(text)
            .setContentIntent(tap).setAutoCancel(channel != CH_TRACK).apply { actions.forEach(::addAction) }.build()

    private fun show(c: Context, id: Int, n: Notification) = c.getSystemService(NotificationManager::class.java).notify(id, n)
}
