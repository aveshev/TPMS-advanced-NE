package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DebugSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.DebugSettingsViewModel

/**
 * The "Developer options" group of the app settings: the demo mode, and the switch of the debug
 * options, whose page [openDebug] opens.
 */
@Composable
public fun DeveloperOptionsSettings(
    openDebug: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = DeveloperOptionsSettings(openDebug, modifier, viewModel { DebugSettingsViewModel() })

@Composable
internal fun DeveloperOptionsSettings(
    openDebug: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebugSettingsViewModel = viewModel { DebugSettingsViewModel() },
) {
    val debugOptions by viewModel.debugOptions.collectAsState()
    val sensorId by viewModel.showSensorId.collectAsState()
    val sensorFlags by viewModel.showSensorFlags.collectAsState()
    val activities by viewModel.showDetectedActivities.collectAsState()
    val selected = listOfNotNull(
        // Same order as their page
        "Sensor ID".takeIf { sensorId },
        "Sensor flags".takeIf { sensorFlags },
        "Detected activities".takeIf { activities },
    )
    SettingsGroup(modifier) {
        DemoModeSettingsItem()
        SwitchNavigationSettingsItem(
            headline = "Debug",
            supporting = selected
                .takeIf { debugOptions && it.isNotEmpty() }
                ?.let { listOf(it.joinToString(", "), "${it.first()} and more") }
                .orEmpty()
                .map(::AnnotatedString),
            // Leaving the page with nothing selected turns the switch off, which only happens once
            // the page is gone: shown that way from the start rather than as on with nothing to show
            checked = debugOptions && selected.isNotEmpty(),
            // Turned on without anything to show, the only sensible next step is to pick something.
            // Leaving the page without doing so turns it off again.
            onCheckedChange = { enabled ->
                viewModel.debugOptions.value = enabled
                if (enabled && selected.isEmpty()) openDebug()
            },
            onClick = openDebug,
            modifier = Modifier.testTag(DeveloperOptionsSettingsTags.debug),
        )
    }
}

@Preview
@Composable
internal fun DeveloperOptionsSettingsPreview() {
    SettingsGroup {
        DemoModeSettingsItem(demoMode = false, onDemoMode = {})
        SwitchNavigationSettingsItem(
            headline = "Debug",
            supporting = listOf(AnnotatedString("Sensor ID, Sensor flags")),
            checked = true,
            onCheckedChange = {},
            onClick = {},
        )
    }
}

@Suppress("ConstPropertyName")
internal object DeveloperOptionsSettingsTags {
    const val debug = "DeveloperOptionsSettingsTags_debug"
}
