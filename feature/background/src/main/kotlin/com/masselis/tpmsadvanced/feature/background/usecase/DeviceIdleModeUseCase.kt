package com.masselis.tpmsadvanced.feature.background.usecase

import android.content.IntentFilter
import android.os.PowerManager
import android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED
import androidx.core.content.getSystemService
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class DeviceIdleModeUseCase {
    private val powerManager = appContext.getSystemService<PowerManager>()!!

    val isDeviceIdle: Flow<Boolean> = IntentFilter(ACTION_DEVICE_IDLE_MODE_CHANGED)
        .asFlow()
        .map { powerManager.isDeviceIdleMode }
        .onStart { emit(powerManager.isDeviceIdleMode) }
}
