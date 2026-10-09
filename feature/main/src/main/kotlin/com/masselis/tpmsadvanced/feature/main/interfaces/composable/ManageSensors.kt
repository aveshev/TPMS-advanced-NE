package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent

/**
 * The vehicle's sensors, laid out like the main screen: every location is outlined, tapping one
 * manages its sensor, or assigns it one by [scanQrCode], [scanBluetooth] or the sensor detected
 * there
 */
@Composable
public fun ManageSensors(
    snackbarHostState: SnackbarHostState,
    scanQrCode: () -> Unit,
    scanBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val vehicle by component.vehicleStateFlow.collectAsState()
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
        Text(
            text = "Tap the wheel/sensor to manage",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Vehicle(
            component = component,
            snackbarHostState = snackbarHostState,
            taps = TyreTaps(isManaging = true, scanQrCode = scanQrCode, scanBluetooth = scanBluetooth),
            modifier = Modifier.weight(1f),
        )
    }
}

@Suppress("ConstPropertyName")
internal object ManageSensorsTags {
    const val root = "ManageSensorsTags_root"
}
