package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/**
 * Every time the app is opened while persistent scanning is on, checks that the service still has
 * what it needs (walking the user through the missing permissions, once) and that it is running.
 */
@Composable
public fun PersistentScanningAppOpenEffect(): Unit =
    PersistentScanningAppOpenEffect(
        viewModel { Bindings.featureBackgroundInternal.persistentScanningViewModel() },
    )

@Composable
internal fun PersistentScanningAppOpenEffect(viewModel: PersistentScanningViewModel) {
    val persistentScanning by viewModel.persistentScanning.collectAsState()
    val permissions by rememberUpdatedState(
        rememberMonitoringPermissions(onGranted = viewModel::ensureRunning)
    )
    LifecycleStartEffect(persistentScanning) {
        // When everything is granted this only makes sure the service runs, without any popup.
        // Coming back from Settings also starts the lifecycle again: the journey started before
        // leaving is resumed by the permissions themselves, it must not be started a second time.
        if (persistentScanning && permissions.isInProgress().not()) permissions.request()
        onStopOrDispose { }
    }
}
