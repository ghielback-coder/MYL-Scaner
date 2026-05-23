package com.mylescaner.app

import androidx.room.*

@Dao
interface CardDao {
    @Query("SELECT * FROM coleccion ORDER BY fecha DESC")
    suspend fun getAll(): List<CardEntity>

    @Insert
    suspend fun insert(card: CardEntity)

    @Update
    suspend fun update(card: CardEntity)

    @Delete
    suspend fun delete(card: CardEntity)

    @Query("SELECT COUNT(*) FROM coleccion")
    suspend fun getCount(): Int
}
