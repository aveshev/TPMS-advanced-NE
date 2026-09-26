package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DemoModeSwitchViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DemoModeSwitchViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.DemoModeSwitchViewModel

/** The "Demo" group of the app settings, switching between made-up sensors and the real ones */
@Composable
public fun DemoModeSettings(
    modifier: Modifier = Modifier,
): Unit = DemoModeSettings(modifier, viewModel { DemoModeSwitchViewModel() })

@Composable
internal fun DemoModeSettings(
    modifier: Modifier = Modifier,
    viewModel: DemoModeSwitchViewModel = viewModel { DemoModeSwitchViewModel() },
) {
    val state by viewModel.stateFlow.collectAsState()
    DemoModeSettings(
        demoMode = state == State.Enabled,
        onDemoMode = { if (it) viewModel.enable() else viewModel.disable() },
        modifier = modifier,
    )
}

@Composable
private fun DemoModeSettings(
    demoMode: Boolean,
    onDemoMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsGroup(modifier) {
    SwitchSettingsItem(
        headline = "Demo mode",
        checked = demoMode,
        onCheckedChange = onDemoMode,
        supporting = "Changing restarts the app",
        modifier = Modifier.testTag(DemoModeSettingsTags.demoMode),
    )
}

@Preview
@Composable
internal fun DemoModeSettingsPreview() {
    DemoModeSettings(demoMode = false, onDemoMode = {})
}

@Suppress("ConstPropertyName")
internal object DemoModeSettingsTags {
    const val demoMode = "DemoModeSettingsTags_demoMode"
}
