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
          AND (:excludedCategory = '' OR q.category != :excludedCategory)
        ORDER BY q.id
        LIMIT :limit
        """,
    )
    suspend fun unseenCandidates(excludedCategory: String, limit: Int): List<QuestionEntity>

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN question_progress p ON p.fact_key = q.fact_key
        WHERE (:excludedCategory = '' OR q.category != :excludedCategory)
          AND q.id = (SELECT MIN(q2.id) FROM questions q2 WHERE q2.fact_key = q.fact_key)
        ORDER BY p.last_seen_at ASC
        LIMIT :limit
        """,
    )
    suspend fun oldestCandidates(excludedCategory: String, limit: Int): List<QuestionEntity>

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
