package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.SwitchNavigationSettingsItem
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.OnLeaveEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.PersistentScanningSettingsViewModel

/**
 * The page opened from the "Activate scan conditions" item of [PersistentScanningSettings], the
 * duration of "Stay active" being picked on its own page, opened through [openStayActiveDuration].
 */
@Composable
public fun ActivateScanConditions(
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = ActivateScanConditions(
    openStayActiveDuration,
    modifier,
    viewModel { PersistentScanningSettingsViewModel() },
)

@Composable
internal fun ActivateScanConditions(
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersistentScanningSettingsViewModel = viewModel { PersistentScanningSettingsViewModel() },
) {
    SettingsOnScreenEffect()
    // Without any condition selected nothing would ever scan: leaving the page like this means
    // not wanting the conditions at all
    OnLeaveEffect(viewModel::disableActivateConditionsIfNoneSelected)
    val cable by viewModel.activateOnCableCharging.collectAsState()
    val wireless by viewModel.activateOnWirelessCharging.collectAsState()
    val androidAuto by viewModel.activateOnAndroidAuto.collectAsState()
    val stayActive by viewModel.stayActive.collectAsState()
    val stayActiveMinutes by viewModel.stayActiveMinutes.collectAsState()
    ActivateScanConditions(
        cable = cable,
        onCable = { viewModel.activateOnCableCharging.value = it },
        wireless = wireless,
        onWireless = { viewModel.activateOnWirelessCharging.value = it },
        androidAuto = androidAuto,
        onAndroidAuto = { viewModel.activateOnAndroidAuto.value = it },
        stayActive = stayActive,
        stayActiveMinutes = stayActiveMinutes,
        onStayActive = { viewModel.stayActive.value = it },
        openStayActiveDuration = openStayActiveDuration,
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@Composable
private fun ActivateScanConditions(
    cable: Boolean,
    onCable: (Boolean) -> Unit,
    wireless: Boolean,
    onWireless: (Boolean) -> Unit,
    androidAuto: Boolean,
    onAndroidAuto: (Boolean) -> Unit,
    stayActive: Boolean,
    stayActiveMinutes: Int,
    onStayActive: (Boolean) -> Unit,
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    @Suppress("MaxLineLength")
    SettingsIntro(
        "Background scanning listens to your tyre sensors whenever any of the conditions below is true, unless a suspend condition holds it back. Scanning while the app is open is never affected."
    )
    SettingsSectionHeader("Scan when")
    SettingsGroup {
        SwitchSettingsItem(
            headline = "Charging with a cable",
            checked = cable,
            onCheckedChange = onCable,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnCable),
        )
        SwitchSettingsItem(
            headline = "Charging wirelessly",
            checked = wireless,
            onCheckedChange = onWireless,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnWireless),
        )
        SwitchSettingsItem(
            headline = "Android Auto is connected",
            checked = androidAuto,
            onCheckedChange = onAndroidAuto,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.activateOnAndroidAuto),
        )
    }
    SettingsSectionHeader("Afterwards")
    SettingsGroup {
        SwitchNavigationSettingsItem(
            headline = "Stay active",
            supporting = listOf(
                "For $stayActiveMinutes min after the last condition ends",
                "For $stayActiveMinutes min",
            ).map(::AnnotatedString),
            checked = stayActive,
            // Only extends the conditions above, meaningless without any of them
            enabled = cable || wireless || androidAuto,
            onCheckedChange = onStayActive,
            onClick = openStayActiveDuration,
            modifier = Modifier.testTag(PersistentScanningSettingsTags.stayActive),
        )
    }
}

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
        stayActiveMinutes = 10,
        onStayActive = {},
        openStayActiveDuration = {},
    )
}
