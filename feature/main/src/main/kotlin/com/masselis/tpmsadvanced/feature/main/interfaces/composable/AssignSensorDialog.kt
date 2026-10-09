package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R

private val ITEM_SHAPE = RoundedCornerShape(12.dp)
private const val HIGHLIGHT_ALPHA = .12f

/**
 * The ways to assign a sensor to [location], each acting when tapped: the [detected] sensor first
 * when one advertises this location, then by its QR code or by scanning the sensors around
 */
@Suppress("LongMethod")
@Composable
internal fun AssignSensorDialog(
    location: Location,
    detected: Sensor?,
    scanQrCode: () -> Unit,
    scanBluetooth: () -> Unit,
    assignDetected: (Sensor) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(buildString { append("Assign a sensor to the "); appendLoc(location) }) },
        text = {
            Column {
                detected?.also { sensor ->
                    // The recommended way, under a light tint of the primary color
                    ListItem(
                        headlineContent = { Text("Assign the detected sensor") },
                        supportingContent = {
                            Text(
                                buildString {
                                    append("Sysgration, ")
                                    appendLoc(location, withType = false)
                                    append(", ")
                                    append(sensor.id.asSensorId())
                                }
                            )
                        },
                        leadingContent = { SysgrationBadge() },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = HIGHLIGHT_ALPHA)
                        ),
                        modifier = Modifier
                            .clip(ITEM_SHAPE)
                            .clickable { assignDetected(sensor) }
                            .testTag(AssignSensorDialogTags.detected),
                    )
                }
                ListItem(
                    headlineContent = { Text("Scan QR code") },
                    supportingContent = { Text("Printed on the sensors' box, card or cap") },
                    leadingContent = { Icon(ImageVector.vectorResource(R.drawable.qr_code_24px), null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clip(ITEM_SHAPE)
                        .clickable(onClick = scanQrCode)
                        .testTag(AssignSensorDialogTags.qrCode),
                )
                ListItem(
                    headlineContent = { Text("Scan via Bluetooth") },
                    supportingContent = { Text("Use pressure changes to detect the right sensor") },
                    leadingContent = { Icon(ImageVector.vectorResource(R.drawable.bluetooth_24px), null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clip(ITEM_SHAPE)
                        .clickable(onClick = scanBluetooth)
                        .testTag(AssignSensorDialogTags.bluetooth),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        },
        modifier = modifier.testTag(AssignSensorDialogTags.root),
    )
}

/** Stands for Sysgration, the only brand advertising its location, without being its logo */
@Composable
private fun SysgrationBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    ) {
        Text(
            text = "S",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Preview
@Composable
internal fun AssignSensorDialogPreview() {
    AssignSensorDialog(Location.Wheel(FRONT_LEFT), null, {}, {}, {}, {})
}

@Preview
@Composable
internal fun AssignSensorDialogDetectedPreview() {
    AssignSensorDialog(
        Location.Wheel(FRONT_LEFT),
        Sensor(0x1A2B3C00, Location.Wheel(FRONT_LEFT), SYSGRATION),
        {}, {}, {}, {},
    )
}

@Suppress("ConstPropertyName")
internal object AssignSensorDialogTags {
    const val root = "AssignSensorDialogTags_root"
    const val detected = "AssignSensorDialogTags_detected"
    const val qrCode = "AssignSensorDialogTags_qrCode"
    const val bluetooth = "AssignSensorDialogTags_bluetooth"
}
