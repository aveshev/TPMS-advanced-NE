package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
internal fun ActivateScanConditions(
    viewModel: PersistentScanningSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val cable by viewModel.activateOnCableCharging.collectAsState()
    val wireless by viewModel.activateOnWirelessCharging.collectAsState()
    val androidAuto by viewModel.activateOnAndroidAuto.collectAsState()
    val stayActive by viewModel.stayActive.collectAsState()
    val justScan by viewModel.justScan.collectAsState()
    ActivateScanConditions(
        cable = cable,
        onCable = { viewModel.activateOnCableCharging.value = it },
        wireless = wireless,
        onWireless = { viewModel.activateOnWirelessCharging.value = it },
        androidAuto = androidAuto,
        onAndroidAuto = { viewModel.activateOnAndroidAuto.value = it },
        stayActive = stayActive,
        onStayActive = { viewModel.stayActive.value = it },
        stayActiveDetails = {
            MinutesField(
                value = viewModel.stayActiveMinutes,
                enabled = justScan.not(),
                modifier = Modifier
                    .padding(start = 24.dp)
                    .testTag(PersistentScanningSettingsTags.stayActiveMinutes),
            )
        },
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
    androidAuto: Boolean,
    onAndroidAuto: (Boolean) -> Unit,
    stayActive: Boolean,
    onStayActive: (Boolean) -> Unit,
    stayActiveDetails: @Composable () -> Unit,
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
        text = "When Android Auto is connected",
        checked = androidAuto,
        enabled = justScan.not(),
        onCheckedChange = onAndroidAuto,
        modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnAndroidAuto),
    )
    ToggleRow(
        text = "Stay active",
        checked = stayActive,
        enabled = justScan.not(),
        onCheckedChange = onStayActive,
        modifier = Modifier.testTag(PersistentScanningSettingsTags.stayActive),
    )
    if (stayActive) stayActiveDetails()
    ToggleRow(
        text = "Just scan! (drains battery)",
        checked = justScan,
        onCheckedChange = onJustScan,
        modifier = Modifier.testTag(PersistentScanningSettingsTags.justScan),
    )
}

/** How long to keep scanning once every activate condition above has ended */
@Composable
private fun MinutesField(
    value: MutableStateFlow<Int>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.value.toString()) }
    val minutes = text.toIntOrNull()?.takeIf { it in MINUTES_RANGE }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            // Only a valid value is saved, the field keeps showing what is being typed
            text = input.filter(Char::isDigit).take(MAX_DIGITS)
            text.toIntOrNull()?.takeIf { it in MINUTES_RANGE }?.let { value.value = it }
        },
        label = { Text("Minutes") },
        supportingText = { Text("Keep scanning this long after the last condition above ends") },
        singleLine = true,
        isError = minutes == null,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private const val MIN_MINUTES = 1
private const val MAX_MINUTES = 1440
private const val MAX_DIGITS = 4
private val MINUTES_RANGE = MIN_MINUTES..MAX_MINUTES

@Suppress("MagicNumber")
@Preview
@Composable
internal fun ActivateScanConditionsPreview() {
    ActivateScanConditions(
        cable = true,
        onCable = {},
        wireless = true,
        onWireless = {},
        androidAuto = true,
        onAndroidAuto = {},
        stayActive = true,
        onStayActive = {},
        stayActiveDetails = { MinutesField(MutableStateFlow(10), enabled = true) },
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
        androidAuto = true,
        onAndroidAuto = {},
        stayActive = true,
        onStayActive = {},
        stayActiveDetails = {},
        justScan = true,
        onJustScan = {},
    )
}
