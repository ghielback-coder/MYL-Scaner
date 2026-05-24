package com.mylescaner.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards") // IMPORTANTE: Le pongo nombre fijo a la tabla
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombreDetectado: String,
    val fotoUri: String,
    val edicionSeleccionada: String,
    val numeroColeccionista: String?,
    val fechaRegistro: Long = System.currentTimeMillis(),
    val enColeccion: Boolean = true
)
