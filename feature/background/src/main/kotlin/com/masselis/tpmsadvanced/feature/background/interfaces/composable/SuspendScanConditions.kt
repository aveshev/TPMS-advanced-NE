package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod")
@Composable
internal fun SuspendScanConditions(
    viewModel: PersistentScanningSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val suspendInDoze by viewModel.suspendScanningInDoze.collectAsState()
    val suspendOnWifi by viewModel.suspendScanningOnWifi.collectAsState()
    val exceptionEnabled by viewModel.wifiExceptionEnabled.collectAsState()
    val exceptedSsids by viewModel.exceptedWifiSsids.collectAsState()
    val permissionState = rememberMultiplePermissionsState(viewModel.missingWifiPermission())
    // Keyed on the grant flag: WifiConnectionUseCase's NetworkCallback delivers redacted data
    // (null SSID) while ungranted, and Android doesn't re-deliver capabilities just because
    // permission was newly granted — restarting the collection forces a fresh registration, which
    // reads correctly the moment the permission is actually held.
    val wifiState by produceState<WifiConnectionUseCase.State>(
        initialValue = WifiConnectionUseCase.State.Disconnected,
        key1 = permissionState.allPermissionsGranted,
    ) { viewModel.wifiConnectionState.collect { value = it } }

    Column(modifier) {
        ToggleRow(
            text = "Suspend scanning while phone is idle (Doze)",
            checked = suspendInDoze,
            onCheckedChange = { viewModel.suspendScanningInDoze.value = it },
            modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendInDoze),
        )
        ToggleRow(
            text = "Suspend scanning when connected to WiFi",
            checked = suspendOnWifi,
            onCheckedChange = { viewModel.suspendScanningOnWifi.value = it },
            modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendOnWifi),
        )
        ToggleRow(
            text = "Make exception for certain WiFis (requires permission)",
            checked = exceptionEnabled,
            enabled = suspendOnWifi,
            onCheckedChange = { viewModel.wifiExceptionEnabled.value = it },
            modifier = Modifier
                .padding(start = 24.dp)
                .testTag(PersistentScanningSettingsTags.wifiExceptionEnabled),
        )
        if (suspendOnWifi && exceptionEnabled) {
            if (permissionState.allPermissionsGranted.not()) {
                Text(
                    "Location permission required",
                    modifier = Modifier.padding(start = 24.dp),
                )
            } else {
                val ssid = (wifiState as? WifiConnectionUseCase.State.Connected)?.ssid
                CurrentWifiExceptionRow(
                    ssid = ssid,
                    checked = ssid != null && ssid in exceptedSsids,
                    onCheckedChange = { ssid?.let(viewModel::toggleExceptedSsid) },
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun CurrentWifiExceptionRow(
    ssid: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ssid != null) {
        CheckboxRow(
            text = "Keep scanning when on current WiFi \"$ssid\"",
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier.testTag(PersistentScanningSettingsTags.currentWifiException),
        )
    } else {
        Text(
            "Not connected to Wi-Fi",
            modifier = modifier,
        )
    }
}

@Composable
private fun CheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier,
) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    Text(text)
}

@Preview
@Composable
internal fun SuspendScanConditionsPreview() {
    Column {
        ToggleRow(
            text = "Suspend scanning while phone is idle (Doze)",
            checked = true,
            onCheckedChange = {},
        )
        ToggleRow(
            text = "Suspend scanning when connected to WiFi",
            checked = true,
            onCheckedChange = {},
        )
        ToggleRow(
            text = "Make exception for certain WiFis (requires permission)",
            checked = true,
            onCheckedChange = {},
            modifier = Modifier.padding(start = 24.dp),
        )
        CurrentWifiExceptionRow(
            ssid = "HomeNetwork",
            checked = false,
            onCheckedChange = {},
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}

@Preview
@Composable
internal fun SuspendScanConditionsDisconnectedPreview() {
    CurrentWifiExceptionRow(
        ssid = null,
        checked = false,
        onCheckedChange = {},
    )
}
