package com.mylescaner.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEscanear = findViewById<Button>(R.id.btnEscanear)
        val btnColeccion = findViewById<Button>(R.id.btnColeccion)
        val txtStats = findViewById<TextView>(R.id.txtStats)

        btnEscanear.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        btnColeccion.setOnClickListener {
            startActivity(Intent(this, ColeccionActivity::class.java))
        }

        // FIX: Ahora usa Flow en vez de getCount()
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            db.cardDao().getAll().collectLatest { lista ->
                val tengo = lista.count { it.enColeccion }
                val faltan = lista.count { !it.enColeccion }
                txtStats.text = "Tengo: $tengo | Faltan: $faltan | Total: ${lista.size}"
            }
        }
    }
}
