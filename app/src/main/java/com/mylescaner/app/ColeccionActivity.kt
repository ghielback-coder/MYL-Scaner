package com.mylescaner.app

import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ColeccionActivity : AppCompatActivity() {

    private lateinit var adapter: CartasAdapter
    private lateinit var db: AppDatabase
    private var listaEdiciones = listOf<EdicionEntity>()

    private val seleccionarCarpetaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uriCarpeta = result.data?.data
            if (uriCarpeta!= null) {
                procesarCarpetaImagenes(uriCarpeta)
            }
        }
    }

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
        
        findViewById<Button>(R.id.btnImportarCarpeta).setOnClickListener {
            abrirSelectorCarpeta()
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAgregarEdicion)
          .setOnClickListener { mostrarDialogoNuevaEdicion() }

        lifecycleScope.launch {
            db.edicionDao().getAll().collectLatest { ediciones ->
                listaEdiciones = ediciones
                if (listaEdiciones.isEmpty()) {
                    val edicionesBase = listOf(
                        EdicionEntity(nombre = "Aguila Imperial", sigla = "AI"),
                        EdicionEntity(nombre = "Primer Bloque", sigla = "PB"),
                        EdicionEntity(nombre = "Libertadores", sigla = "LTD"),
                        EdicionEntity(nombre = "Tierra Austral", sigla = "TAS")
                    )
                    edicionesBase.forEach { db.edicionDao().insert(it) }
                }
            }
        }

        lifecycleScope.launch {
            db.cardDao().getAll().collectLatest { lista ->
                adapter.submitList(lista)
                val enColeccion = lista.count { it.enColeccion }
                val faltantes = lista.count {!it.enColeccion }
                txtTotal.text = "Total: ${lista.size} | Tengo: $enColeccion | Faltan: $faltantes"
            }
        }

        edtBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                lifecycleScope.launch {
                    if (query.isEmpty()) {
                        db.cardDao().getAll().collectLatest { adapter.submitList(it) }
                    } else {
                        db.cardDao().search(query).collectLatest { adapter.submitList(it) }
                    }
                }
            }
        })
    }

    private fun abrirSelectorCarpeta() {
        if (listaEdiciones.isEmpty()) {
            Toast.makeText(this, "Agrega al menos 1 edición primero con el botón +", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        seleccionarCarpetaLauncher.launch(intent)
    }

    private fun procesarCarpetaImagenes(uriCarpeta: Uri) {
        val dialogCargando = ProgressDialog(this)
        dialogCargando.setMessage("Escaneando carpeta...")
        dialogCargando.setCancelable(false)
        dialogCargando.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val contentResolver = applicationContext.contentResolver
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uriCarpeta,
                DocumentsContract.getTreeDocumentId(uriCarpeta)
            )

            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )

            val imagenes = mutableListOf<Uri>()
            cursor?.use {
                while (it.moveToNext()) {
                    val docId = it.getString(0)
                    val mimeType = it.getString(1)
                    if (mimeType.startsWith("image/")) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(uriCarpeta, docId)
                        imagenes.add(docUri)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                dialogCargando.dismiss()
                if (imagenes.isEmpty()) {
                    Toast.makeText(this@ColeccionActivity, "No hay imágenes en esa carpeta", Toast.LENGTH_SHORT).show()
                } else {
                    mostrarDialogoImportarMasivo(imagenes)
                }
            }
        }
    }

    private fun mostrarDialogoImportarMasivo(imagenes: List<Uri>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_guardar_carta, null)
        val spinnerEdicion = dialogView.findViewById<Spinner>(R.id.spinnerEdicion)
        val edtNumero = dialogView.findViewById<EditText>(R.id.edtNumero)
        val txtSigla = dialogView.findViewById<TextView>(R.id.txtSigla)
        edtNumero.hint = "Vacío o número inicial ej: 214"

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listaEdiciones.map { "${it.sigla} - ${it.nombre}" }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEdicion.adapter = adapter

        spinnerEdicion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                txtSigla.text = listaEdiciones[pos].sigla
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
          .setTitle("Importar ${imagenes.size} cartas")
          .setMessage("Elige la edición. El nombre se detectará automático.")
          .setView(dialogView)
          .
