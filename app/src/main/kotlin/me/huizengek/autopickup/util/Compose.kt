package me.huizengek.autopickup.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun isCompositionLaunched(): Boolean {
    var launched by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launched = true
    }

    return launched
}
