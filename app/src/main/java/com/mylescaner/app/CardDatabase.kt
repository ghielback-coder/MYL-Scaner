package com.mylescaner.app

object CardDatabase {
    private val cartas = mutableListOf<Card>()

    init {
        // Cartas base de Tierra Austral
        cartas.add(Card("TAS-001", "Bandido Neira", "Tierra Austral", "Aliado", "Bandido"))
        cartas.add(Card("TAS-036", "Lautaro", "Tierra Austral", "Aliado", "Héroe"))
        cartas.add(Card("TAS-050", "Galvarino", "Tierra Austral", "Aliado", "Héroe"))
        cartas.add(Card("TAS-076", "Caupolicán", "Tierra Austral", "Aliado", "Héroe"))
        cartas.add(Card("TAS-102", "Leftraru", "Tierra Austral", "Aliado", "Héroe"))
        // Agrega más cartas base acá si quieres
    }

    fun agregarCarta(carta: Card) {
        // Solo agrega si el código no existe, pa no duplicar
        if (!cartas.any { it.codigo == carta.codigo }) {
            cartas.add(carta)
        }
    }

    fun buscarPorNombre(nombre: String): List<Card> {
        return cartas.filter { it.nombre.contains(nombre, ignoreCase = true) }
    }

    fun todasLasCartas(): List<Card> = cartas.toList()
}
