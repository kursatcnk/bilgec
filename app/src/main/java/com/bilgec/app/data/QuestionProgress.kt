package com.bilgec.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "question_progress",
    indices = [Index(value = ["last_seen_at"])],
)
data class QuestionProgress(
    @PrimaryKey
    @ColumnInfo(name = "fact_key")
    val factKey: String,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long,
    @ColumnInfo(name = "times_seen")
    val timesSeen: Int,
)
