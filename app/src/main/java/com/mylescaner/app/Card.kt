package com.mylescaner.app

data class Card(
    val codigo: String,
    val nombre: String,
    val edicion: String,
    val tipo: String = "-",
    val raza: String = "-"
)
