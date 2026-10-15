package com.bilgec.app.quiz

data class QuizQuestion(
    val category: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val factKey: String,
)

