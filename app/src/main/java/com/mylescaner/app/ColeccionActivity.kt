package com.mylescaner.app

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ColeccionActivity : AppCompatActivity() {

    private lateinit var adapter: CartasAdapter
    private lateinit var db: AppDatabase
    private var listaEdiciones = listOf<EdicionEntity>()
    private var searchJob: Job? = null
    private var queryActual = ""

    private val carpetaPendientes by lazy {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "MyLScanner/Pendientes"
        )
    }

    private val carpetaPrivada by lazy {
        File(getExternalFilesDir(null), "MyLScanner/Cartas")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coleccion)

        if (!carpetaPendientes.exists()) carpetaPendientes.mkdirs()
        if (!carpetaPrivada.exists()) carpetaPrivada.mkdirs()

        val nomedia = File(carpetaPrivada, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()

        db = AppDatabase.getDatabase(this)
        val recycler = findViewById<RecyclerView>(R.id.recyclerColeccion)
        val txtTotal = findViewById<TextView>(R.id.txtTotal)
        val edtBuscar = findViewById<EditText>(R.id.edtBuscar)
        val txtRutaPendientes = findViewById<TextView>(R.id.txtRutaPendientes)

        txtRutaPendientes.text = "Mete fotos en: ${carpetaPendientes.absolutePath}"

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

        findViewById<Button>(R.id.btnEscanearPendientes).setOnClickListener {
            escanearCarpetaPendientes()
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAgregarEdicion)
            .setOnClickListener { mostrarDialogoNuevaEdicion() }

        lifecycleScope.launch {
            db.edicion
