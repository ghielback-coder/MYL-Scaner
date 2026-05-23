package com.mylescaner.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)

        findViewById<Button>(R.id.btnEscanear).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<Button>(R.id.btnColeccion).setOnClickListener {
            startActivity(Intent(this, ColeccionActivity::class.java))
        }

        findViewById<Button>(R.id.btnExportar).setOnClickListener {
            Toast.makeText(this, "Exportar en Build #83", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarContadores()
    }

    private fun actualizarContadores() {
        val txtCartas = findViewById<TextView>(R.id.txtCartasCargadas)
        lifecycleScope.launch {
            val countBase = contarCartasBase()
            val countColeccion = db.cardDao().getCount()
            txtCartas.text = "Base: $countBase cartas | Colección: $countColeccion"
        }
    }

    private fun contarCartasBase(): Int {
        return try {
            assets.open("cartas_myl.csv").bufferedReader().useLines { lines ->
                lines.count() - 1
            }
        } catch (e: Exception) {
            0
        }
    }
}
