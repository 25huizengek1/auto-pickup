package me.huizengek.autopickup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import me.huizengek.autopickup.service.ListenerService

class CallStateReceiver private constructor(
    private val receiver: ListenerService.Receiver? = null
) : BroadcastReceiver() {
    companion object {
        const val ACTION = "android.intent.action.PHONE_STATE"

        fun register(
            context: Context,
            receiver: ListenerService.Receiver
        ) = CallStateReceiver(receiver).apply {
            ContextCompat.registerReceiver(
                /* context = */ context,
                /* receiver = */ this,
                /* filter = */ IntentFilter(ACTION),
                /* flags = */ ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        if (state != TelephonyManager.EXTRA_STATE_RINGING) {
            context?.sendStop()
            return
        }

        val audioManager = context?.getSystemService<AudioManager>() ?: return
        if (audioManager.mode != AudioManager.MODE_IN_CALL) {
            context.sendStop()
            return
        }

        context.sendStart()
    }

    private fun Context.sendStart() = receiver?.startRinging?.pendingIntent?.send() ?: Unit
    private fun Context.sendStop() = receiver?.stopRinging?.pendingIntent?.send() ?: Unit
}
