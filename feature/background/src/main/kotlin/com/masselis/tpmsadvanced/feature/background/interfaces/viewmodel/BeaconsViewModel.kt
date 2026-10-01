package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Beacon
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Mode
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Mode.LOW_LATENCY
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Companion.asBeaconScanMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    private val reloads = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Whether the page adding a beacon is seen: it only scans then, see [scanPage] */
    val scanPageVisible = MutableStateFlow(false)

    /**
     * The devices around, to pick a beacon from, in an order which only changes on [reload]. Kept
     * for as long as the page is, the screen turning off only pausing the scan: what was found so
     * far is still listed when it turns on again.
     */
    val scanPage: StateFlow<BeaconDiscoveryUseCase.Page> = beaconDiscoveryUseCase
        .page(reloads, scanPageVisible)
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            BeaconDiscoveryUseCase.Page.Searching(BeaconDiscoveryUseCase.SEARCH, found = 0),
        )

    /** Sorts the devices again by their current signal, adding the new ones */
    fun reload() {
        reloads.tryEmit(Unit)
    }

    /** The mode of the background scan for beacons, a debug option */
    val scanMode: Flow<Mode> = appPreferences.beaconScanMode.map { it.asBeaconScanMode() }

    fun setScanMode(mode: Mode) {
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
