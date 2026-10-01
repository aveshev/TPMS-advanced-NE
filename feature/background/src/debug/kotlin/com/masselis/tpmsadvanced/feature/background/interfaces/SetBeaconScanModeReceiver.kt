package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Mode

/**
 * Debug builds only: sets the beacon scan mode of the Debug page over adb, so that a script can
 * compare the modes without anyone touching the phone:
 * ```
 * adb shell am broadcast -a com.masselis.tpmsadvanced.SET_BEACON_SCAN_MODE -p com.masselis.tpmsadvanced \
 *     --es mode LOW_POWER_BATCHED_10
 * ```
 * `mode` is one of [Mode]'s names. Only a sender holding `android.permission.DUMP` reaches it, which
 * the adb shell does and regular apps can't.
 */
internal class SetBeaconScanModeReceiver : BroadcastReceiver() {

    private val logger = Logger.withTag("SetBeaconScanModeReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        intent
            .getStringExtra(EXTRA_MODE)
            ?.let { name -> Mode.entries.firstOrNull { it.name == name } }
            ?.also { Bindings.featureBackgroundInternal.appPreferences.beaconScanMode.value = it.name }
            ?.also { logger.i { "Beacon scan mode set to $it" } }
            ?: logger.w { "Unknown beacon scan mode \"${intent.getStringExtra(EXTRA_MODE)}\", expected one of ${Mode.entries}" }
    }

    private companion object {
        const val EXTRA_MODE = "mode"
    }
}
