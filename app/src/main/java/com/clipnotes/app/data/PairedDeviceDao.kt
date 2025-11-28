package com.clipnotes.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {
    @Query("SELECT * FROM paired_devices ORDER BY lastConnected DESC")
    fun getAllPairedDevices(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun getDeviceById(deviceId: String): PairedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: PairedDeviceEntity)

    @Delete
    suspend fun deleteDevice(device: PairedDeviceEntity)

    @Query("UPDATE paired_devices SET lastConnected = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastConnected(deviceId: String, timestamp: Long)
}
