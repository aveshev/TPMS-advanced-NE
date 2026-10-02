package com.masselis.tpmsadvanced.feature.background.usecase

import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.SystemClock.elapsedRealtime
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Duration.Companion.milliseconds

/**
 * The system's significant motion sensor: a motion that may lead to a change of location, such as
 * walking or riding. A wake-up sensor running on the sensor hub, it costs nothing until it fires.
 */
internal class SignificantMotionUseCase {

    private val logger = Logger.withTag("SignificantMotion")

    private val sensorManager = appContext.getSystemService<SensorManager>()!!

    /** Emits on each significant motion. null when the phone has no such sensor. */
    val motions: Flow<Unit>? = sensorManager
        .getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        ?.let { sensor ->
            callbackFlow {
                // null until the first motion, which is timed from the start of listening instead
                var last: Long? = null
                val listeningSince = elapsedRealtime()
                val listener = object : TriggerEventListener() {
                    override fun onTrigger(event: TriggerEvent) {
                        elapsedRealtime().also { now ->
                            logger.i {
                                "Significant motion, " + (
                                    last?.let { "${(now - it).milliseconds} after the previous one" }
                                        ?: "${(now - listeningSince).milliseconds} after listening started"
                                    )
                            }
                            last = now
                        }
                        trySend(Unit)
                        // A trigger sensor disarms itself once it fired. Not re-armed once the
                        // collection ended, the listener would leak.
                        if (isClosedForSend.not()) sensorManager.requestTriggerSensor(this, sensor)
                    }
                }
                if (sensorManager.requestTriggerSensor(listener, sensor).not())
                    logger.w { "Cannot listen to the significant motion sensor" }
                awaitClose { sensorManager.cancelTriggerSensor(listener, sensor) }
            }
        }
}
