package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.core.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.LocalWifiExceptionJourney
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase

/** The page opened from the "Suspend scan conditions" item of [PersistentScanningSettings] */
@Composable
public fun SuspendScanConditions(
    openExceptedWifis: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = SuspendScanConditions(openExceptedWifis, modifier, viewModel { PersistentScanningSettingsViewModel() })

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod")
@Composable
internal fun SuspendScanConditions(
    openExceptedWifis: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) {
    SettingsOnScreenEffect()
    // Without any condition selected nothing would ever suspend: same as turning them off
    OnLeaveEffect(viewModel::disableSuspendConditionsIfNoneSelected)
    val suspendInDoze by viewModel.suspendScanningInDoze.collectAsState()
    val suspendOnWifi by viewModel.suspendScanningOnWifi.collectAsState()
    val exceptionEnabled by viewModel.wifiExceptionEnabled.collectAsState()
    val exceptedSsids by viewModel.exceptedWifiSsids.collectAsState()
    val wifiExceptionJourney = LocalWifiExceptionJourney.current
    val permissionState = rememberMultiplePermissionsState(viewModel.requiredWifiPermissions())
    // Keyed on the grant flag: WifiConnectionUseCase's NetworkCallback delivers redacted data
    // (null SSID) while ungranted, and Android doesn't re-deliver capabilities just because
    // permission was newly granted — restarting the collection forces a fresh registration, which
    // reads correctly the moment the permission is actually held.
    // null until the new registration reports, so that a stale answer isn't shown meanwhile
    val wifiState by produceState<WifiConnectionUseCase.State?>(
        initialValue = null,
        key1 = permissionState.allPermissionsGranted,
    ) {
        value = null
        viewModel.wifiConnectionState.collect { value = it }
    }

    Column(modifier) {
        @Suppress("MaxLineLength")
        SettingsIntro(
            "Background scanning is suspended whenever any of the conditions below is true, even when an activate condition asks for it, so that no battery is spent while the vehicle is unlikely to be ridden. Scanning while the app is open is never affected."
        )
        SettingsSectionHeader("Suspend scanning when")
        SettingsGroup {
            SwitchSettingsItem(
                headline = "The phone is idle for a while",
                supporting = "Screen off, not charging, no movement (Deep Doze)",
                checked = suspendInDoze,
                onCheckedChange = { viewModel.suspendScanningInDoze.value = it },
                modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendInDoze),
            )
            SwitchSettingsItem(
                headline = "Connected to any WiFi",
                checked = suspendOnWifi,
                onCheckedChange = { viewModel.suspendScanningOnWifi.value = it },
                modifier = Modifier.testTag(PersistentScanningSettingsTags.suspendOnWifi),
            )
            SwitchNavigationSettingsItem(
                headline = "...except some WiFis",
                supporting = listOf(AnnotatedString("Car hotspot, garage, etc.")),
                checked = exceptionEnabled,
                enabled = suspendOnWifi,
                onClick = openExceptedWifis,
                // Only turns on once the permission is held, so that what depends on it appears then
                onCheckedChange = { enabled ->
                    if (enabled) wifiExceptionJourney.request { viewModel.wifiExceptionEnabled.value = true }
                    else viewModel.wifiExceptionEnabled.value = false
                },
                modifier = Modifier.testTag(PersistentScanningSettingsTags.wifiExceptionEnabled),
            )
            // Always shown, only usable once the exception is on and the connected WiFi is known
            val granted = permissionState.allPermissionsGranted
            val ssid = (wifiState as? WifiConnectionUseCase.State.Connected)?.ssid?.takeIf { granted }
            SwitchSettingsItem(
                headline = "Keep scanning on current WiFi:",
                supporting = currentWifiSummary(wifiState, granted, suspendOnWifi && exceptionEnabled),
                checked = ssid != null && ssid in exceptedSsids,
                enabled = suspendOnWifi && exceptionEnabled && ssid != null,
                onCheckedChange = { ssid?.let(viewModel::toggleExceptedSsid) },
                modifier = Modifier.testTag(PersistentScanningSettingsTags.currentWifiException),
            )
        }
    }
}

private fun currentWifiSummary(
    wifiState: WifiConnectionUseCase.State?,
    granted: Boolean,
    exceptionInUse: Boolean,
): String = when {
    exceptionInUse && granted.not() -> "Location permission \"Allow all the time\" required"
    wifiState == WifiConnectionUseCase.State.Disconnected -> "Not connected to a WiFi"
    // Still asking the system, or not allowed to read the name
    wifiState !is WifiConnectionUseCase.State.Connected || granted.not() -> "The current WiFi"
    // Connected, but the system doesn't tell the name: permission granted, yet the Location
    // switch of the phone is off
    wifiState.ssid == null -> "Name unreadable, is Location turned on?"
    else -> "${wifiState.ssid} (connected)"
}

@Preview
@Composable
internal fun CurrentWifiExceptionItemPreview() {
    SettingsGroup {
        SwitchSettingsItem(
            headline = "Keep scanning on current WiFi:",
            supporting = currentWifiSummary(WifiConnectionUseCase.State.Connected("HomeNetwork"), true, true),
            checked = false,
            onCheckedChange = {},
        )
        SwitchSettingsItem(
            headline = "Keep scanning on current WiFi:",
            supporting = currentWifiSummary(WifiConnectionUseCase.State.Disconnected, true, true),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
    }
}
