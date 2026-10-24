package com.bilgec.app.data

import com.bilgec.app.quiz.QuestionSelector
import com.bilgec.app.quiz.QuizQuestion
import com.bilgec.app.settings.AppPreferences
import kotlin.random.Random

class QuestionRepository(private val dao: QuestionDao) {
    suspend fun next(lastCategory: String, categories: Set<String>): QuizQuestion {
        val enabledCategories = categories.ifEmpty { AppPreferences.questionCategories.toSet() }.toList()
        val reviewCandidates = dao.reviewCandidates(lastCategory, enabledCategories, candidateLimit)
            .ifEmpty { dao.reviewCandidates("", enabledCategories, candidateLimit) }
        if (reviewCandidates.isNotEmpty() && Random.Default.nextInt(100) < reviewChancePercent) {
            return QuestionSelector.pick(reviewCandidates.map { it.toQuizQuestion() })
        }

        // Önce daha önce çıkmamış sorulara bakıyoruz.
        var candidates = dao.unseenCandidates(lastCategory, enabledCategories, candidateLimit)
        if (candidates.isEmpty()) {
            candidates = dao.unseenCandidates("", enabledCategories, candidateLimit)
        }

        // Hepsi görüldüyse en uzun zamandır gösterilmeyenlere dönüyoruz.
        if (candidates.isEmpty()) {
            candidates = dao.oldestCandidates(lastCategory, enabledCategories, candidateLimit)
        }
        if (candidates.isEmpty()) {
            candidates = dao.oldestCandidates("", enabledCategories, candidateLimit)
        }

        check(candidates.isNotEmpty()) { "Soru veritabanı boş." }
        return QuestionSelector.pick(candidates.map { it.toQuizQuestion() })
    }

    suspend fun byId(id: Int): QuizQuestion? = dao.byId(id)?.toQuizQuestion()

    suspend fun markSeen(
        question: QuizQuestion,
        correct: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        val previous = dao.progress(question.factKey)
        dao.saveProgress(
            QuestionProgress(
                factKey = question.factKey,
                lastSeenAt = now,
                timesSeen = (previous?.timesSeen ?: 0) + 1,
                correctCount = (previous?.correctCount ?: 0) + if (correct) 1 else 0,
                wrongCount = (previous?.wrongCount ?: 0) + if (correct) 0 else 1,
                isFavorite = previous?.isFavorite ?: false,
            ),
        )
    }

    suspend fun setFavorite(question: QuizQuestion, favorite: Boolean) {
        val previous = dao.progress(question.factKey)
        dao.saveProgress(
            QuestionProgress(
                factKey = question.factKey,
                lastSeenAt = previous?.lastSeenAt ?: 0L,
                timesSeen = previous?.timesSeen ?: 0,
                correctCount = previous?.correctCount ?: 0,
                wrongCount = previous?.wrongCount ?: 0,
                isFavorite = favorite,
            ),
        )
    }

    suspend fun isFavorite(factKey: String): Boolean = dao.progress(factKey)?.isFavorite == true

    suspend fun weakQuestions(): List<QuizQuestion> =
        dao.weakQuestions(reviewLimit).map { it.toQuizQuestion() }

    suspend fun favoriteQuestions(): List<QuizQuestion> =
        dao.favoriteQuestions(reviewLimit).map { it.toQuizQuestion() }

    suspend fun clearProgress() = dao.clearProgress()

    suspend fun questionCount(): Int = dao.questionCount()

    companion object {
        private const val candidateLimit = 64
        private const val reviewLimit = 20
        private const val reviewChancePercent = 35
    }
}
