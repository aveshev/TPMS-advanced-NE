package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

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
    val permissions = rememberMonitoringPermissions(onGranted = viewModel::ensureRunning)
    val currentPermissions by rememberUpdatedState(permissions)
    LifecycleStartEffect(persistentScanning) {
        // When everything is granted this only makes sure the service runs, without any popup.
        // Coming back from Settings also starts the lifecycle again: the journey started before
        // leaving is resumed by the permissions themselves, it must not be started a second time.
        if (persistentScanning && currentPermissions.isInProgress().not()) currentPermissions.request()
        onStopOrDispose { }
    }
    CompositionLocalProvider(LocalMonitoringPermissions provides permissions, content = content)
}
