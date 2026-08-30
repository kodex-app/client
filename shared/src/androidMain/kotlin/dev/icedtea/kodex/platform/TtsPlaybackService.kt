package dev.icedtea.kodex.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * What the read-aloud notification's buttons call. Set by [TtsMediaControls] while the reader is
 * composed; the service holds no reader state of its own, it only forwards presses.
 */
internal interface TtsRemoteHandler {
    fun playPause()
    fun skip(delta: Int)
    fun stop()
}

/**
 * The bridge between the notification (a system component, created by Android whenever it likes) and
 * the composition that actually owns the speech. Process-global because there is exactly one reader
 * at a time and the service has no way to reach into the composition otherwise; cleared on dispose,
 * so a stale reader can never be driven by a leftover notification.
 */
internal object TtsRemote {
    @Volatile
    var handler: TtsRemoteHandler? = null
}

/**
 * Foreground service behind the read-aloud notification.
 *
 * Two jobs, and the second is the one that isn't obvious: it shows the transport controls, and it
 * keeps the app alive while it reads. Without a foreground service a backgrounded app is a candidate
 * for being killed at any moment — which for a book being listened to with the screen off means the
 * voice simply stops mid-sentence. The notification is the price Android charges for that, and it is
 * also the only way to pause without going back into the app.
 */
class TtsPlaybackService : Service() {

    private var title: String = ""
    private var subtitle: String = ""
    private var playing: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> TtsRemote.handler?.playPause()
            ACTION_PREV -> TtsRemote.handler?.skip(-1)
            ACTION_NEXT -> TtsRemote.handler?.skip(1)
            ACTION_STOP -> {
                TtsRemote.handler?.stop()
                // The reader answers by tearing the controls down, which stops this service; doing it
                // here too means the notification goes the moment it is pressed either way.
                stopSelfSafely()
                return START_NOT_STICKY
            }
        }
        // Every start carries the current state (the reader re-sends it whenever anything changes),
        // so one path builds the notification and a button press just refreshes it.
        intent?.getStringExtra(EXTRA_TITLE)?.let { title = it }
        intent?.getStringExtra(EXTRA_SUBTITLE)?.let { subtitle = it }
        if (intent?.hasExtra(EXTRA_PLAYING) == true) playing = intent.getBooleanExtra(EXTRA_PLAYING, false)

        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        // Not sticky: a service restarted by the system would have no speech to control — the reader
        // it belonged to is gone.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSelfSafely()
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle(title.ifBlank { "Read aloud" })
        .setContentText(subtitle)
        .setContentIntent(launchAppIntent())
        .setOngoing(playing)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .addAction(android.R.drawable.ic_media_previous, "Previous paragraph", action(ACTION_PREV))
        .addAction(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (playing) "Pause" else "Play",
            action(ACTION_PLAY_PAUSE),
        )
        .addAction(android.R.drawable.ic_media_next, "Next paragraph", action(ACTION_NEXT))
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", action(ACTION_STOP))
        .build()

    /** Tapping the notification body returns to wherever the app was — i.e. the open reader. */
    private fun launchAppIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this,
        name.hashCode(),
        Intent(this, TtsPlaybackService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // LOW: this notification is a control surface, not news — it should never make a sound or
        // slide over what the user is doing.
        val channel = NotificationChannel(CHANNEL_ID, "Read aloud", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Playback controls while a book is being read aloud"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    internal companion object {
        const val ACTION_UPDATE = "dev.icedtea.kodex.tts.UPDATE"
        const val ACTION_PLAY_PAUSE = "dev.icedtea.kodex.tts.PLAY_PAUSE"
        const val ACTION_PREV = "dev.icedtea.kodex.tts.PREV"
        const val ACTION_NEXT = "dev.icedtea.kodex.tts.NEXT"
        const val ACTION_STOP = "dev.icedtea.kodex.tts.STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val EXTRA_PLAYING = "playing"

        private const val CHANNEL_ID = "kodex.tts"
        private const val NOTIFICATION_ID = 4201

        /** The state the notification should show right now; every change re-sends the whole thing. */
        fun updateIntent(context: Context, title: String, subtitle: String, playing: Boolean): Intent =
            Intent(context, TtsPlaybackService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SUBTITLE, subtitle)
                .putExtra(EXTRA_PLAYING, playing)
    }
}
