package me.huizengek.autopickup.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
fun SettingsGroupText(
    title: String,
    modifier: Modifier = Modifier
) = Text(
    text = title.uppercase(),
    style = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    ),
    modifier = modifier
        .padding(start = 16.dp)
        .padding(horizontal = 16.dp)
)

@Composable
fun SettingsGroupSpacer(modifier: Modifier = Modifier) = Spacer(modifier = modifier.height(24.dp))

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) = CompositionLocalProvider(
    LocalSettingsGroup provides SettingsGroup(
        enabled = enabled
    )
) {
    Column(modifier = modifier) {
        SettingsGroupText(title = title)
        content()
        SettingsGroupSpacer()
    }
}

val LocalSettingsGroup = compositionLocalOf<SettingsGroup> {
    error("No SettingsGroup provided, you should only call this inside a SettingsGroup!")
}

@Immutable
data class SettingsGroup(
    val enabled: Boolean
)

@Composable
fun SettingsEntry(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val group = LocalSettingsGroup.current
    val animatedAlpha by animateFloatAsState(
        targetValue = if (group.enabled && enabled) 1f else 0.5f,
        label = ""
    )

    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = description?.let { { Text(text = it) } },
        trailingContent = trailingContent,
        leadingContent = { },
        modifier = modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .let {
                if (onClick == null || !(group.enabled && enabled)) it
                else it.clickable(onClick = onClick)
            }
    )
}

@Composable
fun SwitchSettingsEntry(
    title: String,
    state: Boolean,
    setState: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) = SettingsEntry(
    modifier = modifier,
    title = title,
    description = description,
    onClick = { setState(!state) },
    enabled = enabled,
    trailingContent = {
        val group = LocalSettingsGroup.current

        Box {
            Switch(
                checked = state,
                onCheckedChange = { setState(it) },
                enabled = group.enabled && enabled,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
)

@Suppress("UnusedReceiverParameter")
@Composable
fun ColumnScope.SliderSettingsEntry(
    title: String,
    state: Float,
    setState: (Float) -> Unit,
    onSlide: () -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    valueDisplayText: (Float) -> String = { "$it" }
) {
    SettingsEntry(
        title = title,
        modifier = modifier,
        description = valueDisplayText(state),
        enabled = enabled
    )

    Slider(
        value = state,
        onValueChange = { setState(it) },
        onValueChangeFinished = onSlide,
        valueRange = range,
        steps = steps,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
inline fun <T> ValueSelectorSettingsEntry(
    title: String,
    selectedValue: T,
    values: ImmutableList<T>,
    crossinline onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    crossinline valueDisplayText: @Composable (T) -> String = { it.toString() },
    noinline trailingContent: (@Composable () -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }

    if (open) ValueSelectorDialog(
        onDismiss = { open = false },
        title = title,
        selectedValue = selectedValue,
        values = values,
        onValueSelected = onValueSelected,
        valueDisplayText = valueDisplayText
    )

    SettingsEntry(
        modifier = modifier,
        title = title,
        description = valueDisplayText(selectedValue),
        onClick = { open = true },
        enabled = enabled,
        trailingContent = trailingContent
    )
}

@Composable
inline fun <T> ValueSelectorDialog(
    title: String,
    selectedValue: T,
    values: ImmutableList<T>,
    crossinline onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    crossinline valueDisplayText: @Composable (T) -> String = { it.toString() },
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    noinline onDismiss: () -> Unit
) = Dialog(onDismissRequest = onDismiss) {
    Surface(
        shape = shape,
        color = containerColor,
        tonalElevation = tonalElevation,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(vertical = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                values.forEach { value ->
                    ListItem(
                        headlineContent = { Text(text = valueDisplayText(value)) },
                        leadingContent = {
                            RadioButton(
                                selected = selectedValue == value,
                                onClick = {
                                    onValueSelected(value)
                                    onDismiss()
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onValueSelected(value)
                                onDismiss()
                            },
                        colors = ListItemDefaults.colors(containerColor = containerColor)
                    )
                }
            }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> EnumSelectorSettingsEntry(
    title: String,
    selectedValue: T,
    crossinline onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    crossinline valueDisplayText: @Composable (T) -> String = { it.name },
    noinline trailingContent: (@Composable () -> Unit)? = null
) = ValueSelectorSettingsEntry(
    modifier = modifier,
    title = title,
    selectedValue = selectedValue,
    values = enumValues<T>().toList().toImmutableList(),
    onValueSelected = onValueSelected,
    enabled = enabled,
    valueDisplayText = valueDisplayText,
    trailingContent = trailingContent
)
