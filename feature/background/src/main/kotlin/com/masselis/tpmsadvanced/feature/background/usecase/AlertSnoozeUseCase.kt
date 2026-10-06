package com.masselis.tpmsadvanced.feature.background.usecase

import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import kotlin.time.Duration

/**
 * The alerts dismissed for a while, by sensor and class, see docs/alerts.md. Kept on disk: the
 * monitor service gets killed and restarted, which would bring every dismissed alert back.
 */
internal class AlertSnoozeUseCase(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preferences = appContext.getSharedPreferences("ALERT_SNOOZES", MODE_PRIVATE)

    /** Whether [level], and so every lower level, of [alertClass] is dismissed for [sensorId] */
    fun isSnoozed(sensorId: Int, alertClass: AlertClass, level: AlertLevel): Boolean =
        preferences.getLong(key(sensorId, alertClass, level), 0L) > clock()

    /**
     * Dismisses [level] and the lower levels of [alertClass] for [duration], unless they already
     * are for longer. A higher level still alerts.
     */
    fun snooze(sensorId: Int, alertClass: AlertClass, level: AlertLevel, duration: Duration) {
        val now = clock()
        preferences.edit(commit = true) {
            // The ended ones would pile up with each sensor ever bound
            preferences
                .all
                .filterValues { (it as? Long ?: 0L) <= now }
                .keys
                .forEach(::remove)
            AlertLevel
                .entries
                .filter { it <= level }
                .map { key(sensorId, alertClass, it) }
                .forEach { key ->
                    putLong(key, maxOf(preferences.getLong(key, 0L), now + duration.inWholeMilliseconds))
                }
        }
    }

    private fun key(sensorId: Int, alertClass: AlertClass, level: AlertLevel) =
        "$sensorId/${alertClass.name}/${level.name}"
}
