package com.bilgec.app.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleQuestionsTest {
    @Test
    fun phaseZeroQuestionHasFourOptionsAndValidAnswer() {
        val question = SampleQuestions.phaseZero

        assertEquals(4, question.options.size)
        assertTrue(question.correctIndex in question.options.indices)
        assertEquals("capital:TR", question.factKey)
    }
}

