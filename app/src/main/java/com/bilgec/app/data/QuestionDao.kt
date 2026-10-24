package com.bilgec.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface QuestionDao {
    @Query(
        """
        SELECT q.* FROM questions q
        LEFT JOIN question_progress p ON p.fact_key = q.fact_key
        WHERE p.fact_key IS NULL
          AND q.category IN (:categories)
          AND (:excludedCategory = '' OR q.category != :excludedCategory)
        GROUP BY q.fact_key
        ORDER BY RANDOM()
        LIMIT :limit
        """,
    )
    suspend fun unseenCandidates(
        excludedCategory: String,
        categories: List<String>,
        limit: Int,
    ): List<QuestionEntity>

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN question_progress p ON p.fact_key = q.fact_key
        WHERE q.category IN (:categories)
          AND (:excludedCategory = '' OR q.category != :excludedCategory)
          AND q.id = (SELECT MIN(q2.id) FROM questions q2 WHERE q2.fact_key = q.fact_key)
        ORDER BY
          CASE WHEN p.wrong_count > p.correct_count THEN 0 ELSE 1 END,
          (p.correct_count - p.wrong_count) ASC,
          p.last_seen_at ASC
        LIMIT :limit
        """,
    )
    suspend fun oldestCandidates(
        excludedCategory: String,
        categories: List<String>,
        limit: Int,
    ): List<QuestionEntity>

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN question_progress p ON p.fact_key = q.fact_key
        WHERE p.wrong_count > p.correct_count
          AND q.category IN (:categories)
          AND (:excludedCategory = '' OR q.category != :excludedCategory)
          AND q.id = (SELECT MIN(q2.id) FROM questions q2 WHERE q2.fact_key = q.fact_key)
        ORDER BY p.last_seen_at ASC
        LIMIT :limit
        """,
    )
    suspend fun reviewCandidates(
        excludedCategory: String,
        categories: List<String>,
        limit: Int,
    ): List<QuestionEntity>

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN question_progress p ON p.fact_key = q.fact_key
        WHERE p.wrong_count > p.correct_count
          AND q.id = (SELECT MIN(q2.id) FROM questions q2 WHERE q2.fact_key = q.fact_key)
        ORDER BY (p.wrong_count - p.correct_count) DESC, p.last_seen_at DESC
        LIMIT :limit
        """,
    )
    suspend fun weakQuestions(limit: Int): List<QuestionEntity>

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN question_progress p ON p.fact_key = q.fact_key
        WHERE p.is_favorite = 1
          AND q.id = (SELECT MIN(q2.id) FROM questions q2 WHERE q2.fact_key = q.fact_key)
        ORDER BY p.last_seen_at DESC
        LIMIT :limit
        """,
    )
    suspend fun favoriteQuestions(limit: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    suspend fun byId(id: Int): QuestionEntity?

    @Query("SELECT * FROM question_progress WHERE fact_key = :factKey LIMIT 1")
    suspend fun progress(factKey: String): QuestionProgress?

    @Upsert
    suspend fun saveProgress(progress: QuestionProgress)

    @Query("DELETE FROM question_progress")
    suspend fun clearProgress()

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun questionCount(): Int
}
