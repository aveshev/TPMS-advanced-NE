package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.LocalSettingsOnScreen
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.rememberMonitoringPermissions
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel

@Composable
public fun PersistentScanningSettings(modifier: Modifier = Modifier): Unit =
    PersistentScanningSettings(
        modifier,
        viewModel { PersistentScanningSettingsViewModel() },
    )

@Composable
internal fun PersistentScanningSettings(
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) {
    val persistentScanning by viewModel.persistentScanning.collectAsState()
    val settingsOnScreen = LocalSettingsOnScreen.current
    DisposableEffect(settingsOnScreen) {
        settingsOnScreen.value = true
        onDispose { settingsOnScreen.value = false }
    }
    // The toggle only turns on once everything the service needs was granted; refusing leaves it off
    val permissions = rememberMonitoringPermissions(onGranted = viewModel::enablePersistentScanning)

    Column(modifier) {
        ToggleRow(
            text = "Persistent scanning",
            checked = persistentScanning,
            onCheckedChange = { enabled ->
                if (enabled) permissions.request() else viewModel.disablePersistentScanning()
            },
            modifier = Modifier.testTag(PersistentScanningSettingsTags.persistentScanning),
        )
        if (persistentScanning) {
            GroupHeader("Suspend scan conditions")
            SuspendScanConditions(viewModel)
            GroupHeader("Activate scan conditions")
            ActivateScanConditions(viewModel)
        }
    }
}

@Composable
private fun GroupHeader(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
)

@Suppress("ConstPropertyName")
internal object PersistentScanningSettingsTags {
    const val persistentScanning = "PersistentScanningSettingsTags_persistentScanning"
    const val suspendInDoze = "PersistentScanningSettingsTags_suspendInDoze"
    const val suspendOnWifi = "PersistentScanningSettingsTags_suspendOnWifi"
    const val wifiExceptionEnabled = "PersistentScanningSettingsTags_wifiExceptionEnabled"
    const val currentWifiException = "PersistentScanningSettingsTags_currentWifiException"
    const val activateOnCable = "PersistentScanningSettingsTags_activateOnCable"
    const val activateOnWireless = "PersistentScanningSettingsTags_activateOnWireless"
    const val activateOnAndroidAuto = "PersistentScanningSettingsTags_activateOnAndroidAuto"
    const val stayActive = "PersistentScanningSettingsTags_stayActive"
    const val stayActiveMinutes = "PersistentScanningSettingsTags_stayActiveMinutes"
    const val justScan = "PersistentScanningSettingsTags_justScan"
}
