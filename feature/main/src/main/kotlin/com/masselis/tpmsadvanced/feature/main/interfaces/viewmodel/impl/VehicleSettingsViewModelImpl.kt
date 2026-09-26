package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.RenameVehicleUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleCalibrationUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleRangesUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class VehicleSettingsViewModelImpl(
    private val vehicleRangesUseCase: VehicleRangesUseCase,
    vehicleCalibrationUseCase: VehicleCalibrationUseCase,
    private val renameVehicleUseCase: RenameVehicleUseCase,
    override val vehicle: StateFlow<Vehicle>,
    unitPreferences: UnitPreferences,
) : ViewModel(), VehicleSettingsViewModel {

    override val lowPressure = vehicleRangesUseCase.lowPressure
    override val highPressure = vehicleRangesUseCase.highPressure
    override val rearLowPressure = vehicleRangesUseCase.rearLowPressure
    override val rearHighPressure = vehicleRangesUseCase.rearHighPressure
    override val separateRearPressure = vehicleRangesUseCase.separateRearPressure.asStateFlow()

    override val pressureUnit = unitPreferences.pressure.asStateFlow()

    override val pressureCalibration = vehicleCalibrationUseCase.isEnabled
    override val pressureOffset = vehicleCalibrationUseCase.offset
    override val pressureMultiplier = vehicleCalibrationUseCase.multiplier

    override val highTemp = vehicleRangesUseCase.highTemp
    override val normalTemp = vehicleRangesUseCase.normalTemp
    override val lowTemp = vehicleRangesUseCase.lowTemp

    override val temperatureUnit = unitPreferences.temperature.asStateFlow()

    override fun setRearOverrideEnabled(enabled: Boolean): Unit =
        vehicleRangesUseCase.setRearOverrideEnabled(enabled)

    override fun rename(name: String) {
        viewModelScope.launch { renameVehicleUseCase.rename(name) }
    }
}
