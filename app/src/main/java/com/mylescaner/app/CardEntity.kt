package com.mylescaner.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coleccion")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombreDetectado: String,
    val fotoUri: String,
    val fecha: Long = System.currentTimeMillis(),
    val codigoEdicion: String? = null,
    val edicionSeleccionada: String? = null
)
