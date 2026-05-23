package com.mylescaner.app

data class Card(
    val nombre: String,
    val codigo: String,
    val edicion: String
)

object CardDatabase {
    // Tierra Austral - agrega las que quieras
    val cartas = listOf(
        Card("Bandido Neira", "TAS-001", "Tierra Austral"),
        Card("Lautaro", "TAS-036", "Tierra Austral"),
        Card("Caupolicán", "TAS-042", "Tierra Austral"),
        Card("Galvarino", "TAS-055", "Tierra Austral"),
        Card("Fresia", "TAS-067", "Tierra Austral"),
        // Agrega más cartas TAS aquí...
        
        // Ejemplo si hay reprint en otra edición
        Card("Bandido Neira", "CDE-120", "Contraataque del Imperio"),
    )
    
    fun buscarPorNombre(nombre: String): List<Card> {
        val nombreLimpio = nombre.trim().lowercase()
        return cartas.filter { 
            it.nombre.lowercase().contains(nombreLimpio) || 
            nombreLimpio.contains(it.nombre.lowercase())
        }
    }
}
