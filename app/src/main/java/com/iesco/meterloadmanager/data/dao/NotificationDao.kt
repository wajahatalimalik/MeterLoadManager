package com.iesco.meterloadmanager.data.dao

import androidx.room.*
import com.iesco.meterloadmanager.data.entity.AppNotification
import com.iesco.meterloadmanager.data.entity.NotificationStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AppNotification>>

    @Query("SELECT * FROM notifications WHERE status = 'UNREAD' ORDER BY timestamp DESC")
    fun getUnread(): Flow<List<AppNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(n: AppNotification): Long

    @Update
    suspend fun update(n: AppNotification)

    @Delete
    suspend fun delete(n: AppNotification)

    @Query("UPDATE notifications SET status = 'READ' WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notifications SET status = 'DISMISSED' WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE notifications SET status = 'SNOOZED', snoozeUntil = :until WHERE id = :id")
    suspend fun snooze(id: Long, until: Long)

    @Query("DELETE FROM notifications WHERE status = 'DISMISSED'")
    suspend fun clearDismissed()

    @Query("SELECT COUNT(*) FROM notifications WHERE status = 'UNREAD'")
    fun unreadCount(): Flow<Int>
}
