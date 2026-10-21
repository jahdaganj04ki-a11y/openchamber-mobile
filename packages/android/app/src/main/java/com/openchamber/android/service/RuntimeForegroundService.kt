package com.openchamber.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.openchamber.android.MainActivity
import com.openchamber.android.OpenChamberApplication
import com.openchamber.android.R
import com.openchamber.android.core.runtime.RuntimeState
import kotlinx.coroutines.launch

/**
 * The foreground service that keeps the server and the engine alive.
 *
 * Android kills background processes when it needs the memory, and a run that
 * dies mid-turn loses the work. The service holds the pair in the foreground
 * and shows what the engine is doing.
 */
class RuntimeForegroundService : android.app.Service() {

    private var running = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as OpenChamberApplication).container
        if (intent?.action == ACTION_STOP) {
            container.scope.launch { container.supervisor.stop() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(container.supervisor.state.value))
        container.scope.launch {
            container.supervisor.state.collect { state ->
                notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
        if (!running) {
            running = true
            container.supervisor.start()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        val container = (application as OpenChamberApplication).container
        container.scope.launch { container.supervisor.stop() }
        super.onDestroy()
    }

    private fun buildNotification(state: RuntimeState): Notification {
        val text = when (state) {
            is RuntimeState.Stopped -> getString(R.string.runtime_notification_title)
            is RuntimeState.Starting -> state.stage
            is RuntimeState.ServerReady -> "The server is up, the engine is starting"
            is RuntimeState.EngineStarting -> "The engine is starting"
            is RuntimeState.Ready -> "Ready"
            is RuntimeState.Failed -> state.reason
        }
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RuntimeForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.runtime_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.runtime_notification_stop), stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.runtime_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "openchamber.runtime"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_STOP = "com.openchamber.android.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RuntimeForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
