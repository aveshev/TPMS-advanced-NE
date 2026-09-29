package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Mock advertisements are debug-only, see the debug source set. Release builds only scan. */
@Suppress("UnusedParameter")
internal fun Flow<ScanResult>.withMockAdvertisements(
    context: Context,
    filters: List<ScanFilter>,
): Flow<ScanResult> = this
