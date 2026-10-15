package com.bilgec.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [QuestionEntity::class, QuestionProgress::class],
    version = 1,
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
                .build()
                .also { instance = it }
        }
    }
}
