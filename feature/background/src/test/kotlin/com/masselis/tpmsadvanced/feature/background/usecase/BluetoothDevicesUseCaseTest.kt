package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase.PairedDevice
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class BluetoothDevicesUseCaseTest {

    private fun service(shortUuid: Int) = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(shortUuid))

    @Test
    fun `an audio device class is enough`() {
        assertTrue(PairedDevice.isAudio(AUDIO_VIDEO, emptyList()))
    }

    @Test
    fun `an audio service is enough, whatever the device class tells`() {
        // A car stereo telling itself a hands-free device, an LE Audio device with no class at all
        assertTrue(PairedDevice.isAudio(UNCATEGORIZED, listOf(service(0x111E))))
        assertTrue(PairedDevice.isAudio(null, listOf(service(0x1850))))
    }

    @Test
    fun `neither an audio class nor an audio service is not an audio device`() {
        // A fitness band: a wearable offering only a vendor service
        assertFalse(PairedDevice.isAudio(WEARABLE, listOf(UUID.randomUUID())))
        assertFalse(PairedDevice.isAudio(null, emptyList()))
    }

    private companion object {
        const val AUDIO_VIDEO = 0x0400
        const val WEARABLE = 0x0700
        const val UNCATEGORIZED = 0x1F00
    }
}
