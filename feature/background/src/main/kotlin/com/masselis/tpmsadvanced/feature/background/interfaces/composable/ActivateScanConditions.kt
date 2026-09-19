package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel

@Composable
internal fun ActivateScanConditions(
    viewModel: PersistentScanningSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val cable by viewModel.activateOnCableCharging.collectAsState()
    val wireless by viewModel.activateOnWirelessCharging.collectAsState()
    val justScan by viewModel.justScan.collectAsState()
    ActivateScanConditions(
        cable = cable,
        onCable = { viewModel.activateOnCableCharging.value = it },
        wireless = wireless,
        onWireless = { viewModel.activateOnWirelessCharging.value = it },
        justScan = justScan,
        onJustScan = { viewModel.justScan.value = it },
        modifier = modifier,
    )
}

@Composable
private fun ActivateScanConditions(
    cable: Boolean,
    onCable: (Boolean) -> Unit,
    wireless: Boolean,
    onWireless: (Boolean) -> Unit,
    justScan: Boolean,
    onJustScan: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    // "Just scan" makes every other activate condition irrelevant, they are greyed out
    ToggleRow(
        text = "When charging with a cable",
        checked = cable,
        enabled = justScan.not(),
        onCheckedChange = onCable,
        modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnCable),
    )
    ToggleRow(
        text = "When charging wirelessly",
        checked = wireless,
        enabled = justScan.not(),
        onCheckedChange = onWireless,
        modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnWireless),
    )
    ToggleRow(
        text = "Just scan! (drains battery)",
        checked = justScan,
        onCheckedChange = onJustScan,
        modifier = Modifier.testTag(PersistentScanningSettingsTags.justScan),
    )
}

@Preview
@Composable
internal fun ActivateScanConditionsPreview() {
    ActivateScanConditions(
        cable = true,
        onCable = {},
        wireless = true,
        onWireless = {},
        justScan = false,
        onJustScan = {},
    )
}

@Preview
@Composable
internal fun ActivateScanConditionsJustScanPreview() {
    ActivateScanConditions(
        cable = true,
        onCable = {},
        wireless = true,
        onWireless = {},
        justScan = true,
        onJustScan = {},
    )
}
