package com.masselis.tpmsadvanced.feature.background.usecase

import android.annotation.SuppressLint
import android.content.IntentFilter
import android.content.Intent.ACTION_BATTERY_CHANGED
import android.os.BatteryManager.BATTERY_PLUGGED_AC
import android.os.BatteryManager.BATTERY_PLUGGED_DOCK
import android.os.BatteryManager.BATTERY_PLUGGED_USB
import android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS
import android.os.BatteryManager.EXTRA_PLUGGED
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class ChargingStateUseCase {

    data class State(val cable: Boolean, val wireless: Boolean) {
        companion object {
            // A dock charges through a cable too
            @SuppressLint("InlinedApi")
            fun fromPlugged(plugged: Int) = State(
                cable = plugged and (BATTERY_PLUGGED_AC or BATTERY_PLUGGED_USB or BATTERY_PLUGGED_DOCK) != 0,
                wireless = plugged and BATTERY_PLUGGED_WIRELESS != 0,
            )
        }
    }

    private val filter = IntentFilter(ACTION_BATTERY_CHANGED)

    val state: Flow<State> = filter
        .asFlow()
        // Sticky broadcast: registering without a receiver returns the current value immediately,
        // instead of waiting for the receiver's first delivery
        .onStart { appContext.registerReceiver(null, filter)?.let { emit(it) } }
        .map { State.fromPlugged(it.getIntExtra(EXTRA_PLUGGED, 0)) }
        .distinctUntilChanged()
}
