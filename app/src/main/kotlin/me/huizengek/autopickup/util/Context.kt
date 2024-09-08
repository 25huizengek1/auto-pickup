package me.huizengek.autopickup.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.getSystemService

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
inline val isAtLeastAndroid10
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
inline val isAtLeastAndroid11
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
inline val isAtLeastAndroid12
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
inline val isAtLeastAndroid13
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

val Context.isIgnoringBatteryOptimizations
    get() = getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) ?: true

fun Context.hasPermission(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

context(Context)
inline fun <reified T> intent() = Intent(this@Context, T::class.java)

context(Context)
val Intent.pendingActivity: PendingIntent
    get() = PendingIntent.getActivity(
        /* context = */ this@Context,
        /* requestCode = */ 0,
        /* intent = */ this,
        /* flags = */ PendingIntent.FLAG_IMMUTABLE
    )

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("Should be called in the context of an Activity")
}

private val handler = Handler(Looper.getMainLooper())

fun Context.toast(
    message: String,
    duration: ToastDuration = ToastDuration.Short
) = handler.post {
    Toast.makeText(this, message, duration.length).show()
}

@JvmInline
value class ToastDuration private constructor(internal val length: Int) {
    companion object {
        val Short = ToastDuration(length = Toast.LENGTH_SHORT)
        val Long = ToastDuration(length = Toast.LENGTH_LONG)
    }
}
