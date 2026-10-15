package com.bilgec.app.data

import com.bilgec.app.quiz.QuestionSelector
import com.bilgec.app.quiz.QuizQuestion

class QuestionRepository(private val dao: QuestionDao) {
    suspend fun next(lastCategory: String): QuizQuestion {
        val preferred = dao.unseenCandidates(lastCategory, candidateLimit)
        val unseen = if (preferred.isNotEmpty()) preferred else dao.unseenCandidates("", candidateLimit)
        val candidates = if (unseen.isNotEmpty()) {
            unseen
        } else {
            dao.oldestCandidates(lastCategory, candidateLimit).ifEmpty {
                dao.oldestCandidates("", candidateLimit)
            }
        }

        check(candidates.isNotEmpty()) { "Soru veritabanı boş." }
        return QuestionSelector.pick(candidates.map { it.toQuizQuestion() })
    }

    suspend fun byId(id: Int): QuizQuestion? = dao.byId(id)?.toQuizQuestion()

    suspend fun markSeen(question: QuizQuestion, now: Long = System.currentTimeMillis()) {
        val previous = dao.progress(question.factKey)
        dao.saveProgress(
            QuestionProgress(
                factKey = question.factKey,
                lastSeenAt = now,
                timesSeen = (previous?.timesSeen ?: 0) + 1,
            ),
        )
    }

    suspend fun clearProgress() = dao.clearProgress()

    suspend fun questionCount(): Int = dao.questionCount()

    companion object {
        private const val candidateLimit = 64
    }
}
