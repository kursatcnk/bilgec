package com.bilgec.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bilgec.app.quiz.QuizQuestion

@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["fact_key"]),
        Index(value = ["category"]),
    ],
)
data class QuestionEntity(
    @PrimaryKey val id: Int,
    val category: String,
    val prompt: String,
    @ColumnInfo(name = "option_0") val option0: String,
    @ColumnInfo(name = "option_1") val option1: String,
    @ColumnInfo(name = "option_2") val option2: String,
    @ColumnInfo(name = "option_3") val option3: String,
    @ColumnInfo(name = "correct_index") val correctIndex: Int,
    val explanation: String,
    @ColumnInfo(name = "fact_key") val factKey: String,
    @ColumnInfo(name = "image_asset") val imageAsset: String?,
    val reviewed: Boolean,
) {
    fun toQuizQuestion() = QuizQuestion(
        id = id,
        category = category,
        prompt = prompt,
        options = listOf(option0, option1, option2, option3),
        correctIndex = correctIndex,
        explanation = explanation,
        factKey = factKey,
        imageAsset = imageAsset,
        reviewed = reviewed,
    )
}
