package com.mylescaner.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cartas")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombreDetectado: String,
    val fotoUri: String,
    val fechaRegistro: Long = System.currentTimeMillis(),
    val edicionSeleccionada: String = "Sin edición",
    val numeroColeccionista: String? = null,
    val enColeccion: Boolean = true // NUEVO: true = la tengo, false = faltante
)
