package com.bilgec.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QuestionEntity::class, QuestionProgress::class],
    version = 2,
    exportSchema = true,
)
abstract class BilGecDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao

    companion object {
        @Volatile
        private var instance: BilGecDatabase? = null

        fun get(context: Context): BilGecDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BilGecDatabase::class.java,
                "bilgec.db",
            )
                .createFromAsset("database/bilgec.db")
                .addMigrations(migration1To2)
                .build()
                .also { instance = it }
        }

        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE question_progress ADD COLUMN correct_count INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE question_progress ADD COLUMN wrong_count INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE question_progress ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0",
                )
                QuestionSeedV2.insert(db)
            }
        }
    }
}
