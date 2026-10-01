package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Beacon
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_LATENCY
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Companion.asBeaconScanMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
internal class BeaconsViewModel(
    private val appPreferences: AppPreferences,
    beaconPresenceUseCase: BeaconPresenceUseCase,
    beaconDiscoveryUseCase: BeaconDiscoveryUseCase,
) : ViewModel() {

    val activateOnBeacon = appPreferences.activateOnBeacon
    val beacons = appPreferences.beacons
    val beaconOverridesSuspend = appPreferences.beaconOverridesSuspend

    /**
     * The saved beacons nearby, with their signal. A scan of its own at full speed, whatever mode
     * the background one uses, so that the page tells right away which beacon is around.
     */
    val nearby: Flow<Map<String, Int>> = beacons
        // Renaming a beacon doesn't need another scan
        .map { beacons -> beacons.map { it.copy(label = null) } }
        .distinctUntilChanged()
        .flatMapLatest { beaconPresenceUseCase.nearby(it, LOW_LATENCY) }

    /** Every device around, to pick a beacon from */
    val discovered: Flow<List<BeaconDiscoveryUseCase.Device>> = beaconDiscoveryUseCase.devices()

    /** The mode of the background scan for beacons, a debug option */
    val scanMode: Flow<ScanMode> = appPreferences.beaconScanMode.map { it.asBeaconScanMode() }

    fun setScanMode(mode: ScanMode) {
        appPreferences.beaconScanMode.value = mode.name
    }

    /** Adds the device as a beacon, once */
    fun add(device: BeaconDiscoveryUseCase.Device) {
        beacons.value
            .takeIf { beacons -> beacons.none { it.address == device.address } }
            ?.also { beacons.value = it + Beacon(device.address, device.name, label = null) }
    }

    /** A blank [label] removes the user's name, the advertised one is shown again */
    fun rename(address: String, label: String) {
        beacons.value = beacons.value.map { beacon ->
            if (beacon.address == address) beacon.copy(label = label.trim().takeIf { it.isNotEmpty() })
            else beacon
        }
    }

    fun remove(addresses: Set<String>) {
        beacons.value = beacons.value.filter { it.address !in addresses }
    }

    /** Without any beacon to look for the condition could never be fulfilled: same as turning it off */
    fun disableIfNoBeacon() {
        if (beacons.value.isEmpty()) activateOnBeacon.value = false
    }
}
