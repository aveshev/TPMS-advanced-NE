package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.camera.view.CameraController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors
import com.masselis.tpmsadvanced.feature.qrcode.usecase.QrCodeSensorUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

/** Reads a QR code from the camera, until one is found: [Event.Found] */
@AssistedInject
internal class QRCodeViewModel(
    qrCodeSensorUseCase: QrCodeSensorUseCase,
    @Assisted controller: CameraController
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        operator fun invoke(controller: CameraController): QRCodeViewModel
    }

    sealed interface Event {
        data object LeaveBecauseCameraUnavailable : Event

        @JvmInline
        value class Found(val result: QrCodeResult) : Event
    }

    private val channel = Channel<Event>(BUFFERED)
    val eventChannel: ReceiveChannel<Event> = channel

    init {
        qrCodeSensorUseCase
            .analyse(controller)
            .map { QrCodeResult.Sensors(it) as QrCodeResult }
            .catch { exc ->
                when (exc) {
                    is CameraAnalyser.CameraUnavailable -> channel.send(Event.LeaveBecauseCameraUnavailable)

                    is QrCodeSensorUseCase.UnsupportedWircarlinkQrCode -> emit(QrCodeResult.UnsupportedWicarlink)

                    is QrCodeSensors.DuplicateWheelLocation ->
                        emit(QrCodeResult.DuplicateWheelLocation(exc.wheels.duplicates()))

                    is QrCodeSensors.DuplicateId -> emit(QrCodeResult.DuplicateId(exc.ids.duplicates()))

                    else -> throw exc
                }
            }
            // The first code found closes the camera
            .take(1)
            .onEach { channel.send(Event.Found(it)) }
            .launchIn(viewModelScope)
    }

    private fun <T> Iterable<T>.duplicates() = groupingBy { it }
        .eachCount()
        .mapNotNull { (value, count) -> if (count > 1) value else null }
}
