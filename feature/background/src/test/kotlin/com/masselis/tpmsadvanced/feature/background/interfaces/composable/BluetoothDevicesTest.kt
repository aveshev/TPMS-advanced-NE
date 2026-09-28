package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

internal class BluetoothDevicesTest {
    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material3.DayNight.NoActionBar",
        // As tall as the content, so that the end of the longer lists isn't cut
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
    )

    @Test
    fun list() {
        paparazzi.snapshot { BluetoothDevicesListPreview() }
    }

    @Test
    fun otherDevicesExpanded() {
        paparazzi.snapshot { BluetoothDevicesExpandedPreview() }
    }

    @Test
    fun bluetoothOff() {
        paparazzi.snapshot { BluetoothDevicesOffPreview() }
    }

    @Test
    fun noPairedDevice() {
        paparazzi.snapshot { BluetoothDevicesEmptyPreview() }
    }

    @Test
    fun activateScanConditions() {
        paparazzi.snapshot { ActivateScanConditionsPreview() }
    }
}
