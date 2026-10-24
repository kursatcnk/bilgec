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
    @ColumnInfo(name = "correct_count", defaultValue = "0")
    val correctCount: Int = 0,
    @ColumnInfo(name = "wrong_count", defaultValue = "0")
    val wrongCount: Int = 0,
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,
)
