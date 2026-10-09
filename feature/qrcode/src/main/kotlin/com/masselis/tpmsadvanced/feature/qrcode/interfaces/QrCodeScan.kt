package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import android.Manifest.permission.CAMERA
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.masselis.tpmsadvanced.core.ui.LocalHomeNavController
import com.masselis.tpmsadvanced.core.ui.MissingPermission
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QRCodeViewModel.Event
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QRCodeViewModel.State
import com.masselis.tpmsadvanced.feature.qrcode.ioc.Bindings.Companion.QrCodeViewModel


@OptIn(ExperimentalPermissionsApi::class)
@Composable
public fun QrCodeScan(
    snackbarHostState: SnackbarHostState,
    scanBluetooth: () -> Unit,
    assignViaBluetooth: (sensorIds: Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionState = rememberMultiplePermissionsState(listOf(CAMERA))
    when {
        permissionState.allPermissionsGranted.not() -> MissingPermission(
            text = "TPMS Advanced need you to approve a permission to scan the QR Code",
            refusedText = "This app requires camera permission to scan QR, please enable it in settings",
            permissionState = permissionState,
            autoRequest = true,
            modifier = modifier,
        )

        else -> Preview(
            snackbarHostState = snackbarHostState,
            scanBluetooth = scanBluetooth,
            assignViaBluetooth = assignViaBluetooth,
            modifier = modifier,
        )
    }
}

@Suppress("NAME_SHADOWING")
@Composable
private fun Preview(
    snackbarHostState: SnackbarHostState,
    scanBluetooth: () -> Unit,
    assignViaBluetooth: (sensorIds: Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = DEFAULT_BACK_CAMERA,
) {
    val controller = LocalContext.current
        .let { context ->
            remember {
                LifecycleCameraController(context).apply { this.cameraSelector = cameraSelector }
            }
        }
        .also { controller ->
            LocalLifecycleOwner.current.also { lifecycleOwner ->
                DisposableEffect(controller) {
                    controller.bindToLifecycle(lifecycleOwner)
                    onDispose { controller.unbind() }
                }
            }
        }

    Box(modifier) {
        AndroidView(
            { context -> PreviewView(context).apply { this.controller = controller } },
            Modifier.fillMaxSize()
        )
        QrCodeOverlay(Modifier.fillMaxSize())
    }

    val viewModel = remember(controller) { QrCodeViewModel(controller) }
    val navController = LocalHomeNavController.current
    val state by viewModel.stateFlow.collectAsState()
    when (val state = state) {
        State.Scanning -> {}

        is State.AskForBinding -> BindingAlert(
            state = state,
            onDismissRequest = viewModel::scanAgain,
            onBind = viewModel::bindSensors,
            assignViaBluetooth = { assignViaBluetooth(state.qrCodeSensors.map { it.id }.toSet()) },
        )

        is State.Error -> ErrorAlert(
            state = state,
            onDismissRequest = viewModel::scanAgain,
            scanBluetooth = scanBluetooth,
        )

        State.Assigned -> AlertDialog(
            onDismissRequest = viewModel::leave,
            text = { Text("Sensors assigned") },
            confirmButton = { TextButton(onClick = viewModel::leave) { Text(text = "OK") } },
        )
    }

    LaunchedEffect(viewModel) {
        for (event in viewModel.eventChannel) {
            when (event) {
                Event.Leave -> navController.popBackStack()

                Event.LeaveBecauseCameraUnavailable -> {
                    snackbarHostState.showSnackbar("Your device should to have a camera to continue")
                    navController.popBackStack()
                }
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun BindingAlert(
    state: State.AskForBinding,
    onDismissRequest: () -> Unit,
    onBind: () -> Unit,
    assignViaBluetooth: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                text = StringBuilder(
                    "Sysgration sensors can be assigned to all wheels automatically at once, do you want to do it now?"
                )
                    .apply {
                        when (state) {

                            is State.AskForBinding.Compatible -> {}

                            is State.AskForBinding.Missing -> {
                                append("\n\n⚠️ Filled QR Code doesn't contains sensors dedicated to ")
                                state.locations.forEachIndexed { index, location ->
                                    append("the ")
                                    appendLoc(location)
                                    append(
                                        when (index) {
                                            state.locations.size - 1 -> "."
                                            state.locations.size - 2 -> " and "
                                            else -> ", "
                                        }
                                    )
                                }
                            }
                        }
                    }
                    .toString()
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onBind) {
                Text(text = "Assign automatically")
            }
        },
        // Listening to the code's sensors only, the neighbours' are left out
        dismissButton = {
            TextButton(onClick = assignViaBluetooth) {
                Text(text = "Assign via Bluetooth")
            }
        }
    )
}

@Suppress("MaxLineLength")
@Composable
private fun ErrorAlert(
    state: State.Error,
    onDismissRequest: () -> Unit,
    scanBluetooth: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                text = StringBuilder("Detected inconsistency with the QR Code")
                    .apply {
                        when (state) {

                            is State.Error.DuplicateWheelLocation -> {
                                append("\n\n⚠️ Filled QR Code contains different sensors associated to the same wheel, ")
                                if (state.wheels.size == 1) append("duplication:")
                                else append("duplications:")
                                state.wheels.forEach { location ->
                                    append("\n   · ")
                                    appendLoc(location)
                                }
                            }

                            is State.Error.DuplicateId -> {
                                append("\n\n⚠️ Filled QR Code contains the same sensor id multiple time")
                            }

                            State.Error.UnsupportedWircarlinkQrCode -> {
                                append("\n\n⚠️ QR codes made by Wicarlink aren't supported yet\nAssign each sensor with Scan via Bluetooth instead")
                            }
                        }
                    }
                    .toString()
            )
        },
        onDismissRequest = onDismissRequest,
        dismissButton =
            if (state is State.Error.UnsupportedWircarlinkQrCode) {
                {
                    TextButton(onClick = scanBluetooth) {
                        Text(text = "Scan via Bluetooth")
                    }
                }
            } else null,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "OK")
            }
        },
    )
}


