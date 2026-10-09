package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.AssignMethod.BLUETOOTH
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.AssignMethod.DETECTED
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.AssignMethod.QR_CODE

/**
 * Asks how to assign a sensor to [location]: by its QR code, by scanning the sensors around, or
 * directly the [detected] sensor when one advertises this location
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
    var method by rememberSaveable { mutableStateOf(null as AssignMethod?) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(buildString { append("Assign a sensor to the "); appendLoc(location) }) },
        text = {
            Column {
                Row {
                    MethodCard(QR_CODE, method == QR_CODE, { method = QR_CODE }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MethodCard(BLUETOOTH, method == BLUETOOTH, { method = BLUETOOTH }, Modifier.weight(1f))
                }
                detected?.also { sensor ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .selectable(
                                selected = method == DETECTED,
                                onClick = { method = DETECTED },
                                role = Role.RadioButton,
                            )
                            .testTag(AssignSensorDialogTags.detected),
                    ) {
                        RadioButton(selected = method == DETECTED, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            buildString {
                                append("Assign the detected ")
                                appendLoc(location, withType = false)
                                append(" Sysgration sensor here (")
                                append(sensor.id.asSensorId())
                                append(")")
                            }
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (method) {
                        QR_CODE -> scanQrCode()
                        BLUETOOTH -> scanBluetooth()
                        DETECTED -> detected?.also(assignDetected)
                        null -> {}
                    }
                },
                enabled = method != null,
                modifier = Modifier.testTag(AssignSensorDialogTags.next),
            ) {
                Text(
                    when (method) {
                        QR_CODE -> "Scan QR Code"
                        BLUETOOTH -> "Bind sensor one by one"
                        DETECTED -> "Assign"
                        null -> "Next"
                    }
                )
            }
        },
        modifier = modifier.testTag(AssignSensorDialogTags.root),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodCard(
    method: AssignMethod,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 100),
        label = "color_animation_$method"
    )
    OutlinedCard(
        shape = RoundedCornerShape(percent = 20),
        border = BorderStroke(2.dp, borderColor),
        onClick = onClick,
        modifier = modifier.testTag(
            if (method == QR_CODE) AssignSensorDialogTags.qrCode else AssignSensorDialogTags.bluetooth
        ),
    ) {
        Text(
            text = when (method) {
                QR_CODE -> "Scan QR Code"
                BLUETOOTH, DETECTED -> "Bind manually"
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, start = 4.dp, end = 4.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Image(
            painter = painterResource(
                id = when (method) {
                    QR_CODE -> R.drawable.sysgration_sensor
                    BLUETOOTH, DETECTED -> R.drawable.pecham_sensor
                }
            ),
            contentDescription = when (method) {
                QR_CODE -> "Sysgration sensors"
                BLUETOOTH, DETECTED -> "Pecham sensors"
            },
            contentScale = ContentScale.FillHeight,
            modifier = Modifier
                .height(72.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (method) {
                QR_CODE -> "Sysgration sensors"
                BLUETOOTH, DETECTED -> "Other sensors"
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

private enum class AssignMethod { QR_CODE, BLUETOOTH, DETECTED }

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
    const val next = "AssignSensorDialogTags_next"
    const val qrCode = "AssignSensorDialogTags_qrCode"
    const val bluetooth = "AssignSensorDialogTags_bluetooth"
}
