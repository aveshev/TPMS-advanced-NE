package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.compose.ui.text.buildAnnotatedString
import com.masselis.tpmsadvanced.core.ui.appendBold
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
import java.util.UUID

/**
 * What [result], a scanned QR code, leads to on the vehicle [vehicleUuid] it was scanned for, shown
 * on the screen the scan started from once the camera is closed. [scan] tells one scan from the
 * next, so a code scanned again is worked out again. [onDismiss] once answered, [scanBluetooth] for
 * a code the app can't use.
 */
@Suppress("MaxLineLength")
@Composable
public fun QrCodeResultDialog(
    vehicleUuid: UUID,
    result: QrCodeResult,
    scan: Int,
    scanBluetooth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: QrCodeResultViewModel = viewModel(key = "QrCodeResult_$scan") {
        QrCodeResultViewModel(vehicleUuid, result)
    }
    val state by viewModel.stateFlow.collectAsState()
    when (val state = state) {
        State.Loading -> {}

        is State.Ask -> AlertDialog(
            onDismissRequest = onDismiss,
            text = {
                Text(
                    buildAnnotatedString {
                        append("Assign the ${state.sensors.size} Sysgration sensors from this QR to ")
                        appendBold(state.vehicle.name)
                        append("?")
                        if (state.overwrites) append("\n\n⚠️ Warning: this will overwrite already assigned sensors")
                    }
                )
            },
            // Answering yes is confirmation enough, the assigned sensors show on the vehicle
            confirmButton = {
                TextButton(onClick = { viewModel.bind(); onDismiss() }) { Text(text = "Yes") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
        )

        is State.TooManySensors -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Too many sensors") },
            text = {
                Text(
                    buildAnnotatedString {
                        append("This QR lists ${state.count} sensors, but ")
                        appendBold(state.vehicle.name)
                        append(" only needs ${state.vehicle.kind.locations.size}.\nPlease assign via Bluetooth instead.")
                    }
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        State.Unreadable -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text("Error reading QR, please assign via Bluetooth instead") },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        is State.Unusable -> UnusableAlert(state.result, scanBluetooth, onDismiss)
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
