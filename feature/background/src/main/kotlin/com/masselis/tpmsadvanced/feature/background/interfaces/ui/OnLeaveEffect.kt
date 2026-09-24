package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Calls [onLeave] once the caller leaves the composition, unless it is only rebuilt for a
 * configuration change such as a rotation.
 */
@Composable
internal fun OnLeaveEffect(onLeave: () -> Unit) {
    val activity = LocalActivity.current
    val currentOnLeave by rememberUpdatedState(onLeave)
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isChangingConfigurations == true) return@onDispose
            currentOnLeave()
        }
    }
}
