package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel

/**
 * The "Display" group of the app settings, [openTimeSinceUpdate] opening the page explaining it.
 * [additionalItems] are appended to the group, for display settings owned by other features.
 */
@Composable
public fun TyreDisplaySettings(
    openTimeSinceUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
): Unit =
    TyreDisplaySettings(
        openTimeSinceUpdate,
        modifier,
        additionalItems,
        viewModel { TyreDisplaySettingsViewModel() },
    )

@Composable
internal fun TyreDisplaySettings(
    openTimeSinceUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
    viewModel: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
) {
    val showSensorId by viewModel.showSensorId.collectAsState()
    val showTimeSinceUpdate by viewModel.showTimeSinceUpdate.collectAsState()
    TyreDisplaySettings(
        showSensorId = showSensorId,
        onShowSensorId = { viewModel.showSensorId.value = it },
        showTimeSinceUpdate = showTimeSinceUpdate,
        onShowTimeSinceUpdate = { viewModel.showTimeSinceUpdate.value = it },
        openTimeSinceUpdate = openTimeSinceUpdate,
        modifier = modifier,
        additionalItems = additionalItems,
    )
}

@Composable
private fun TyreDisplaySettings(
    showSensorId: Boolean,
    onShowSensorId: (Boolean) -> Unit,
    showTimeSinceUpdate: Boolean,
    onShowTimeSinceUpdate: (Boolean) -> Unit,
    openTimeSinceUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: @Composable ColumnScope.() -> Unit = {},
) = SettingsGroup(modifier) {
    SwitchNavigationSettingsItem(
        headline = "Time since last update",
        checked = showTimeSinceUpdate,
        onCheckedChange = onShowTimeSinceUpdate,
        onClick = openTimeSinceUpdate,
        // The page explains why a sensor can stay silent, which matters whether shown or not
        openableWhenOff = true,
        modifier = Modifier.testTag(TyreDisplaySettingsTags.showTimeSinceUpdate),
    )
    SwitchSettingsItem(
        headline = "Sensor ID",
        checked = showSensorId,
        onCheckedChange = onShowSensorId,
        modifier = Modifier.testTag(TyreDisplaySettingsTags.showSensorId),
    )
    additionalItems()
}

@Preview
@Composable
internal fun TyreDisplaySettingsPreview() {
    TyreDisplaySettings(
        showSensorId = true,
        onShowSensorId = {},
        showTimeSinceUpdate = true,
        onShowTimeSinceUpdate = {},
        openTimeSinceUpdate = {},
    )
}

@Suppress("ConstPropertyName")
internal object TyreDisplaySettingsTags {
    const val showSensorId = "TyreDisplaySettingsTags_showSensorId"
    const val showTimeSinceUpdate = "TyreDisplaySettingsTags_showTimeSinceUpdate"
    const val timeSinceUpdateDetails = "TyreDisplaySettingsTags_timeSinceUpdateDetails"
}
