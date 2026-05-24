package com.mylescaner.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ediciones")
data class EdicionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String
)
