package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QrCodeResultViewModel.State
import com.masselis.tpmsadvanced.feature.qrcode.ioc.Bindings.Companion.QrCodeResultViewModel
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult

/**
 * What [result], a scanned QR code, leads to on the current vehicle, shown on the screen the scan
 * started from once the camera is closed. [onDismiss] once answered, [scanBluetooth] for a code the
 * app can't use.
 */
@Suppress("MaxLineLength")
@Composable
public fun QrCodeResultDialog(
    result: QrCodeResult,
    scanBluetooth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: QrCodeResultViewModel = viewModel(key = "QrCodeResult_$result") { QrCodeResultViewModel(result) }
    val state by viewModel.stateFlow.collectAsState()
    when (val state = state) {
        State.Loading -> {}

        is State.Ask -> AlertDialog(
            onDismissRequest = onDismiss,
            text = {
                Text(
                    buildString {
                        append("Assign the ${state.sensors.size} Sysgration sensors from this QR to ${state.vehicle.name}?")
                        if (state.overwrites) append("\n\n⚠️ Warning: this will overwrite already assigned sensors")
                    }
                )
            },
            confirmButton = { TextButton(onClick = viewModel::bind) { Text(text = "Yes") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
        )

        is State.TooManySensors -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text("This QR lists more sensors than ${state.vehicle.name} needs, please assign via Bluetooth") },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        is State.Unusable -> UnusableAlert(state.result, scanBluetooth, onDismiss)

        State.Assigned -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text("Sensors assigned") },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )
    }
}

@Suppress("MaxLineLength")
@Composable
private fun UnusableAlert(
    result: QrCodeResult,
    scanBluetooth: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                buildString {
                    append("Detected inconsistency with the QR Code")
                    when (result) {
                        is QrCodeResult.DuplicateWheelLocation -> {
                            append("\n\n⚠️ Filled QR Code contains different sensors associated to the same wheel, ")
                            append(if (result.wheels.size == 1) "duplication:" else "duplications:")
                            result.wheels.forEach { location ->
                                append("\n   · ")
                                appendLoc(location)
                            }
                        }

                        is QrCodeResult.DuplicateId ->
                            append("\n\n⚠️ Filled QR Code contains the same sensor id multiple time")

                        QrCodeResult.UnsupportedWicarlink ->
                            append("\n\n⚠️ QR codes made by Wicarlink aren't supported yet\nAssign each sensor with Scan via Bluetooth instead")

                        is QrCodeResult.Sensors -> {}
                    }
                }
            )
        },
        onDismissRequest = onDismiss,
        dismissButton = if (result is QrCodeResult.UnsupportedWicarlink) {
            { TextButton(onClick = { onDismiss(); scanBluetooth() }) { Text(text = "Scan via Bluetooth") } }
        } else null,
        confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
    )
}
