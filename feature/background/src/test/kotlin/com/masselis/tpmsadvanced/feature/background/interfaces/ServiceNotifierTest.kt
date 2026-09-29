package com.masselis.tpmsadvanced.feature.background.interfaces

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.LowBatteryAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.NoAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.PressureAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.PressureLossAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.SensorAlarm
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.TemperatureAlert
import com.masselis.tpmsadvanced.feature.background.usecase.VehicleAlertUseCase.Alert
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class ServiceNotifierTest {

    private val atmosphere = TyreAtmosphere(0.0, 1, 1f.bar, 20f.celsius)
    private val otherAtmosphere = TyreAtmosphere(0.0, 2, 3f.bar, 90f.celsius)
    private val loss = PressureLoss(0.1f.bar, 0.0, 3600.0, 36_000.0, 1.3f.bar)

    private fun vehicle(vehicleName: String, vehicleUuid: UUID = UUID.randomUUID()) =
        mockk<Vehicle> {
            every { uuid } returns vehicleUuid
            every { name } returns vehicleName
        }

    @Test
    fun `no vehicle in alert gives no alert`() {
        val state = worst(
            listOf(
                vehicle("Car") to Alert.None,
                vehicle("Bike") to Alert.None,
            )
        )
        assertEquals(NoAlert, state)
    }

    @Test
    fun `no vehicle at all gives no alert`() {
        assertEquals(NoAlert, worst(emptyList()))
    }

    @Test
    fun `an alert is reported with the vehicle it comes from`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car") to Alert.None,
                vehicle("Bike", uuid) to Alert.Pressure(atmosphere),
            )
        )
        assertEquals(PressureAlert(uuid, "Bike", atmosphere), state)
    }

    @Test
    fun `a temperature alert is reported when there is no pressure alert`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Bike", uuid) to Alert.Temperature(atmosphere),
                vehicle("Car") to Alert.None,
            )
        )
        assertEquals(TemperatureAlert(uuid, "Bike", atmosphere), state)
    }

    @Test
    fun `a pressure alert wins over a temperature alert of an earlier vehicle`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car") to Alert.Temperature(atmosphere),
                vehicle("Bike", uuid) to Alert.Pressure(otherAtmosphere),
            )
        )
        assertEquals(PressureAlert(uuid, "Bike", otherAtmosphere), state)
    }

    @Test
    fun `ties are broken by list order`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car", uuid) to Alert.Pressure(atmosphere),
                vehicle("Bike") to Alert.Pressure(otherAtmosphere),
            )
        )
        assertEquals(PressureAlert(uuid, "Car", atmosphere), state)
    }

    @Test
    fun `a low battery is reported when no tyre alerts`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car") to Alert.None,
                vehicle("Bike", uuid) to Alert.LowBattery(atmosphere),
            )
        )
        assertEquals(LowBatteryAlert(uuid, "Bike", atmosphere), state)
    }

    @Test
    fun `a temperature alert wins over a low battery of an earlier vehicle`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car") to Alert.LowBattery(atmosphere),
                vehicle("Bike", uuid) to Alert.Temperature(otherAtmosphere),
            )
        )
        assertEquals(TemperatureAlert(uuid, "Bike", otherAtmosphere), state)
    }

    @Test
    fun `a sensor alarm wins over a temperature alert`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car") to Alert.Temperature(otherAtmosphere),
                vehicle("Bike", uuid) to Alert.SensorAlarm(atmosphere),
            )
        )
        assertEquals(SensorAlarm(uuid, "Bike", atmosphere), state)
    }

    @Test
    fun `a pressure alert wins over a sensor alarm`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car") to Alert.SensorAlarm(atmosphere),
                vehicle("Bike", uuid) to Alert.Pressure(otherAtmosphere),
            )
        )
        assertEquals(PressureAlert(uuid, "Bike", otherAtmosphere), state)
    }

    @Test
    fun `a pressure loss is reported when no tyre alerts`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car") to Alert.None,
                vehicle("Bike", uuid) to Alert.PressureLoss(Wheel(REAR_LEFT), loss),
            )
        )
        assertEquals(PressureLossAlert(uuid, "Bike", Wheel(REAR_LEFT), loss), state)
    }

    @Test
    fun `a temperature alert wins over a pressure loss of an earlier vehicle`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car") to Alert.PressureLoss(Wheel(REAR_LEFT), loss),
                vehicle("Bike", uuid) to Alert.Temperature(otherAtmosphere),
            )
        )
        assertEquals(TemperatureAlert(uuid, "Bike", otherAtmosphere), state)
    }

    @Test
    fun `a pressure loss wins over a low battery of an earlier vehicle`() {
        val uuid = UUID.randomUUID()
        val state = worst(
            listOf(
                vehicle("Car") to Alert.LowBattery(atmosphere),
                vehicle("Bike", uuid) to Alert.PressureLoss(Wheel(REAR_LEFT), loss),
            )
        )
        assertEquals(PressureLossAlert(uuid, "Bike", Wheel(REAR_LEFT), loss), state)
    }

    @Test
    fun `the tyre reaching its flat mark first is reported`() {
        val uuid = UUID.randomUUID()
        val sooner = loss.copy(flatAt = 20_000.0)
        val state = worst(
            listOf(
                vehicle("Car") to Alert.PressureLoss(Wheel(REAR_LEFT), loss),
                vehicle("Bike", uuid) to Alert.PressureLoss(Wheel(REAR_LEFT), sooner),
            )
        )
        assertEquals(PressureLossAlert(uuid, "Bike", Wheel(REAR_LEFT), sooner), state)
    }
}
