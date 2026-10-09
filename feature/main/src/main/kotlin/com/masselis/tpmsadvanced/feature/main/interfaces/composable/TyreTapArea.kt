package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent

private val OUTSET = 6.dp
private val SHAPE = RoundedCornerShape(12.dp)

/**
 * Covers a location's tyre and readout. Tapping it assigns a sensor while there's none, and opens
 * a menu managing the assigned one while [isManaging]. It's outlined whenever it can be tapped.
 */
@Suppress("LongMethod")
@Composable
internal fun TyreTapArea(
    location: Location,
    isManaging: Boolean,
    scanQrCode: () -> Unit,
    scanBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    viewModel: TyreActionsViewModel = vehicleComponent
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreActionsViewModel() } },
) {
    val state by viewModel.stateFlow.collectAsState()
    // A dialog to assign a sensor, a menu to manage the assigned one
    var isOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val isUnassigned = state is State.Unassigned
    val isTappable = when (state) {
        State.Demo -> false
        is State.Unassigned -> true
        is State.Assigned -> isManaging
    }
    Box(
        modifier
            .outset(OUTSET)
            .run { if (isManaging || isUnassigned) border(2.dp, MaterialTheme.colorScheme.primary, SHAPE) else this }
            .clip(SHAPE)
            .run { if (isTappable) clickable { isOpen = true } else this }
            .testTag(TyreTapAreaTags.root(location))
    ) {
        (state as? State.Assigned)?.also { assigned ->
            ManageSensorMenu(
                expanded = isOpen,
                location = location,
                sensor = assigned.sensor,
                delete = { isOpen = false; confirmDelete = true },
                onDismissRequest = { isOpen = false },
            )
        }
    }
    (state as? State.Unassigned)
        ?.takeIf { isOpen }
        ?.also { unassigned ->
            AssignSensorDialog(
                location = location,
                detected = unassigned.detected,
                scanQrCode = { isOpen = false; scanQrCode() },
                scanBluetooth = { isOpen = false; scanBluetooth() },
                assignDetected = { viewModel.assign(it); isOpen = false },
                onDismissRequest = { isOpen = false },
            )
        }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = {
            Text(
                buildString {
                    append("Remove the sensor from the ")
                    appendLoc(location)
                    append("?\nThis cannot be undone.")
                }
            )
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.delete(); confirmDelete = false },
                modifier = Modifier.testTag(TyreTapAreaTags.confirmDelete),
            ) { Text("Remove") }
        },
    )
}

/** Grows by [outset] on every side, around what it's laid out on */
private fun Modifier.outset(outset: Dp) = layout { measurable, constraints ->
    val extra = outset.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
            minHeight = constraints.minHeight + extra,
            maxHeight = constraints.maxHeight + extra,
        )
    )
    layout(placeable.width - extra, placeable.height - extra) {
        placeable.place(-extra / 2, -extra / 2)
    }
}

/** What can be done with the [sensor] assigned to [location], under its position and id */
@Composable
private fun ManageSensorMenu(
    expanded: Boolean,
    location: Location,
    sensor: Sensor,
    delete: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(TyreTapAreaTags.manageMenu),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                buildString { appendLoc(location, withType = false, capitalized = true) },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Sensor ${sensor.id.asSensorId()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = delete,
            modifier = Modifier.testTag(TyreTapAreaTags.delete),
        )
    }
}

@Preview
@Composable
internal fun ManageSensorMenuPreview() {
    Box {
        ManageSensorMenu(
            true,
            Location.Wheel(FRONT_RIGHT),
            Sensor(0x0A0B0C, Location.Wheel(FRONT_RIGHT), PECHAM),
            {},
            {},
        )
    }
}

@Suppress("ConstPropertyName")
internal object TyreTapAreaTags {
    fun root(location: Location) = "TyreTapAreaTags_root_$location"
    const val manageMenu = "TyreTapAreaTags_manageMenu"
    const val delete = "TyreTapAreaTags_delete"
    const val confirmDelete = "TyreTapAreaTags_confirmDelete"
}
