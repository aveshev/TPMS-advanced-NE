package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.ManageSensorsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.ManageSensorsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.feature.main.model.MoveChain
import com.masselis.tpmsadvanced.feature.main.model.MoveChain.Step

/**
 * The vehicle's sensors, laid out like the main screen: every location is outlined, tapping one
 * manages its sensor, or assigns it one by [scanQrCode], [scanBluetooth] or the sensor detected
 * there. Moving a sensor picks where it goes on the vehicle, see [MoveChain].
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
public fun ManageSensors(
    snackbarHostState: SnackbarHostState,
    scanQrCode: () -> Unit,
    scanBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: ManageSensorsViewModel = component.viewModel(component.key()) { it.ManageSensorsViewModel() }
    val vehicle by component.vehicleStateFlow.collectAsState()
    val occupied by viewModel.occupied.collectAsState()
    val all = vehicle.kind.locations
    // Waiting for the user to pick where the last sensor of the chain goes
    var moving by rememberSaveable { mutableStateOf<MoveChain?>(null) }
    // Its last location has a sensor too: just swap them, or continue the chain
    var asking by rememberSaveable { mutableStateOf<MoveChain?>(null) }
    // Ready, waiting for the user to confirm it
    var confirming by rememberSaveable { mutableStateOf<MoveChain?>(null) }
    fun stop() {
        moving = null
        asking = null
        confirming = null
    }
    BackHandler(enabled = moving != null, onBack = ::stop)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag(ManageSensorsTags.root),
    ) {
        Text(
            text = vehicle.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = if (moving != null) "Tap the wheel you want this sensor assigned to"
                else "Tap the wheel/sensor to manage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 4.dp),
            )
            if (moving != null) TextButton(
                onClick = ::stop,
                modifier = Modifier.testTag(ManageSensorsTags.cancelMove),
            ) { Text("Cancel") }
        }
        Vehicle(
            component = component,
            snackbarHostState = snackbarHostState,
            taps = TyreTaps(
                isManaging = true,
                scanQrCode = scanQrCode,
                scanBluetooth = scanBluetooth,
                move = moving?.let { chain ->
                    TyreMove(chain) { target ->
                        when (val step = chain.tap(target, occupied, all)) {
                            is Step.Done -> confirming = step.chain
                            is Step.AskSwapOrChain -> asking = step.chain
                            is Step.Continue -> moving = step.chain
                        }
                    }
                },
                startMove = { location ->
                    if (all.size <= 2)
                    // Nowhere to pick, straight to the confirmation
                        all.first { it != location }.let { confirming = MoveChain.from(location).plus(it) }
                    else
                        moving = MoveChain.from(location)
                },
            ),
            modifier = Modifier.weight(1f),
        )
    }
    asking?.also { chain ->
        AlertDialog(
            onDismissRequest = { asking = null },
            text = { Text("This wheel already has a sensor assigned to it") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = { asking = null; confirming = chain },
                        modifier = Modifier.testTag(ManageSensorsTags.justSwap),
                    ) { Text("Just swap them") }
                    TextButton(
                        onClick = {
                            asking = null
                            when (val step = chain.continuing(all)) {
                                is Step.Done -> confirming = step.chain
                                is Step.Continue -> moving = step.chain
                                is Step.AskSwapOrChain -> error("Continuing never asks")
                            }
                        },
                        modifier = Modifier.testTag(ManageSensorsTags.multiWheel),
                    ) { Text("Multi-wheel change") }
                    TextButton(onClick = { asking = null }) { Text("Cancel") }
                }
            },
            modifier = Modifier.testTag(ManageSensorsTags.askDialog),
        )
    }
    confirming?.also { chain ->
        MoveConfirmation(
            chain = chain,
            occupied = occupied,
            isTwoLocations = all.size <= 2,
            onConfirm = { viewModel.apply(chain); stop() },
            onDismissRequest = ::stop,
        )
    }
}

/**
 * Asks to apply [chain]: a vehicle with two locations only says whether it's a swap or a move,
 * the others list each sensor's move
 */
@Composable
private fun MoveConfirmation(
    chain: MoveChain,
    occupied: Set<Location>,
    isTwoLocations: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val moves = chain.moves(occupied)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                when {
                    isTwoLocations.not() -> "Move the sensors?"
                    moves.size > 1 -> "Swap the sensors?"
                    else -> "Move the sensor to the other wheel?"
                }
            )
        },
        text = if (isTwoLocations) null else {
            {
                Column {
                    moves.forEach { (from, to) ->
                        Text(
                            buildString {
                                appendLoc(from, withType = false, capitalized = true)
                                append(" → ")
                                appendLoc(to, withType = false, capitalized = true)
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
                onClick = onConfirm,
                modifier = Modifier.testTag(ManageSensorsTags.confirmMove),
            ) { Text(if (isTwoLocations) "Yes" else "OK") }
        },
        modifier = modifier.testTag(ManageSensorsTags.confirmDialog),
    )
}

@Preview
@Composable
internal fun MoveConfirmationPreview() {
    val car = listOf(FRONT_LEFT, FRONT_RIGHT, REAR_RIGHT, REAR_LEFT).map { Location.Wheel(it) }
    MoveConfirmation(MoveChain(car), car.toSet(), isTwoLocations = false, {}, {})
}

@Suppress("ConstPropertyName")
internal object ManageSensorsTags {
    const val root = "ManageSensorsTags_root"
    const val cancelMove = "ManageSensorsTags_cancelMove"
    const val askDialog = "ManageSensorsTags_askDialog"
    const val justSwap = "ManageSensorsTags_justSwap"
    const val multiWheel = "ManageSensorsTags_multiWheel"
    const val confirmDialog = "ManageSensorsTags_confirmDialog"
    const val confirmMove = "ManageSensorsTags_confirmMove"
}
