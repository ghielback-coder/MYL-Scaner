package com.mylescaner.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ColeccionActivity : AppCompatActivity() {

    private lateinit var adapter: CartasAdapter
    private lateinit var db: AppDatabase
    private var listaEdiciones = listOf<EdicionEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coleccion)

        db = AppDatabase.getDatabase(this)
        val recycler = findViewById<RecyclerView>(R.id.recyclerColeccion)
        val txtTotal = findViewById<TextView>(R.id.txtTotal)
        val edtBuscar = findViewById<EditText>(R.id.edtBuscar)

        adapter = CartasAdapter { carta, accion ->
            when (accion) {
                "editar" -> mostrarDialogoEditar(carta)
                "eliminar" -> confirmarEliminar(carta)
                "toggle" -> toggleColeccion(carta)
                "verImagen" -> abrirVisorImagen(carta.fotoUri)
            }
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }

        findViewById<com.google.android.material.f
