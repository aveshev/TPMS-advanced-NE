package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BeaconsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.BeaconsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Mode
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Mode.BALANCED
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Mode.LOW_LATENCY
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Mode.LOW_POWER
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Mode.LOW_POWER_BATCHED
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Mode.OPPORTUNISTIC

/** The mode of the background scan looking for beacons, to compare their battery use and delay */
@Composable
public fun BeaconScanModeSettingsItem(modifier: Modifier = Modifier): Unit =
    BeaconScanModeSettingsItem(modifier, viewModel { BeaconsViewModel() })

@Composable
internal fun BeaconScanModeSettingsItem(
    modifier: Modifier = Modifier,
    viewModel: BeaconsViewModel = viewModel { BeaconsViewModel() },
) {
    val mode by viewModel.scanMode.collectAsState(initial = BeaconPresenceUseCase.DEFAULT_SCAN_MODE)
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        TextSettingsItem(
            headline = "Beacon scan mode",
            supporting = "${mode.label}, while looking for beacons in the background",
            onClick = { expanded = true },
            modifier = Modifier.testTag(BeaconScanModeSettingsItemTags.scanMode),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Mode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        viewModel.setScanMode(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == mode) Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                            contentDescription = "Selected",
                        )
                    },
                    modifier = Modifier.testTag("${BeaconScanModeSettingsItemTags.option}_${option.name}"),
                )
            }
        }
    }
}

private val Mode.label
    get() = when (this) {
        OPPORTUNISTIC -> "Opportunistic"
        LOW_POWER -> "Low power"
        LOW_POWER_BATCHED -> "Low power, batched every 30 s"
        BALANCED -> "Balanced"
        LOW_LATENCY -> "Low latency"
    }

@Suppress("ConstPropertyName")
internal object BeaconScanModeSettingsItemTags {
    const val scanMode = "BeaconScanModeSettingsItemTags_scanMode"
    const val option = "BeaconScanModeSettingsItemTags_option"
}
