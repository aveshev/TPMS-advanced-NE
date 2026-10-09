package com.masselis.tpmsadvanced.feature.unlocated.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Preconditions
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel.BluetoothAssignViewModel
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel.BluetoothAssignViewModel.Dialog
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.viewmodel.BluetoothAssignViewModel.Event
import com.masselis.tpmsadvanced.feature.unlocated.ioc.Bindings.Companion.BluetoothAssignViewModel
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep
import java.util.UUID

/**
 * Assigns [location] a sensor found by Bluetooth: the user puts it on the wheel, takes it off and
 * puts it back, see [AssignStep]. Only [allowedIds] are listened to when given, those of a QR code.
 * [onLeave] once assigned.
 */
@Suppress("LongMethod")
@Composable
public fun BluetoothAssign(
    vehicleUuid: UUID,
    location: Location,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    allowedIds: Set<Int>? = null,
) {
    val viewModel: BluetoothAssignViewModel = viewModel { BluetoothAssignViewModel(vehicleUuid, location, allowedIds) }
    val state by viewModel.stateFlow.collectAsState()
    LaunchedEffect(viewModel) {
        for (event in viewModel.eventChannel) when (event) {
            Event.Leave -> onLeave()
        }
    }
    // Bluetooth on and its permissions granted, like the main screen
    // Its modifier only goes to what it shows in place of the steps
    Preconditions(modifier) { Steps(location, state, viewModel, modifier) }
    Dialogs(location, state.dialog, viewModel)
}

@Suppress("LongMethod")
@Composable
private fun Steps(
    location: Location,
    state: BluetoothAssignViewModel.State,
    viewModel: BluetoothAssignViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(BluetoothAssignTags.root),
    ) {
        Text(
            text = buildString { append("Assign a sensor to the "); appendLoc(location) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        when (val step = state.step) {
            AssignStep.Ask -> {
                Instruction("Is the sensor on the wheel right now?")
                Button(
                    onClick = { viewModel.answer(isOnWheel = true) },
                    modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.onWheel),
                ) { Text("Yes, the sensor is on the wheel") }
                OutlinedButton(
                    onClick = { viewModel.answer(isOnWheel = false) },
                    modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.inHand),
                ) { Text("No, the sensor is in my hand") }
            }

            is AssignStep.PutOn -> {
                Instruction("Put the sensor on the wheel (this will force it to send an update)")
                Listening("Listening for on-wheel sensors…", step.found.size.sensors("on-wheel"))
                Button(
                    onClick = viewModel::next,
                    enabled = step.found.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.next),
                ) { Text("Continue") }
                // Below 10 kPa, a sensor isn't told on a wheel from in the hand
                if (state.offersLowPressure && step.found.isEmpty() && step.low.isNotEmpty()) TextButton(
                    onClick = viewModel::lowPressure,
                    modifier = Modifier.testTag(BluetoothAssignTags.lowPressure),
                ) { Text("Low pressure tyre?") }
            }

            is AssignStep.TakeOff -> {
                Instruction("Remove the sensor from the wheel (this will force it to send an update)")
                Listening("Listening for off-wheel sensors…", step.off.size.sensors("off-wheel"))
                Button(
                    onClick = viewModel::next,
                    enabled = step.off.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag(BluetoothAssignTags.next),
                ) { Text("Continue") }
            }

            is AssignStep.PutBack -> {
                Instruction("Put the sensor back on the wheel (this will force it to send an update)")
                Listening("Listening for the sensor to come back on…", null)
            }

            is AssignStep.Found -> Instruction("Sensor found")
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun Dialogs(location: Location, dialog: Dialog?, viewModel: BluetoothAssignViewModel) {
    when (dialog) {
        null -> {}

        Dialog.OneFound -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            text = {
                Text(buildString {
                    append("One sensor found, assign it to the ")
                    appendLoc(location)
                    append(" or check again to make sure?")
                })
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::assignFound,
                    modifier = Modifier.testTag(BluetoothAssignTags.assign),
                ) { Text("Assign") }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::checkAgain,
                    modifier = Modifier.testTag(BluetoothAssignTags.checkAgain),
                ) { Text("Check again") }
            },
        )

        is Dialog.ManyFound -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            text = { Text("${dialog.count} on-wheel sensors were found, let's figure out which one to assign") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::checkAgain,
                    modifier = Modifier.testTag(BluetoothAssignTags.checkAgain),
                ) { Text("OK") }
            },
        )

        is Dialog.BoundElsewhere -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            text = {
                Text(buildString {
                    append("This sensor is assigned to ")
                    append(dialog.vehicle.name)
                    append(". It will be removed from it and assigned to the ")
                    appendLoc(location)
                    append(".")
                })
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmBoundElsewhere,
                    modifier = Modifier.testTag(BluetoothAssignTags.assign),
                ) { Text("Assign") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } },
        )

        Dialog.Assigned -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            text = { Text(buildString { append("Sensor assigned to the "); appendLoc(location) }) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissDialog,
                    modifier = Modifier.testTag(BluetoothAssignTags.assignedOk),
                ) { Text("OK") }
            },
        )
    }
}

@Composable
private fun Instruction(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.headlineSmall,
    textAlign = TextAlign.Center,
)

/** A pulsing bar while listening, [found] under it */
@Composable
private fun Listening(text: String, found: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        found?.also { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
}

/** "1 on-wheel sensor found", "2 on-wheel sensors found", nothing until one is */
private fun Int.sensors(kind: String): String? =
    takeIf { it > 0 }?.let { "$it $kind sensor${if (it > 1) "s" else ""} found" }

@Suppress("ConstPropertyName")
public object BluetoothAssignTags {
    public const val root: String = "BluetoothAssignTags_root"
    public const val onWheel: String = "BluetoothAssignTags_onWheel"
    public const val inHand: String = "BluetoothAssignTags_inHand"
    public const val next: String = "BluetoothAssignTags_next"
    public const val lowPressure: String = "BluetoothAssignTags_lowPressure"
    public const val assign: String = "BluetoothAssignTags_assign"
    public const val checkAgain: String = "BluetoothAssignTags_checkAgain"
    public const val assignedOk: String = "BluetoothAssignTags_assignedOk"
}
