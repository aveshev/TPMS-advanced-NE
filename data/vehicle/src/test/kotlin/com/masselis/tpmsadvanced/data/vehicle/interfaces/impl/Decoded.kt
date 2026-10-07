package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.Tyre

/** Decodes an advertisement like the scanner and the database do */
internal fun ByteArray.decoded(): Raw? = AdvertisingPacket(this).decode()

/** The ID the scanner would give a sensor identified by its address, for the ones which are */
internal const val ADDRESS_SENSOR_ID = 1

@OptIn(ExperimentalStdlibApi::class)
internal fun ByteArray.decodedTyre(): Tyre.SensorInput? = decoded()
    ?.let { it.asTyre(0.0, -60, it.sensorId ?: ADDRESS_SENSOR_ID, toHexString()) }
