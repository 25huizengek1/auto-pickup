package me.huizengek.autopickup.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.huizengek.autopickup.BuildConfig
import me.huizengek.autopickup.R
import me.huizengek.autopickup.preferences.PhonePreferences
import me.huizengek.autopickup.receiver.CallStateReceiver
import me.huizengek.autopickup.util.ActionReceiver
import me.huizengek.autopickup.util.hasSufficientPermissions
import me.huizengek.autopickup.util.intent
import me.huizengek.autopickup.util.isAtLeastAndroid10
import me.huizengek.autopickup.util.isAtLeastAndroid12
import me.huizengek.autopickup.util.pendingActivity
import me.huizengek.autopickup.util.toast
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import android.os.Binder as AndroidBinder

class ListenerService : InvincibleService() {
    companion object {
        const val CHANNEL_ID = "incoming_call"
    }

    private val audioManager by lazy { getSystemService<AudioManager>() }
    private val telecomManager by lazy { getSystemService<TelecomManager>() }
    private val vibrator by lazy { getSystemService<Vibrator>() }
    private val sensorManager by lazy { getSystemService<SensorManager>() }
    private val proximitySensor by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY) }
    private val accelerometer by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    private val toneGenerator by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }

    private var proximity: Float? = null
    private var proximityThreshold: Float? = null
    private var inclination: Int? = null

    private val binder = Binder()
    private val receiver = Receiver()
    private lateinit var callStateReceiver: CallStateReceiver

    override val isInvincibilityEnabled = true
    override val notificationId = 1000

    override fun shouldBeInvincible() = true

    override fun startForeground() {
        val channel = NotificationChannelCompat.Builder(
            /* id = */ CHANNEL_ID,
            /* importance = */ NotificationManager.IMPORTANCE_LOW
        )
            .setName(getString(R.string.foreground_service))
            .setLightsEnabled(false)
            .setVibrationEnabled(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(
            /* context = */ this,
            /* channelId = */ CHANNEL_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentText(getText(R.string.listening))
            .setContentIntent(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
                }.pendingActivity
            )
            .build()

        startForeground(notificationId, notification)
    }

    override fun onCreate() {
        super.onCreate()

        startForegroundService(intent<ListenerService>())
        startForeground()
        receiver.register(ContextCompat.RECEIVER_NOT_EXPORTED)
        callStateReceiver = CallStateReceiver.register(this, receiver)
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
        unregisterReceiver(callStateReceiver)

        toneGenerator.release()
    }

    override fun onBind(intent: Intent?): AndroidBinder {
        super.onBind(intent)
        return binder
    }

    val Float.fastInverseSquareRoot
        get(): Float {
            var y =
                java.lang.Float.intBitsToFloat(0x5f3759df - (java.lang.Float.floatToRawIntBits(this) shr 1))
            y *= 1.5f - this * 0.5f * y * y

            return y
        }

    enum class State {
        Idle, Calling
    }

    inner class Binder : AndroidBinder() {
        private val coroutineScope = CoroutineScope(Dispatchers.IO)

        @get:Synchronized
        @set:Synchronized
        private var job: Job? by mutableStateOf(null)
        val state by derivedStateOf { if (job == null) State.Idle else State.Calling }

        private val listener = SensorChangeListener {
            when (it.sensor?.type) {
                Sensor.TYPE_PROXIMITY -> proximity = it.values.getOrNull(0)
                    .also { log("New proximity: $proximity / $proximityThreshold") }

                Sensor.TYPE_ACCELEROMETER -> {
                    var (x, y, z) = it.values
                    val normalizationFactor = (x * x + y * y + z * z).fastInverseSquareRoot

                    x *= normalizationFactor
                    y *= normalizationFactor

                    inclination = Math.toDegrees(
                        atan2(x, y).toDouble()
                    ).roundToInt()
                    log("New inclination: $inclination degrees")
                }
            }
        }

        private val AudioDeviceInfo.isBluetoothDevice
            get() = isSink && type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO

        private fun shouldAnswer(): Boolean {
            @Suppress("DEPRECATION")
            if (PhonePreferences.connectedBluetooth) when {
                isAtLeastAndroid12 && audioManager?.communicationDevice?.isBluetoothDevice == true -> return true

                audioManager?.isBluetoothScoOn == true -> return true
                audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    ?.any { it.isBluetoothDevice } == true -> return true
            }

            if (PhonePreferences.phoneToEar && inclination in (-90..90)) {
                val proximity = proximity ?: return false
                val proximityThreshold = proximityThreshold ?: return false

                return proximity <= proximityThreshold
            }

            return false
        }

        private suspend fun maybeCountdown() {
            if (!PhonePreferences.countdown) return

            repeat(3) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 100)
                delay(1000)
            }
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ANSWER, 750)
            delay(1250)
        }

        private suspend fun maybeVibrate() {
            if (!PhonePreferences.vibrate) return

            vibrator?.vibrate(
                if (isAtLeastAndroid10) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                else VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            ) ?: return

            delay(200)

            vibrator?.vibrate(
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        fun startRinging() {
            if (!PhonePreferences.enabled) {
                stopRinging()
                return
            }

            proximityThreshold = proximitySensor?.maximumRange?.let { it / 2 }

            sensorManager?.registerListener(
                listener,
                proximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )

            sensorManager?.registerListener(
                listener,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
            )

            job?.cancel()
            job = coroutineScope.launch {
                runCatching {
                    while (isActive) {
                        delay(400.milliseconds)

                        if (!shouldAnswer()) continue

                        maybeCountdown()
                        if (hasSufficientPermissions) withContext(Dispatchers.Main) {
                            telecomManager?.acceptRingingCall()
                        } else toast(getString(R.string.permission_not_granted))

                        maybeVibrate()
                        break
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    else it.printStackTrace()
                }
            }.apply {
                invokeOnCompletion {
                    stopRinging()
                }
            }
        }

        fun stopRinging() {
            job?.cancel()
            job = null

            sensorManager?.unregisterListener(listener, proximitySensor)
            sensorManager?.unregisterListener(listener, accelerometer)

            proximity = null
            proximityThreshold = null
            inclination = null
        }
    }

    inner class Receiver : ActionReceiver("${BuildConfig.APPLICATION_ID}.service") {
        val startRinging by action {
            binder.startRinging()
        }

        val stopRinging by action {
            binder.stopRinging()
        }
    }
}

fun interface SensorChangeListener : SensorEventListener {
    fun log(msg: String) = Log.d("SensorChangeListener", msg)
    fun SensorChangeListener.onEvent(event: SensorEvent)

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) onEvent(event)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
