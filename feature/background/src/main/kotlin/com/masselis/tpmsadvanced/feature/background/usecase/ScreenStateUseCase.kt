package com.masselis.tpmsadvanced.feature.background.usecase

import android.app.KeyguardManager
import android.content.Intent.ACTION_SCREEN_OFF
import android.content.Intent.ACTION_SCREEN_ON
import android.content.Intent.ACTION_USER_PRESENT
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.core.content.getSystemService
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class ScreenStateUseCase {
    private val powerManager = appContext.getSystemService<PowerManager>()!!
    private val keyguardManager = appContext.getSystemService<KeyguardManager>()!!

    /**
     * Whether someone uses the phone: screen on and unlocked. A screen lit on the lock screen, by
     * a notification or by lifting the phone, doesn't count, nor does an always-on display.
     */
    val isInUse: Flow<Boolean> = IntentFilter()
        .apply {
            addAction(ACTION_SCREEN_ON)
            addAction(ACTION_SCREEN_OFF)
            // Sent on unlocking, the screen being already on
            addAction(ACTION_USER_PRESENT)
        }
        // Protected system broadcasts, see asFlow()
        .asFlow(RECEIVER_EXPORTED)
        .map { isInUseNow() }
        .onStart { emit(isInUseNow()) }
        .distinctUntilChanged()

    private fun isInUseNow() = powerManager.isInteractive && keyguardManager.isKeyguardLocked.not()
}
