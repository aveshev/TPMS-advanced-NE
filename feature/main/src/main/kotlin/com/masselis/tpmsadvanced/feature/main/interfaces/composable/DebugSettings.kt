package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DebugSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.DebugSettingsViewModel

/**
 * The page opened from the "Debug" item of [DeveloperOptionsSettings]: what the sensors and the
 * phone report, shown on the home screen. [additionalItems] are appended to the group, for debug
 * options owned by other features.
 */
@Composable
public fun DebugSettings(
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
): Unit = DebugSettings(modifier, additionalItems, viewModel { DebugSettingsViewModel() })

@Composable
internal fun DebugSettings(
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
    viewModel: DebugSettingsViewModel = viewModel { DebugSettingsViewModel() },
) {
    // With nothing selected there is nothing to show: same as turning the switch off
    OnLeaveEffect(viewModel::disableIfNoneSelected)
    val showSensorId by viewModel.showSensorId.collectAsState()
    val showSensorFlags by viewModel.showSensorFlags.collectAsState()
    DebugSettings(
        showSensorId = showSensorId,
        onShowSensorId = { viewModel.showSensorId.value = it },
        showSensorFlags = showSensorFlags,
        onShowSensorFlags = { viewModel.showSensorFlags.value = it },
        modifier = modifier,
        additionalItems = additionalItems,
    )
}

@Composable
private fun DebugSettings(
    showSensorId: Boolean,
    onShowSensorId: (Boolean) -> Unit,
    showSensorFlags: Boolean,
    onShowSensorFlags: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "What the sensors and the phone report, to help investigate them. Turning Debug off in the app settings hides all of it, and keeps what is selected here."
    )
    SettingsGroup(Modifier.padding(top = 24.dp)) {
        SwitchSettingsItem(
            headline = "Sensor ID",
            checked = showSensorId,
            onCheckedChange = onShowSensorId,
            modifier = Modifier.testTag(DebugSettingsTags.showSensorId),
        )
        SwitchSettingsItem(
            headline = "Sensor flags",
            supporting = "Bits 0 to 7 of the last packet's status byte, the unset ones greyed out",
            checked = showSensorFlags,
            onCheckedChange = onShowSensorFlags,
            modifier = Modifier.testTag(DebugSettingsTags.showSensorFlags),
        )
        additionalItems()
    }
}

@Preview
@Composable
internal fun DebugSettingsPreview() {
    DebugSettings(
        showSensorId = true,
        onShowSensorId = {},
        showSensorFlags = false,
        onShowSensorFlags = {},
    )
}

@Suppress("ConstPropertyName")
internal object DebugSettingsTags {
    const val showSensorId = "DebugSettingsTags_showSensorId"
    const val showSensorFlags = "DebugSettingsTags_showSensorFlags"
}
