package com.masselis.tpmsadvanced.data.vehicle.model

/** What an alert is about. Each class of a sensor alerts on its own, see [AlertThresholds]. */
public enum class AlertClass {
    PRESSURE,
    TEMPERATURE,
    BATTERY,

    /** A tyre losing pressure while riding, see [PressureLoss.Tracker] */
    PRESSURE_LOSS,

    /** Raised by the sensor itself (Sysgration), see [Tyre.isAlarm] */
    SENSOR_ALARM,

    /** The sensor taken off the valve, see [TyreAlerts.isRemoved] */
    SENSOR_REMOVED,
}
