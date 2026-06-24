package com.iesco.meterloadmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NotificationCategory { INFORMATION, WATCH, RECOMMENDATION, WARNING, CRITICAL }
enum class NotificationStatus   { UNREAD, READ, SNOOZED, DISMISSED }

@Entity(tableName = "notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val category: NotificationCategory,
    val status: NotificationStatus = NotificationStatus.UNREAD,
    val meterNumber: String? = null,       // which meter triggered this
    val timestamp: Long = System.currentTimeMillis(),
    val snoozeUntil: Long? = null,
    val isEnabled: Boolean = true
)
