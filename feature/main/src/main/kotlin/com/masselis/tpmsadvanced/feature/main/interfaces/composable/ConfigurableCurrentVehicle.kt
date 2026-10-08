package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorConfigurationViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.SensorConfigurationViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key

/**
 * The current vehicle, which also configures its sensors while [isConfiguring]: a bar at the
 * bottom switches each location's readout for its actions, and leaves the configuration once the
 * vehicle has a sensor. A vehicle without any is always being configured, [onConfiguringChange]
 * is called to say so.
 */
@Suppress("LongMethod")
@Composable
public fun ConfigurableCurrentVehicle(
    isConfiguring: Boolean,
    onConfiguringChange: (Boolean) -> Unit,
    /** Starts assigning a sensor to this location */
    assign: (Location) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    center: @Composable (Modifier) -> Unit = {},
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: SensorConfigurationViewModel =
        component.viewModel(component.key()) { it.SensorConfigurationViewModel() }
    val canFinish by viewModel.canFinish.collectAsState()
    LaunchedEffect(canFinish) { if (canFinish.not()) onConfiguringChange(true) }
    // Each configuration starts on the actions, that's what it's opened for
    var showActions by rememberSaveable(isConfiguring) { mutableStateOf(true) }
    Column(modifier) {
        Vehicle(
            component = component,
            snackbarHostState = snackbarHostState,
            center = center,
            tyreActions = if (isConfiguring && showActions) { location, modifier ->
                TyreActionButtons(location, { assign(location) }, modifier, component)
            } else null,
            modifier = Modifier.weight(1f),
        )
        if (isConfiguring) Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(ConfigurableCurrentVehicleTags.bar),
        ) {
            SingleChoiceSegmentedButtonRow {
                listOf(true, false).forEachIndexed { index, actions ->
                    SegmentedButton(
                        selected = showActions == actions,
                        onClick = { showActions = actions },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        // Same as the settings' segmented buttons, the fill tells the selection
                        icon = {},
                        label = { Text(if (actions) "Actions" else "Readings") },
                        modifier = Modifier.testTag(
                            if (actions) ConfigurableCurrentVehicleTags.actions
                            else ConfigurableCurrentVehicleTags.readings
                        ),
                    )
                }
            }
            Button(
                onClick = { onConfiguringChange(false) },
                enabled = canFinish,
                modifier = Modifier.testTag(ConfigurableCurrentVehicleTags.finish),
            ) { Text("Finish") }
        }
    }
}

@Suppress("ConstPropertyName")
internal object ConfigurableCurrentVehicleTags {
    const val bar = "ConfigurableCurrentVehicleTags_bar"
    const val actions = "ConfigurableCurrentVehicleTags_actions"
    const val readings = "ConfigurableCurrentVehicleTags_readings"
    const val finish = "ConfigurableCurrentVehicleTags_finish"
}
