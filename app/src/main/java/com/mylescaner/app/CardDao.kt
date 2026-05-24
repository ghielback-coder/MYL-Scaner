package com.mylescaner.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY fechaRegistro DESC")
    fun getAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY fechaRegistro DESC")
    suspend fun getAllSync(): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CardEntity): Long

    @Update
    suspend fun update(card: CardEntity)

    @Delete
    suspend fun delete(card: CardEntity)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Int): CardEntity?
}
