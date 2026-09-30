package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SegmentedSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.PressureLossSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.PressureLossSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.VehiclePressureLossUseCase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The page of the experimental pressure loss warning, opened from [DeveloperOptionsSettings]. It
 * applies to every vehicle, each against its own low pressure alert.
 */
@Composable
public fun PressureLossSettings(
    modifier: Modifier = Modifier,
): Unit = PressureLossSettings(modifier, viewModel { PressureLossSettingsViewModel() })

@Composable
internal fun PressureLossSettings(
    modifier: Modifier = Modifier,
    viewModel: PressureLossSettingsViewModel = viewModel { PressureLossSettingsViewModel() },
) {
    val enabled by viewModel.enabled.collectAsState()
    val hours by viewModel.hours.collectAsState()
    val minDrop by viewModel.minDrop.collectAsState()
    val alwaysShow by viewModel.alwaysShow.collectAsState()
    PressureLossSettings(
        enabled = enabled,
        horizon = hours.hours,
        minDrop = minDrop,
        alwaysShow = alwaysShow,
        onEnabled = { viewModel.enabled.value = it },
        onHorizon = { viewModel.hours.value = it.inWholeHours.toInt() },
        onMinDrop = { viewModel.minDrop.value = it },
        onAlwaysShow = { viewModel.alwaysShow.value = it },
        modifier = modifier,
    )
}

@Suppress("LongMethod", "LongParameterList", "MaxLineLength")
@Composable
private fun PressureLossSettings(
    enabled: Boolean,
    horizon: Duration,
    minDrop: Float,
    alwaysShow: Boolean,
    onEnabled: (Boolean) -> Unit,
    onHorizon: (Duration) -> Unit,
    onMinDrop: (Float) -> Unit,
    onAlwaysShow: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(
        "Experimental. Warns about a leak before the low pressure alert: when a tyre loses pressure fast enough to fall to two thirds of its low pressure alert within the chosen time. A drop counts once it reaches the chosen share of the low pressure alert, 7 kPa (1 psi) at least.\n\nThe pressures are compared at the same temperature, so a tyre cooling down once parked doesn't count as a loss. Letting air out shows as a loss too."
    )
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = "Pressure loss warning",
            checked = enabled,
            onCheckedChange = onEnabled,
            modifier = Modifier.testTag(PressureLossSettingsTags.enabled),
        )
    }
    // Greyed out rather than hidden while the warning is off, like Android's settings do
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SegmentedSettingsItem(
            headline = "Warn if at ⅔ of the low alert within",
            options = VehiclePressureLossUseCase.HORIZONS,
            selected = horizon,
            onSelect = onHorizon,
            label = { "${it.inWholeHours} h" },
        )
        SegmentedSettingsItem(
            headline = "Minimum drop, of the low alert",
            options = VehiclePressureLossUseCase.MIN_DROPS,
            selected = minDrop,
            onSelect = onMinDrop,
            // 7.5 %, not 7.50 %
            label = { "${(it * 100).toBigDecimal().stripTrailingZeros().toPlainString()} %" },
        )
        SwitchSettingsItem(
            headline = "Always show leak rate",
            supporting = "(even when not above threshold)",
            checked = alwaysShow,
            onCheckedChange = onAlwaysShow,
            enabled = enabled,
            modifier = Modifier.testTag(PressureLossSettingsTags.alwaysShow),
        )
    }
}

@Suppress("ConstPropertyName")
internal object PressureLossSettingsTags {
    const val enabled = "PressureLossSettingsTags_enabled"
    const val alwaysShow = "PressureLossSettingsTags_alwaysShow"
}

@Preview
@Composable
internal fun PressureLossSettingsPreview() {
    PressureLossSettings(
        enabled = true,
        horizon = 10.hours,
        minDrop = 0.075f,
        alwaysShow = false,
        onEnabled = {},
        onHorizon = {},
        onMinDrop = {},
        onAlwaysShow = {},
    )
}

@Preview
@Composable
internal fun PressureLossSettingsOffPreview() {
    PressureLossSettings(
        enabled = false,
        horizon = 2.hours,
        minDrop = 0.1f,
        alwaysShow = true,
        onEnabled = {},
        onHorizon = {},
        onMinDrop = {},
        onAlwaysShow = {},
    )
}
