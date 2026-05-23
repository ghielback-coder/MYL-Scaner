package com.mylescaner.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CardDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "MyLScanner.db"
        private const val TABLE_CARDS = "cards"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_COUNT = "count"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_CARDS_TABLE = ("CREATE TABLE " + TABLE_CARDS + "("
                + KEY_ID + " INTEGER PRIMARY KEY," + KEY_NAME + " TEXT,"
                + KEY_COUNT + " INTEGER" + ")")
        db.execSQL(CREATE_CARDS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CARDS")
        onCreate(db)
    }
}
