package com.masselis.tpmsadvanced.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.BuildConfig
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.ActivateScanConditions
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.ExceptedWifis
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.PersistentScanningDetails
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.PersistentScanningSettings
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.StayActiveDuration
import com.masselis.tpmsadvanced.feature.background.interfaces.composable.SuspendScanConditions
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.DemoModeSettings
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.TimeSinceUpdateDetails
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.TyreDisplaySettings
import com.masselis.tpmsadvanced.feature.unit.interfaces.UnitsSettings
import com.masselis.tpmsadvanced.interfaces.composable.AppSettingsTag.persistentScanning
import com.masselis.tpmsadvanced.interfaces.composable.AppSettingsTag.tyreDisplay

@Composable
internal fun AppSettings(
    openTimeSinceUpdate: () -> Unit,
    openPersistentScanning: () -> Unit,
    openActivateScanConditions: () -> Unit,
    openSuspendScanConditions: () -> Unit,
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    SettingsSectionHeader("Display")
    TyreDisplaySettings(
        openTimeSinceUpdate = openTimeSinceUpdate,
        modifier = Modifier.testTag(tyreDisplay),
    )
    UnitsSettings(Modifier.padding(top = 16.dp))
    SettingsSectionHeader("Background scanning")
    PersistentScanningSettings(
        openPersistentScanning = openPersistentScanning,
        openActivateConditions = openActivateScanConditions,
        openSuspendConditions = openSuspendScanConditions,
        modifier = Modifier.testTag(persistentScanning),
    )
    SettingsSectionHeader("Demo")
    DemoModeSettings()
    SettingsSectionHeader("About")
    SettingsGroup {
        TextSettingsItem(headline = "Version", supporting = BuildConfig.VERSION_NAME)
    }
}

@Composable
internal fun TimeSinceUpdateSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    TimeSinceUpdateDetails()
}

@Composable
internal fun PersistentScanningDetailsSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    PersistentScanningDetails()
}

@Composable
internal fun ActivateScanConditionsSettings(
    openStayActiveDuration: () -> Unit,
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    ActivateScanConditions(openStayActiveDuration)
}

@Composable
internal fun StayActiveDurationSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    StayActiveDuration()
}

@Composable
internal fun SuspendScanConditionsSettings(
    openExceptedWifis: () -> Unit,
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    SuspendScanConditions(openExceptedWifis)
}

@Composable
internal fun ExceptedWifisSettings(
    modifier: Modifier = Modifier
) = SettingsPage(modifier) {
    ExceptedWifis()
}

@Composable
internal fun SettingsPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    content = content,
)

internal object AppSettingsTag {
    const val tyreDisplay = "AppSettingsTag_tyreDisplay"
    const val persistentScanning = "AppSettingsTag_persistentScanning"
}
