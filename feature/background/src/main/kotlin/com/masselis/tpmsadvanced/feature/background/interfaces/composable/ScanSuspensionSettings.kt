package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import android.content.Intent
import android.content.Intent.CATEGORY_DEFAULT
import android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.ScanSuspensionSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.ScanSuspensionSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase

@Composable
public fun ScanSuspensionSettings(modifier: Modifier = Modifier): Unit =
    ScanSuspensionSettings(
        modifier,
        viewModel { ScanSuspensionSettingsViewModel() },
    )

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod")
@Composable
internal fun ScanSuspensionSettings(
    modifier: Modifier = Modifier,
    viewModel: ScanSuspensionSettingsViewModel = viewModel { ScanSuspensionSettingsViewModel() },
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
    var showLocationPermissionAlert by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    // Requesting via our own launcher (rather than permissionState.launchMultiplePermissionRequest())
    // gives us a completion callback, so refusal — including a silent one, when the permission was
    // already permanently denied — reliably surfaces the rationale popup below.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.all { it }.not()) showLocationPermissionAlert = true }

    Column(modifier) {
        ToggleRow(
            text = "Suspend scanning while phone is idle (Doze)",
            checked = suspendInDoze,
            onCheckedChange = { viewModel.suspendScanningInDoze.value = it },
            modifier = Modifier.testTag(ScanSuspensionSettingsTags.suspendInDoze),
        )
        ToggleRow(
            text = "Suspend scanning when connected to WiFi",
            checked = suspendOnWifi,
            onCheckedChange = { viewModel.suspendScanningOnWifi.value = it },
            modifier = Modifier.testTag(ScanSuspensionSettingsTags.suspendOnWifi),
        )
        ToggleRow(
            text = "Make exception for certain WiFis (requires permission)",
            checked = exceptionEnabled,
            enabled = suspendOnWifi,
            onCheckedChange = { viewModel.wifiExceptionEnabled.value = it },
            modifier = Modifier
                .padding(start = 24.dp)
                .testTag(ScanSuspensionSettingsTags.wifiExceptionEnabled),
        )
        if (suspendOnWifi && exceptionEnabled) {
            if (permissionState.allPermissionsGranted.not()) {
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        permissionState.permissions.map { it.permission }.toTypedArray()
                    )
                }
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
    if (showLocationPermissionAlert) {
        LocationPermissionAlert(
            onDismissRequest = { showLocationPermissionAlert = false },
            onConfirm = {
                showLocationPermissionAlert = false
                Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                    .apply { addCategory(CATEGORY_DEFAULT) }
                    .apply { data = "package:${activity!!.packageName}".toUri() }
                    .apply { addFlags(FLAG_ACTIVITY_NEW_TASK) }
                    .apply { addFlags(FLAG_ACTIVITY_NO_HISTORY) }
                    .apply { addFlags(FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) }
                    .also { activity!!.startActivity(it) }
            }
        )
    }
}

@Composable
private fun LocationPermissionAlert(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                text = "Identifying which WiFi network you're connected to — so it can be" +
                        " excepted from the WiFi suspend setting — needs the \"Precise" +
                        " location\" permission. Please enable it to use this feature."
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Open settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Cancel")
            }
        }
    )
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
            modifier = modifier.testTag(ScanSuspensionSettingsTags.currentWifiException),
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

@Composable
private fun ToggleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
) {
    Text(text, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
}

@Preview
@Composable
internal fun ScanSuspensionSettingsPreview() {
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
internal fun ScanSuspensionSettingsDisconnectedPreview() {
    CurrentWifiExceptionRow(
        ssid = null,
        checked = false,
        onCheckedChange = {},
    )
}

@Suppress("ConstPropertyName")
internal object ScanSuspensionSettingsTags {
    const val suspendInDoze = "ScanSuspensionSettingsTags_suspendInDoze"
    const val suspendOnWifi = "ScanSuspensionSettingsTags_suspendOnWifi"
    const val wifiExceptionEnabled = "ScanSuspensionSettingsTags_wifiExceptionEnabled"
    const val currentWifiException = "ScanSuspensionSettingsTags_currentWifiException"
}
