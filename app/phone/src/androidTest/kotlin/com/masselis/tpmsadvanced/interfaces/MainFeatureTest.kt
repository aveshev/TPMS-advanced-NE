package com.masselis.tpmsadvanced.interfaces

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import com.masselis.tpmsadvanced.interfaces.screens.Home.Companion.home
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class MainFeatureTest {

    @get:Rule
    val androidComposeTestRule = createAndroidComposeRule<RootActivity>()

    @Before
    fun setup() {
    }

    @Suppress("LongMethod")
    @Test
    fun mainFeatures() = androidComposeTestRule.home {
        actionOverflow {
            settings {
                assertVehicleDeleteIsNotEnabled()
                leave()
            }
        }
        dropdownMenu {
            addVehicle {
                cancel()
            }
        }
        dropdownMenu {
            addVehicle {
                setVehicleName("Car")
                setKind(CAR)
                add()
            }
        }
        dropdownMenu {
            addVehicle {
                setVehicleName("Motorcycle")
                setKind(MOTORCYCLE)
                add()
            }
        }
        dropdownMenu {
            assertVehicleExists("Car")
            assertVehicleExists("Motorcycle")
            close()
        }
        dropdownMenu {
            select("Car")
        }
        // The demo scanner's Sysgration sensor advertising the front left is detected there
        wheel(Location.Wheel(FRONT_LEFT)) {
            waitUntilUnassigned()
            assign {
                assertDetectedIsOffered()
                cancel()
            }
            assign {
                assignDetected()
            }
            waitUntilAssigned()
        }
        actionOverflow {
            settings {
                manageSensors {
                    wheel(Location.Wheel(FRONT_LEFT)) {
                        manage {
                            delete(confirm = false)
                        }
                        waitUntilAssigned()
                        manage {
                            delete()
                        }
                        waitUntilUnassigned()
                    }
                    leave()
                }
                leave()
            }
        }
        wheel(Location.Wheel(FRONT_LEFT)) {
            assign {
                assignDetected()
            }
            waitUntilAssigned()
        }
        actionOverflow {
            settings {
                waitDeleteAllSensorsEnabled()
                deleteAllSensors(confirm = false)
                waitDeleteAllSensorsEnabled()
                deleteAllSensors()
                waitDeleteAllSensorsDisabled()
                leave()
            }
        }
        wheel(Location.Wheel(FRONT_LEFT)) {
            waitUntilUnassigned()
        }
        dropdownMenu {
            select("My car")
        }
        actionOverflow {
            settings {
                deleteVehicle {
                    cancel()
                }
                deleteVehicle {
                    delete()
                }
            }
        }
        dropdownMenu {
            assertVehicleDoesNotExists("My car")
            select("Car")
        }
        wheel(Location.Wheel(FRONT_LEFT)) {
            assign {
                scanBluetooth()
            }
        }
        unlocatedSensorsList {
            tapSensorUnplugged()
            tapSensor(2) {
                assertBindButtonIsNotEnabled()
                tapLocation(Location.Wheel(FRONT_LEFT))
                tapCancel()
            }
            tapSensor(2) {
                tapLocation(Location.Wheel(FRONT_LEFT))
                tapBindButton()
            }
            tapSensor(4) {
                tapLocation(Location.Wheel(FRONT_RIGHT))
                tapBindButton()
            }
            tapSensor(6) {
                tapLocation(Location.Wheel(REAR_LEFT))
                tapBindButton()
            }
            tapSensor(8) {
                tapLocation(Location.Wheel(REAR_RIGHT))
                tapBindButton()
            }
            assertAllLocationBound(
                2 to Location.Wheel(FRONT_LEFT),
                4 to Location.Wheel(FRONT_RIGHT),
                6 to Location.Wheel(REAR_LEFT),
                8 to Location.Wheel(REAR_RIGHT),
            )
            tapGoBack()
        }
        listOf(FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT).forEach {
            wheel(Location.Wheel(it)) { waitUntilAssigned() }
        }
        actionOverflow {
            settings {
                manageSensors {
                    // Front left and right swap their sensors
                    wheel(Location.Wheel(FRONT_LEFT)) { manage { move() } }
                    wheel(Location.Wheel(FRONT_RIGHT)) { tapWhileMoving() }
                    justSwap()
                    confirmMove()
                    // Then all four rotate, the rear left's sensor coming back to the front left
                    wheel(Location.Wheel(FRONT_LEFT)) { manage { move() } }
                    wheel(Location.Wheel(FRONT_RIGHT)) { tapWhileMoving() }
                    multiWheelChange()
                    wheel(Location.Wheel(REAR_RIGHT)) { tapWhileMoving() }
                    wheel(Location.Wheel(REAR_LEFT)) { tapWhileMoving() }
                    confirmMove()
                    // And three of them, the rear right's sensor going to the front left
                    wheel(Location.Wheel(FRONT_LEFT)) { manage { move() } }
                    wheel(Location.Wheel(FRONT_RIGHT)) { tapWhileMoving() }
                    multiWheelChange()
                    wheel(Location.Wheel(REAR_RIGHT)) { tapWhileMoving() }
                    wheel(Location.Wheel(FRONT_LEFT)) { tapWhileMoving() }
                    confirmMove()
                    listOf(FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT).forEach {
                        wheel(Location.Wheel(it)) { waitUntilAssigned() }
                    }
                    leave()
                }
                leave()
            }
        }
    }
}
