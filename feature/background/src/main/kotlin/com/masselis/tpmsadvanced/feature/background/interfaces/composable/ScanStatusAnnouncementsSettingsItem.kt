package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.ScanStatusAnnouncementsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/**
 * Speaks the status of persistent scanning each time it changes, to hear when the phone switches
 * modes in a pocket. Quiet while the main screen is shown.
 */
@Composable
public fun ScanStatusAnnouncementsSettingsItem(modifier: Modifier = Modifier): Unit =
    ScanStatusAnnouncementsSettingsItem(
        modifier,
        viewModel { Bindings.featureBackgroundInternal.scanStatusAnnouncementsViewModel() },
    )

@Composable
internal fun ScanStatusAnnouncementsSettingsItem(
    modifier: Modifier = Modifier,
    viewModel: ScanStatusAnnouncementsViewModel = viewModel {
        Bindings.featureBackgroundInternal.scanStatusAnnouncementsViewModel()
    },
) {
    val checked by viewModel.announceScanStatus.collectAsState()
    SwitchSettingsItem(
        headline = "Status announcements",
        supporting = "Speaks the persistent scanning status when it changes, unless the main screen is shown",
        checked = checked,
        onCheckedChange = { viewModel.announceScanStatus.value = it },
        modifier = modifier.testTag(ScanStatusAnnouncementsSettingsItemTags.announceScanStatus),
    )
}

@Suppress("ConstPropertyName")
internal object ScanStatusAnnouncementsSettingsItemTags {
    const val announceScanStatus = "ScanStatusAnnouncementsSettingsItemTags_announceScanStatus"
}
