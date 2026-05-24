package com.mylescaner.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CardEntity::class, EdicionEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun edicionDao(): EdicionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "myl_scanner_db"
                )
                .fallbackToDestructiveMigration() // Borra BD si cambia versión
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
