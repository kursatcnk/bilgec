package com.bilgec.app.quiz

object SampleQuestions {
    val phaseZero = QuizQuestion(
        category = "Coğrafya",
        prompt = "Türkiye'nin başkenti hangisidir?",
        options = listOf("İstanbul", "Ankara", "İzmir", "Bursa"),
        correctIndex = 1,
        explanation = "Türkiye'nin başkenti 13 Ekim 1923'te Ankara olarak ilan edildi.",
        factKey = "capital:TR",
    )
}

