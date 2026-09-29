package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.DetectedActivitiesViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.IN_VEHICLE
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.ON_BICYCLE
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.ON_FOOT
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.RUNNING
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.STILL
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.TILTING
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.UNKNOWN
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.WALKING

/**
 * The activities the phone is the most confident it is doing, stacked so that they fit in the
 * corner of the top bar. Nothing is shown unless the user asked for it in the debug settings.
 */
@Composable
public fun DetectedActivitiesIndicator(modifier: Modifier = Modifier): Unit =
    DetectedActivitiesIndicator(
        viewModel { Bindings.featureBackgroundInternal.detectedActivitiesViewModel() },
        modifier,
    )

@Composable
internal fun DetectedActivitiesIndicator(
    viewModel: DetectedActivitiesViewModel,
    modifier: Modifier = Modifier,
) {
    // Lifecycle aware: the activity updates cost battery, they stop when the app is not visible
    val activities by viewModel.topActivities.collectAsStateWithLifecycle(initialValue = null)
    activities?.let { DetectedActivitiesIndicator(it, modifier) }
}

@Composable
private fun DetectedActivitiesIndicator(activities: List<Activity>, modifier: Modifier = Modifier) =
    Column(
        modifier = modifier
            .padding(start = 16.dp)
            .testTag(DetectedActivitiesIndicatorTags.root),
    ) {
        // Waiting for the first sample
        if (activities.isEmpty()) Text(text = "--%", fontSize = 11.sp, lineHeight = 14.sp)
        activities.forEach { activity ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ImageVector.vectorResource(
                        when (activity.type) {
                            IN_VEHICLE -> R.drawable.activity_in_vehicle
                            ON_BICYCLE -> R.drawable.activity_on_bicycle
                            ON_FOOT -> R.drawable.activity_on_foot
                            WALKING -> R.drawable.activity_walking
                            RUNNING -> R.drawable.activity_running
                            STILL -> R.drawable.activity_still
                            TILTING -> R.drawable.activity_tilting
                            UNKNOWN -> R.drawable.activity_unknown
                        }
                    ),
                    contentDescription = activity.type.name.lowercase().replace('_', ' '),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "${activity.confidence}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }

@Preview
@Composable
internal fun DetectedActivitiesIndicatorPreview() {
    Row {
        DetectedActivitiesIndicator(emptyList())
        DetectedActivitiesIndicator(
            listOf(Activity(IN_VEHICLE, 40), Activity(ON_FOOT, 15), Activity(UNKNOWN, 10))
        )
        DetectedActivitiesIndicator(
            listOf(Activity(WALKING, 71), Activity(RUNNING, 20), Activity(STILL, 5))
        )
        DetectedActivitiesIndicator(
            listOf(Activity(ON_BICYCLE, 100), Activity(TILTING, 100))
        )
    }
}

@Suppress("ConstPropertyName")
internal object DetectedActivitiesIndicatorTags {
    const val root = "DetectedActivitiesIndicatorTags_root"
}
