package com.leoai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.leoai.app.data.ChatMessageDao
import com.leoai.app.data.local.dao.ChatDao
import com.leoai.app.data.local.entity.ChatMessageEntity
import com.leoai.app.data.local.entity.ChatSessionEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LeoDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private const val DATABASE_NAME = "leo_ai_room.db"

        @Volatile
        private var instance: LeoDatabase? = null

        fun getInstance(context: Context): LeoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LeoDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
