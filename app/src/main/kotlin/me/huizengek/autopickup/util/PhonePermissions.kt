package me.huizengek.autopickup.util

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission

val permissions = arrayOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ANSWER_PHONE_CALLS
)

@get:RequiresPermission(
    allOf = [
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS
    ],
    conditional = true
)
val Context.hasSufficientPermissions get() = permissions.all { hasPermission(it) }
