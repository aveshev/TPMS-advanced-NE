package com.masselis.tpmsadvanced.data.vehicle.model

/** How urgent an alert is, declared from the least to the most urgent so levels compare by it */
public enum class AlertLevel {
    /** Doesn't need immediate action, can be addressed when convenient */
    AMBER,

    /** Riding can go on with caution, to be addressed at the earliest possibility */
    RED,

    /** Must be fixed before riding on */
    CRIMSON,
}
