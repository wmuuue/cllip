package com.clipnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val deviceName: String,
    val ipAddress: String,
    val port: Int,
    val lastConnected: Long = System.currentTimeMillis()
)
