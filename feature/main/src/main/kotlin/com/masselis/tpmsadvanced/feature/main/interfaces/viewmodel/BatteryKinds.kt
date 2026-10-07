package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BatteryUnit.PERCENT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BatteryUnit.VOLT

/** What a vehicle's bound sensors report their battery as, which its battery alarms apply to */
internal data class BatteryKinds(val hasVoltage: Boolean, val hasPercent: Boolean) {

    internal companion object {
        fun of(brands: Collection<SensorBrand>): BatteryKinds = brands
            .map { it.batteryUnit }
            .let { units -> BatteryKinds(VOLT in units, PERCENT in units) }
    }
}
