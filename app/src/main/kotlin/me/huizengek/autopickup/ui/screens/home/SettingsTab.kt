package me.huizengek.autopickup.ui.screens.home

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.huizengek.autopickup.LocalListenerServiceBinder
import me.huizengek.autopickup.R
import me.huizengek.autopickup.preferences.PhonePreferences
import me.huizengek.autopickup.preferences.ThemePreferences
import me.huizengek.autopickup.service.AddTileServiceResult
import me.huizengek.autopickup.service.ListenerService
import me.huizengek.autopickup.service.StatusTileService
import me.huizengek.autopickup.service.coroutineScope
import me.huizengek.autopickup.ui.components.EnumSelectorSettingsEntry
import me.huizengek.autopickup.ui.components.LocalSettingsGroup
import me.huizengek.autopickup.ui.components.SettingsEntry
import me.huizengek.autopickup.ui.components.SettingsGroup
import me.huizengek.autopickup.ui.components.SwitchSettingsEntry
import me.huizengek.autopickup.util.currentLocale
import me.huizengek.autopickup.util.findActivity
import me.huizengek.autopickup.util.hasSufficientPermissions
import me.huizengek.autopickup.util.isAtLeastAndroid13
import me.huizengek.autopickup.util.isCompositionLaunched
import me.huizengek.autopickup.util.isIgnoringBatteryOptimizations
import me.huizengek.autopickup.util.permissions
import me.huizengek.autopickup.util.startLanguagePicker
import me.huizengek.autopickup.util.toast

@SuppressLint("BatteryLife")
@Composable
fun SettingsTab(modifier: Modifier = Modifier) = Column(
    modifier = modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
) {
    val context = LocalContext.current
    val binder = LocalListenerServiceBinder.current

    var hasPermission by remember(context, isCompositionLaunched()) {
        mutableStateOf(context.hasSufficientPermissions)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (permissions.all { result[it] == true }) hasPermission = true
    }

    var isIgnoringBatteryOptimizations by remember(context, isCompositionLaunched()) {
        mutableStateOf(context.isIgnoringBatteryOptimizations)
    }

    val activityResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations }
    )

    val enabled =
        remember(hasPermission, isIgnoringBatteryOptimizations, PhonePreferences.enabled) {
            hasPermission && isIgnoringBatteryOptimizations && PhonePreferences.enabled
        }

    TextButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        onClick = {
            PhonePreferences.enabled = !PhonePreferences.enabled
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.enable),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .weight(1f)
            )
            Switch(
                checked = PhonePreferences.enabled,
                onCheckedChange = { PhonePreferences.enabled = it }
            )
        }
    }

    AnimatedVisibility(
        visible = PhonePreferences.enabled && !enabled,
        label = ""
    ) {
        SettingsGroup(title = stringResource(R.string.permissions)) {
            SettingsEntry(
                title = if (hasPermission) stringResource(R.string.permission_granted)
                else stringResource(R.string.permission_not_granted),
                description = if (hasPermission) null else stringResource(R.string.tap_to_grant),
                enabled = !hasPermission,
                onClick = {
                    permissionLauncher.launch(permissions)
                }
            )

            SettingsEntry(
                title = stringResource(R.string.ignore_battery_optimizations),
                description = if (isIgnoringBatteryOptimizations) stringResource(R.string.ignoring_battery_optimizations)
                else stringResource(R.string.ignore_battery_optimizations_action),
                onClick = {
                    try {
                        activityResultLauncher.launch(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } catch (e: ActivityNotFoundException) {
                        try {
                            activityResultLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (e: ActivityNotFoundException) {
                            context.toast(context.getString(R.string.no_battery_optimization_settings_found))
                        }
                    }
                },
                enabled = !isIgnoringBatteryOptimizations
            )
        }
    }

    SettingsGroup(
        title = stringResource(R.string.answering_calls),
        enabled = enabled
    ) {
        if (
            context.hasSystemFeatures(
                "android.hardware.sensor.accelerometer",
                "android.hardware.sensor.proximity"
            )
        ) SwitchSettingsEntry(
            title = stringResource(R.string.answer_phone_to_ear),
            state = PhonePreferences.phoneToEar,
            setState = { PhonePreferences.phoneToEar = it }
        )

        SwitchSettingsEntry(
            title = stringResource(R.string.answer_bluetooth),
            state = PhonePreferences.connectedBluetooth,
            setState = { PhonePreferences.connectedBluetooth = it }
        )
    }

    SettingsGroup(
        title = stringResource(R.string.other_settings),
        enabled = enabled
    ) {
        SwitchSettingsEntry(
            title = stringResource(R.string.vibrate),
            state = PhonePreferences.vibrate,
            setState = { PhonePreferences.vibrate = it }
        )

        SwitchSettingsEntry(
            title = stringResource(R.string.countdown),
            state = PhonePreferences.countdown,
            setState = { PhonePreferences.countdown = it }
        )

        if (isAtLeastAndroid13) SettingsEntry(
            title = stringResource(R.string.add_to_quick_settings),
            description = stringResource(R.string.add_to_quick_settings_description),
            onClick = {
                coroutineScope.launch {
                    if (StatusTileService.requestAddService(context) == AddTileServiceResult.AlreadyAdded)
                        context.toast(context.getString(R.string.add_to_quick_settings_error))
                }
            }
        )
    }

    SettingsGroup(
        title = stringResource(R.string.testing),
        enabled = hasPermission && PhonePreferences.enabled
    ) {
        val group = LocalSettingsGroup.current
        val calling = remember(binder, binder?.state) {
            binder?.state == ListenerService.State.Calling
        }

        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            val rotation by animateFloatAsState(
                targetValue = if (calling) 135f else 0f,
                label = ""
            )

            OutlinedIconButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(start = 16.dp),
                onClick = {
                    if (calling) binder?.stopRinging() else binder?.startRinging()
                },
                enabled = group.enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }

    if (isAtLeastAndroid13) SettingsGroup(title = stringResource(R.string.language)) {
        SettingsEntry(
            title = stringResource(R.string.language),
            description = currentLocale()?.displayLanguage ?: stringResource(R.string.default_text),
            onClick = context.findActivity()::startLanguagePicker
        )
    }

    SettingsGroup(title = stringResource(R.string.theme)) {
        EnumSelectorSettingsEntry(
            title = stringResource(R.string.theme),
            selectedValue = ThemePreferences.theme,
            onValueSelected = { ThemePreferences.theme = it },
            valueDisplayText = { it.displayName() }
        )
        SwitchSettingsEntry(
            title = stringResource(R.string.dynamic_theme),
            description = stringResource(R.string.dynamic_theme_description),
            state = ThemePreferences.isDynamic,
            setState = { ThemePreferences.isDynamic = it }
        )
    }
}

fun Context.hasSystemFeatures(vararg features: String) = packageManager?.let { manager ->
    features.all { manager.hasSystemFeature(it) }
} == true
