package com.mylescaner.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cartas ORDER BY fechaRegistro DESC")
    fun getAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cartas WHERE nombreDetectado LIKE '%' || :search || '%' OR edicionSeleccionada LIKE '%' || :search || '%' OR numeroColeccionista LIKE '%' || :search || '%' ORDER BY fechaRegistro DESC")
    fun search(search: String): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(carta: CardEntity)

    @Update
    suspend fun update(carta: CardEntity)

    @Delete
    suspend fun delete(carta: CardEntity)

    @Query("SELECT * FROM cards WHERE fotoUri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): CardEntity?
}
