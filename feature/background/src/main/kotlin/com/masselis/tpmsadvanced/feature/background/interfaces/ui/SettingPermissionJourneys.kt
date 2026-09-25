package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import androidx.compose.runtime.Composable

/**
 * Location must be "Allow all the time" (API 29+, where that option exists): that is the only way
 * the service can read the connected WiFi's name while the app is not visible, so it cannot be
 * granted from a system popup. "Use precise location" only exists from API 31.
 */
@Composable
internal fun rememberWifiExceptionJourney(
    permissions: List<String>,
    isSettingsOnScreen: () -> Boolean,
    onDenied: () -> Unit,
): PermissionJourney = rememberPermissionJourney(
    permissions = permissions,
    systemPopupFirst = false,
    rationale = "Identifying which WiFi network you're connected to — so it can be excepted from" +
            " the WiFi suspend setting — needs the location permission. In the app's settings," +
            when {
                SDK_INT >= S -> " open Permissions → Location, then select \"Allow all the time\"" +
                        " and turn on \"Use precise location\"."

                SDK_INT >= Q -> " open Permissions → Location, then select \"Allow all the time\"."
                else -> " open Permissions, then allow Location."
            } +
            if (SDK_INT >= Q) {
                " This is the only way the current WiFi name can be read while the app is in the" +
                        " background."
            } else "",
    disabledInfo = "The exception for certain WiFis has been turned off, because the location" +
            " permission it needs" +
            when {
                SDK_INT >= S -> " (\"Allow all the time\", with \"Use precise location\")"
                SDK_INT >= Q -> " (\"Allow all the time\")"
                else -> ""
            } +
            " was not granted. You can turn it on again in App settings, under Background scanning.",
    isSettingsOnScreen = isSettingsOnScreen,
    onDenied = onDenied,
)
