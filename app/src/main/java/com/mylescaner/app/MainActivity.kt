package com.mylescaner.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val txtCartas = findViewById<TextView>(R.id.txtCartasCargadas)
        val count = contarCartas()
        txtCartas.text = "Base: $count cartas MyL"

        findViewById<Button>(R.id.btnEscanear).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<Button>(R.id.btnColeccion).setOnClickListener {
            Toast.makeText(this, "Colección en desarrollo", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnExportar).setOnClickListener {
            Toast.makeText(this, "Exportar en desarrollo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun contarCartas(): Int {
        return try {
            assets.open("cartas_myl.csv").bufferedReader().useLines { lines ->
                lines.count() - 1
            }
        } catch (e: Exception) {
            0
        }
    }
}
