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

    // Carpeta de entrada y salida
    private val carpetaPendientes by lazy {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyLScanner/Pendientes")
    }
    private val carpetaImportadas by lazy {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyLScanner/Importadas")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coleccion)

        // Crear carpetas si no existen
        if (!carpetaPendientes.exists()) carpetaPendientes.mkdirs()
        if (!carpetaImportadas.exists()) carpetaImportadas.mkdirs()

        db = AppDatabase.getDatabase(this)
        val recycler = findViewById<RecyclerView>(R.id.recyclerColeccion)
        val txtTotal = findViewById<TextView>(R.id.txtTotal)
        val edtBuscar = findViewById<EditText>(R.id.edtBuscar)
        val txtRuta = findViewById<TextView>(R.id.txtRutaPendientes)

        txtRuta.text = "Mete fotos en: ${carpetaPendientes.absolutePath}"

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

        // Un solo Flow para todo
        lifecycleScope.launch {
            db.cardDao().getAll().collectLatest { lista ->
                val listaFiltrada = if (queryActual.isEmpty()) {
                    lista
                } else {
                    lista.filter {
                        it.nombreDetectado.contains(queryActual, ignoreCase = true) ||
                        it.edicionSeleccionada.contains(queryActual, ignoreCase = true) ||
                        it.numeroColeccionista?.contains(queryActual, ignoreCase = true) == true
                    }
                }
                adapter.submitList(listaFiltrada)
                val enColeccion = lista.count { it.enColeccion }
                val faltantes = lista.count {!it.enColeccion }
                txtTotal.text = "Total: ${lista.size} | Tengo: $enColeccion | Faltan: $faltantes"
            }
        }

        edtBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    queryActual = s.toString()
                    // El Flow de arriba se actualiza solo porque queryActual cambió
                }
            }
        })
    }

    private fun escanearCarpetaPendientes() {
        if (listaEdiciones.isEmpty()) {
            Toast.makeText(this, "Agrega al menos 1 edición primero con el botón +", Toast.LENGTH_LONG).show()
            return
        }

        val archivos = carpetaPendientes.listFiles { file ->
            file.isFile && (file.extension.equals("jpg", true) || 
                           file.extension.equals("jpeg", true) || 
                           file.extension.equals("png", true))
        }?: emptyArray()

        if (archivos.isEmpty()) {
            AlertDialog.Builder(this)
              .setTitle("Carpeta vacía")
              .setMessage("No hay fotos en:\n\n${carpetaPendientes.absolutePath}\n\nMete las fotos ahí y vuelve a escanear.")
              .setPositiveButton("OK", null)
              .show()
            return
        }

        val imagenes = archivos.map { Uri.fromFile(it) }
        mostrarDialogoImportarMasivo(imagenes)
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
          .setMessage("Las fotos se moverán a 'Importadas' después.\nEl nombre se detectará automático.")
          .setView(dialogView)
          .setPositiveButton("IMPORTAR Y MOVER") { _, _ ->
                val edicionSeleccionada = listaEdiciones[spinnerEdicion.selectedItemPosition]
                val numeroInicial = edtNumero.text.toString().toIntOrNull()
                importarYMover(imagenes, edicionSeleccionada, numeroInicial)
            }
          .setNegativeButton("CANCELAR", null)
          .show()
    }

    private fun importarYMover(imagenes: List<Uri>, edicion: EdicionEntity, numeroInicial: Int?) {
        val dialogProgreso = ProgressDialog(this)
        dialogProgreso.setMessage("Procesando 0/${imagenes.size}...")
        dialogProgreso.setCancelable(false)
        dialogProgreso.show()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var contador = 0
        var numeroActual = numeroInicial
        val archivosAMover = mutableListOf<File>()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@ColeccionActivity)

            for (uri in imagenes) {
                try {
                    val archivo = File(uri.path!!)
                    archivosAMover.add(archivo)

                    val image = InputImage.fromFilePath(this@ColeccionActivity, uri)
                    val visionText = Tasks.await(recognizer.process(image))

                    val imageWidth = visionText.textBlocks.maxOfOrNull { it.boundingBox?.right?: 0 }?: 1000
                    val leftZoneLimit = (imageWidth * 0.4).toInt()

                    val nombre = visionText.textBlocks
                      .filter { block ->
                            val box = block.boundingBox?: return@filter false
                            box.left < leftZoneLimit && box.right < leftZoneLimit
                        }
                      .flatMap { it.lines }
                      .map { it.text.trim() }
                      .filter { it.length > 2 &&!it.contains(" ") && it.any { c -> c.isLetter() } }
                      .maxByOrNull { it.length }?: "SinNombre_$contador"

                    val numeroCompleto = if (numeroActual!= null) "${edicion.sigla}-$numeroActual" else null
                    if (numeroActual!= null) numeroActual++

                    db.cardDao().insert(
                        CardEntity(
                            nombreDetectado = nombre,
                            fotoUri = uri.toString(),
                            edicionSeleccionada = edicion.nombre,
                            numeroColeccionista = numeroCompleto,
                            enColeccion = true
                        )
                    )
                } catch (e: Exception) {
                    Log.e("MYL", "Error procesando imagen $uri", e)
                }

                contador++
                withContext(Dispatchers.Main) {
                    dialogProgreso.setMessage("Procesando $contador/${imagenes.size}...")
                }
            }

            // MOVER ARCHIVOS DESPUÉS DE IMPORTAR
            archivosAMover.forEach { archivo ->
                try {
                    val destino = File(carpetaImportadas, archivo.name)
                    archivo.renameTo(destino)
                } catch (e: Exception) {
                    Log.e("MYL", "Error moviendo archivo ${archivo.name}", e)
                }
            }

            withContext(Dispatchers.Main) {
                dialogProgreso.dismiss()
                Toast.makeText(
                    this@ColeccionActivity, 
                    "Importadas: $contador cartas\nMovidas a: Importadas/", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun abrirVisorImagen(uriFoto: String) {
        val intent = Intent(this, VisorImagenActivity::class.java)
        intent.putExtra("URI", uriFoto)
        startActivity(intent)
    }

    private fun mostrarDialogoNuevaEdicion() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_nueva_edicion, null)
        val edtNombre = dialogView.findViewById<EditText>(R.id.edtNombreEdicion)
        val edtSigla = dialogView.findViewById<EditText>(R.id.edtSiglaEdicion)

        AlertDialog.Builder(this)
          .setTitle("Agregar Edición")
          .setView(dialogView)
          .setPositiveButton("AGREGAR") { _, _ ->
                val nombre = edtNombre.text.toString()
                val sigla = edtSigla.text.toString().uppercase()
                if (nombre.isNotEmpty() && sigla.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.edicionDao().insert(EdicionEntity(nombre = nombre, sigla = sigla))
                    }
                    Toast.makeText(this, "Edición agregada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Completa nombre y sigla", Toast.LENGTH_SHORT).show()
                }
            }
          .setNegativeButton("CANCELAR", null)
          .show()
    }

    private fun mostrarDialogoEditar(carta: CardEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_editar_carta, null)
        val edtNombre = dialogView.findViewById<EditText>(R.id.edtNombreEditar)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerEdicionEditar)
        val edtNumero = dialogView.findViewById<EditText>(R.id.edtNumeroEditar)

        edtNombre.setText(carta.nombreDetectado)

        val numeroSinSigla = carta.numeroColeccionista?.substringAfter("-")?: ""
        edtNumero.setText(numeroSinSigla)

        val adapterSpinner = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listaEdiciones.map { "${it.sigla} - ${it.nombre}" }
        )
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapterSpinner

        val indexEdicion = listaEdiciones.indexOfFirst { it.nombre == carta.edicionSeleccionada }
        if (indexEdicion!= -1) spinner.setSelection(indexEdicion)

        AlertDialog.Builder(this)
          .setTitle("Editar Carta")
          .setView(dialogView)
          .setPositiveButton("GUARDAR") { _, _ ->
                val edicionSeleccionada = listaEdiciones[spinner.selectedItemPosition]
                val numero = edtNumero.text.toString().ifEmpty { null }
                val numeroCompleto = if (numero!= null) "${edicionSeleccionada.sigla}-$numero" else null

                val nuevaCarta = carta.copy(
                    nombreDetectado = edtNombre.text.toString(),
                    edicionSeleccionada = edicionSeleccionada.nombre,
                    numeroColeccionista = numeroCompleto
                )
                lifecycleScope.launch(Dispatchers.IO) {
                    db.cardDao().update(nuevaCarta)
                }
                Toast.makeText(this, "Carta actualizada", Toast.LENGTH_SHORT).show()
            }
          .setNegativeButton("CANCELAR", null)
          .show()
    }

    private fun confirmarEliminar(carta: CardEntity) {
        AlertDialog.Builder(this)
          .setTitle("Eliminar Carta")
          .setMessage("¿Borrar ${carta.nombreDetectado} de tu colección?")
          .setPositiveButton("ELIMINAR") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.cardDao().delete(carta)
                }
                Toast.makeText(this, "Carta eliminada", Toast.LENGTH_SHORT).show()
            }
          .setNegativeButton("CANCELAR", null)
          .show()
    }

    private fun toggleColeccion(carta: CardEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.cardDao().update(carta.copy(enColeccion =!carta.enColeccion))
        }
    }

    inner class CartasAdapter(
        private val onAction: (CardEntity, String) -> Unit
    ) : ListAdapter<CardEntity, CartasAdapter.ViewHolder>(DiffCallback()) {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.imgFoto)
            val nombre: TextView = view.findViewById(R.id.txtNombre)
            val edicion: TextView = view.findViewById(R.id.txtEdicion)
            val numero: TextView = view.findViewById(R.id.txtNumero)
            val fecha: TextView = view.findViewById(R.id.txtFecha)
            val switch: Switch = view.findViewById(R.id.switchColeccion)

            init {
                view.setOnLongClickListener {
                    val carta = getItem(adapterPosition)
                    AlertDialog.Builder(itemView.context)
                      .setTitle(carta.nombreDetectado)
                      .setItems(arrayOf("Editar", "Eliminar")) { _, which ->
                            if (which == 0) onAction(carta, "editar") else onAction(carta, "eliminar")
                        }
                      .show()
                    true
                }

                img.setOnClickListener {
                    val carta = getItem(adapterPosition)
                    onAction(carta, "verImagen")
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
              .inflate(R.layout.item_carta_coleccion, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val carta = getItem(position)
            holder.nombre.text = carta.nombreDetectado
            holder.edicion.text = "Edición: ${carta.edicionSeleccionada}"
            holder.numero.text = "N°: ${carta.numeroColeccionista?: "---"}"
            holder.fecha.text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
              .format(Date(carta.fechaRegistro))

            holder.switch.setOnCheckedChangeListener(null)
            holder.switch.isChecked = carta.enColeccion
            holder.switch.text = if (carta.enColeccion) "Tengo" else "Falta"
            holder.switch.setOnCheckedChangeListener { _, _ -> onAction(carta, "toggle") }

            Glide.with(holder.itemView.context)
              .load(carta.fotoUri)
              .placeholder(android.R.drawable.ic_menu_gallery)
              .into(holder.img)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CardEntity>() {
        override fun areItemsTheSame(oldItem: CardEntity, newItem: CardEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CardEntity, newItem: CardEntity): Boolean {
            return oldItem == newItem
        }
    }
}
