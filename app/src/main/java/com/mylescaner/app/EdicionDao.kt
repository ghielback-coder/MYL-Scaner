package com.mylescaner.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EdicionDao {
    @Query("SELECT * FROM ediciones ORDER BY nombre ASC")
    fun getAll(): Flow<List<EdicionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(edicion: EdicionEntity)

    @Update
    suspend fun update(edicion: EdicionEntity) // ← ESTE ES EL "AGREGAR UPDATE"

    @Delete
    suspend fun delete(edicion: EdicionEntity)
}
