package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SegmentedSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionNote
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.SPEECH
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.TONES
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
    val alertSound by viewModel.alertSound.collectAsState()
    AlertsSettings(alertSound, { viewModel.alertSound.value = it }, modifier)
}

@Suppress("MaxLineLength")
@Composable
private fun AlertsSettings(
    alertSound: AlertSound,
    onAlertSound: (AlertSound) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsSectionNote(
        when (alertSound) {
            SPEECH -> "Says the red and critical alerts out loud on top of their notifications, \"Tyre pressure\", \"Tyre hot\" or \"Sensor battery\", and repeats them while they last"
            TONES -> "Plays a tone pattern on top of the red and critical alerts' notifications, faster and higher pitched for a critical one, and repeats it while they last"
            NONE -> "Only the notifications sound"
        }
    )
    SettingsGroup {
        SegmentedSettingsItem(
            headline = "Alert sound",
            options = AlertSound.entries,
            selected = alertSound,
            onSelect = onAlertSound,
            label = {
                when (it) {
                    SPEECH -> "Speech"
                    TONES -> "Tones"
                    NONE -> "None"
                }
            },
            modifier = Modifier.testTag(AlertsSettingsTags.alertSound),
        )
    }
}

@Suppress("ConstPropertyName")
internal object AlertsSettingsTags {
    const val alertSound = "AlertsSettingsTags_alertSound"
}

@Preview
@Composable
private fun AlertsSettingsPreview() = AlertsSettings(alertSound = SPEECH, onAlertSound = {})

@Preview
@Composable
private fun AlertsSettingsTonesPreview() = AlertsSettings(alertSound = TONES, onAlertSound = {})
