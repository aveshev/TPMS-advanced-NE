package com.masselis.tpmsadvanced.interfaces

import android.app.UiModeManager.MODE_NIGHT_NO
import android.app.UiModeManager.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import com.masselis.tpmsadvanced.interfaces.screens.Home
import com.masselis.tpmsadvanced.interfaces.screens.Home.Companion.home
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class TakeScreenshotTest {

    @get:Rule
    val androidComposeTestRule = createAndroidComposeRule<RootActivity>()

    @Test
    fun lightModeScreenshots() {
        androidComposeTestRule.home {
            takeScreenshots(
                AppCompatDelegate.MODE_NIGHT_NO,
                listOf(FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT).map { Location.Wheel(it) },
            )
        }
    }

    @Test
    fun darkModeScreenshots() {
        androidComposeTestRule.home {
            dropdownMenu {
                addVehicle {
                    setVehicleName("Motorcycle")
                    setKind(MOTORCYCLE)
                    add()
                }
            }
            takeScreenshots(AppCompatDelegate.MODE_NIGHT_YES, listOf(FRONT, REAR).map { Location.Axle(it) })
        }
    }

    /**
     * The Play Store listing's screenshots, see the app's `copyScreenshot` task. The demo sensors
     * detected at [locations] are assigned first, so the screens show their readings.
     */
    private fun Home.takeScreenshots(@AppCompatDelegate.NightMode mode: Int, locations: List<Location>) {
        androidComposeTestRule.activityRule.scenario.onActivity {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        androidComposeTestRule.waitForIdle()
        val prefix = when (mode) {
            MODE_NIGHT_NO -> "light_"
            MODE_NIGHT_YES -> "dark_"
            else -> error("Unknown mode sent $mode")
        }
        locations.forEach { location ->
            wheel(location) {
                assign { assignDetected() }
                waitUntilAssigned()
            }
        }
        actionOverflow {
            settings {
                capture("${prefix}settings")
                manageSensors {
                    waitForReadings()
                    capture("${prefix}manage_sensors")
                    leave()
                }
                leave()
            }
        }
        // Back on the main screen, the demo scanner sends the assigned sensors' readings again
        waitForReadings()
        capture("${prefix}main")
    }

    private fun waitForReadings() = androidComposeTestRule.waitUntil(READINGS_TIMEOUT) {
        androidComposeTestRule.onAllNodes(hasText(NO_PRESSURE)).fetchSemanticsNodes().isEmpty()
    }

    private fun capture(name: String) = androidComposeTestRule
        .onRoot()
        .captureToImage()
        .asAndroidBitmap()
        .writeToTestStorage(name)

    private companion object {
        /** What a readout shows without a pressure, see TyreStat */
        const val NO_PRESSURE = "-.--"
        const val READINGS_TIMEOUT = 10_000L
    }
}
