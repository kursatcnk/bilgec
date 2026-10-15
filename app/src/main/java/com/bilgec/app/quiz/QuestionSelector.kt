package com.bilgec.app.quiz

import kotlin.random.Random

object QuestionSelector {
    fun pick(candidates: List<QuizQuestion>, random: Random = Random.Default): QuizQuestion {
        require(candidates.isNotEmpty())
        return candidates[random.nextInt(candidates.size)]
    }
}
