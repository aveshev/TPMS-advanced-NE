package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.SwitchSettingsItem
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.rememberPermissionJourney
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.DetectedActivitiesViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings

/**
 * Shows, on the home screen, the activities the phone detects (in a vehicle, walking...) with their
 * confidence. A debugging aid, to see how reliable they are before scanning depends on them.
 */
@Composable
public fun DetectedActivitiesSettingsItem(modifier: Modifier = Modifier): Unit =
    DetectedActivitiesSettingsItem(
        modifier,
        viewModel { Bindings.featureBackgroundInternal.detectedActivitiesViewModel() },
    )

@Composable
internal fun DetectedActivitiesSettingsItem(
    modifier: Modifier = Modifier,
    viewModel: DetectedActivitiesViewModel = viewModel {
        Bindings.featureBackgroundInternal.detectedActivitiesViewModel()
    },
) {
    val shown by viewModel.showDetectedActivities.collectAsState()
    val journey = rememberPermissionJourney(
        permissions = viewModel.requiredPermissions(),
        systemPopupFirst = true,
        rationale = "Detecting what the phone is doing needs the physical activity permission." +
                " In the app's settings, open Permissions, then allow Physical activity.",
        disabledInfo = "The detected activities are not shown, because the physical activity" +
                " permission was not granted.",
        isSettingsOnScreen = { true },
        onDenied = { viewModel.showDetectedActivities.value = false },
    )
    SwitchSettingsItem(
        headline = "Top detected activities",
        supporting = "Shown on the home screen with their confidence",
        checked = shown,
        // Only turns on once the permission is held
        onCheckedChange = { enabled ->
            if (enabled) journey.request { viewModel.showDetectedActivities.value = true }
            else viewModel.showDetectedActivities.value = false
        },
        modifier = modifier.testTag(DetectedActivitiesSettingsItemTags.showDetectedActivities),
    )
}

@Suppress("ConstPropertyName")
internal object DetectedActivitiesSettingsItemTags {
    const val showDetectedActivities = "DetectedActivitiesSettingsItemTags_showDetectedActivities"
}
