package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.AlertsSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/** The settings of the tyres' alerts, see docs/alerts.md */
@Composable
public fun AlertsSettings(modifier: Modifier = Modifier): Unit = AlertsSettings(
    modifier,
    viewModel { Bindings.featureBackgroundInternal.alertsSettingsViewModel() },
)

@Composable
internal fun AlertsSettings(
    modifier: Modifier = Modifier,
    viewModel: AlertsSettingsViewModel,
) {
    val spokenAlerts by viewModel.spokenAlerts.collectAsState()
    AlertsSettings(spokenAlerts, { viewModel.spokenAlerts.value = it }, modifier)
}

@Suppress("MaxLineLength")
@Composable
private fun AlertsSettings(
    spokenAlerts: Boolean,
    onSpokenAlerts: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsGroup(modifier) {
    SwitchSettingsItem(
        headline = "Spoken alerts",
        supporting = "Says the red and critical alerts out loud, on top of their notifications: \"Tyre pressure\", \"Tyre hot\" or \"Sensor battery\"",
        checked = spokenAlerts,
        onCheckedChange = onSpokenAlerts,
        modifier = Modifier.testTag(AlertsSettingsTags.spokenAlerts),
    )
}

@Suppress("ConstPropertyName")
internal object AlertsSettingsTags {
    const val spokenAlerts = "AlertsSettingsTags_spokenAlerts"
}

@Preview
@Composable
private fun AlertsSettingsPreview() = AlertsSettings(spokenAlerts = true, onSpokenAlerts = {})