@Composable
private fun QrCodeOverlay(
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        // The usual scanner look: the preview dimmed around a clear square, its corners marked
        Canvas(
            Modifier
                .fillMaxSize()
                // Offscreen, so the clear square cuts through the dim rather than painting over it
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val side = size.minDimension * WINDOW_FRACTION
            val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
            val radius = CornerRadius(CORNER_RADIUS.toPx())
            drawRect(Color.Black.copy(alpha = DIM_ALPHA))
            drawRoundRect(Color.Transparent, topLeft, Size(side, side), radius, blendMode = BlendMode.Clear)
            val stroke = BRACKET_WIDTH.toPx()
            val arm = side * BRACKET_FRACTION
            // Each corner: its position, and which way its arms go
            listOf(
                topLeft to Offset(1f, 1f),
                topLeft + Offset(side, 0f) to Offset(-1f, 1f),
                topLeft + Offset(0f, side) to Offset(1f, -1f),
                topLeft + Offset(side, side) to Offset(-1f, -1f),
            ).forEach { (corner, direction) ->
                drawLine(Color.White, corner, corner + Offset(arm * direction.x, 0f), stroke, StrokeCap.Round)
                drawLine(Color.White, corner, corner + Offset(0f, arm * direction.y), stroke, StrokeCap.Round)
            }
        }
        Text(
            text = "Point the camera at the sensors' QR code",
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 48.dp),
        )
    }
}

private const val WINDOW_FRACTION = .7f
private const val BRACKET_FRACTION = .15f
private const val DIM_ALPHA = .55f
private val CORNER_RADIUS = 4.dp
private val BRACKET_WIDTH = 4.dp
