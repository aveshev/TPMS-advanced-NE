package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/**
 * How many settings screens are composed, so that the host knows the user is looking at one. A count
 * rather than a flag: while navigating between two of them, the next one enters before the
 * previous one leaves.
 */
private val LocalSettingsOnScreen = compositionLocalOf<MutableIntState> {
    error("No PersistentScanningHost above this composable")
}

/** Tells the host that a settings screen is visible, for as long as the caller is composed */
@Composable
internal fun SettingsOnScreenEffect() {
    val settingsOnScreen = LocalSettingsOnScreen.current
    DisposableEffect(settingsOnScreen) {
        settingsOnScreen.intValue += 1
        onDispose { settingsOnScreen.intValue -= 1 }
    }
}

/** The journey behind the WiFi exception toggle, so that it only turns on once its permission is held */
internal val LocalWifiExceptionJourney = compositionLocalOf<PermissionJourney> {
    error("No PersistentScanningHost above this composable")
}

/**
 * Must wrap every [MonitoringButton]. It owns the one permission journey (and its dialogs) that the
 * bell and the app-open check share: two independent journeys would both react when the user comes
 * back from Settings and show their dialogs twice.
 *
 * Every time the app is opened while persistent scanning is on, it also checks that the service
 * still has what it needs (walking the user through what is missing, once) and that it is running.
 */
@Composable
public fun PersistentScanningHost(content: @Composable () -> Unit): Unit =
    PersistentScanningHost(
        viewModel { Bindings.featureBackgroundInternal.persistentScanningViewModel() },
        content,
    )

@Composable
internal fun PersistentScanningHost(
    viewModel: PersistentScanningViewModel,
    content: @Composable () -> Unit,
) {
    val persistentScanning by viewModel.persistentScanning.collectAsState()
    val permissions = rememberMonitoringPermissions(
        confirmBeforeStart = false,
        onGranted = viewModel::ensureRunning,
    )
    val currentPermissions by rememberUpdatedState(permissions)
    LifecycleStartEffect(persistentScanning) {
        // When everything is granted this only makes sure the service runs, without any popup.
        // Coming back from Settings also starts the lifecycle again: the journey started before
        // leaving is resumed by the permissions themselves, it must not be started a second time.
        if (persistentScanning && currentPermissions.isInProgress().not()) currentPermissions.request()
        onStopOrDispose { }
    }

    // The WiFi exception needs permissions of its own: they are asked for every time the app is
    // opened while the settings in place need them, not only when the settings screen is visible.
    val wifiExceptionActive by viewModel.wifiExceptionActive.collectAsState(initial = false)
    val settingsOnScreen = remember { mutableIntStateOf(0) }
    val wifiJourney = rememberWifiExceptionJourney(
        permissions = viewModel.requiredWifiPermissions(),
        isSettingsOnScreen = { settingsOnScreen.intValue > 0 },
        onDenied = viewModel::disableWifiException,
    )
    val currentWifiJourney by rememberUpdatedState(wifiJourney)
    // Read here to key the effect: the journey waits for the monitoring one to end
    val monitoringJourneyRunning = permissions.isInProgress()
    LifecycleStartEffect(wifiExceptionActive, monitoringJourneyRunning) {
        // One system dialog at a time. Both journeys start together when the app opens, the
        // monitoring one goes first (its effect is declared first) and its end runs this again.
        if (
            wifiExceptionActive &&
            currentPermissions.isInProgress().not() &&
            currentWifiJourney.isInProgress().not()
        ) {
            currentWifiJourney.request()
        }
        onStopOrDispose { }
    }
    CompositionLocalProvider(
        LocalMonitoringPermissions provides permissions,
        LocalSettingsOnScreen provides settingsOnScreen,
        LocalWifiExceptionJourney provides wifiJourney,
        content = content,
    )
}
