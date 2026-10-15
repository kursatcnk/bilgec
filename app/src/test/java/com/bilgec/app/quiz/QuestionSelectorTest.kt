package com.bilgec.app.quiz

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionSelectorTest {
    @Test
    fun returnsOnlyCandidateWhenPoolHasOneQuestion() {
        val question = QuizQuestion(
            id = 1,
            category = "Bilim",
            prompt = "Suyun kimyasal formülü nedir?",
            options = listOf("H₂O", "CO₂", "O₂", "NaCl"),
            correctIndex = 0,
            explanation = "Su iki hidrojen ve bir oksijen atomundan oluşur.",
            factKey = "test:water",
        )

        assertEquals(question, QuestionSelector.pick(listOf(question), Random(1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyCandidatePool() {
        QuestionSelector.pick(emptyList(), Random(1))
    }
}
