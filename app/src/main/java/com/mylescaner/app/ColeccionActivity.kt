package com.mylescaner.app

import android.app.ProgressDialog
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ColeccionActivity : AppCompatActivity() {

    private lateinit var adapter: CartasAdapter
    private lateinit var db: AppDatabase
    private var listaEdiciones = listOf<EdicionEntity>()
    private var searchJob: Job? = null
    private var queryActual = ""
    private var filtroEdicion: String? = null

    private var actionMode: ActionMode? = null
    private val seleccionadas = mutableSetOf<CardEntity>()

    private val carpetaPendientes by lazy {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyLScanner/Pendientes")
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
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupEdiciones)

        txtRutaPendientes.text = "Mete fotos en: ${carpetaPendientes.absolutePath}"

        adapter = CartasAdapter(
            onAction = { carta, accion ->
                when (accion) {
                    "editar" -> mostrarDialogoEditar(carta)
                    "eliminar" -> confirmarEliminar(carta)
                    "toggle" -> toggleColeccion(carta)
                    "verImagen" -> abrirVisorImagen(carta.fotoUri)
                    "longClick" -> activarModoSeleccion(carta)
                    "click" -> {
                        if (actionMode!= null) toggleSeleccion(carta)
                        else abrirVisorImagen(carta.fotoUri)
                    }
                }
            },
            getSeleccionadas = { seleccionadas }
        )

        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnEscanearPendientes).setOnClickListener { escanearCarpetaPendientes() }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAgregarEdicion)
          .setOnClickListener { mostrarDialogoNuevaEdicion() }

        lifecycleScope.launch {
            db.edicionDao().getAll().collectLatest { ediciones ->
                listaEdiciones = ediciones
                actualizarChipsEdiciones(chipGroup)
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
                aplicarFiltros(lista)
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
                    val lista = db.cardDao().getAllSync()
                    aplicarFiltros(lista)
                }
            }
        })
    }

    private fun actualizarChipsEdiciones(chipGroup: ChipGroup) {
        chipGroup.removeAllViews()

        val chipTodas = Chip(this).apply {
            text = "Todas"
            isCheckable = true
            isChecked = filtroEdicion == null
            setOnClickListener {
                filtroEdicion = null
                lifecycleScope.launch {
                    val lista = db.cardDao().getAllSync()
                    aplicarFiltros(lista)
                }
            }
        }
        chipGroup.addView(chipTodas)

        listaEdiciones.forEach { edicion ->
            val chip = Chip(this).apply {
                text = "${edicion.sigla} - ${edicion.nombre}"
                isCheckable = true
                isChecked = filtroEdicion == edicion.nombre
                setOnClickListener {
                    filtroEdicion = if (isChecked) edicion.nombre else null
                    lifecycleScope.launch {
                        val lista = db.cardDao().getAllSync()
                        aplicarFiltros(lista)
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun aplicarFiltros(lista: List<CardEntity>) {
        var listaFiltrada = lista

        if (filtroEdicion!= null) {
            listaFiltrada = listaFiltrada.filter { it.edicionSeleccionada == filtroEdicion }
        }

        if (queryActual.isNotEmpty()) {
            listaFiltrada = listaFiltrada.filter {
                it.nombreDetectado.contains(queryActual, ignoreCase = true) ||
                it.edicionSeleccionada.contains(queryActual, ignoreCase = true) ||
                it.numeroColeccionista?.contains(queryActual, ignoreCase = true) == true
            }
        }

        adapter.submitList(listaFiltrada)
        val enColeccion = lista.count { it.enColeccion }
        val faltantes = lista.count {!it.enColeccion }
        findViewById<TextView>(R.id.txtTotal).text = "Total: ${lista.size} | Tengo: $enColeccion | Faltan: $faltantes"
    }

    private fun activarModoSeleccion(carta: CardEntity) {
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback)
        }
        toggleSeleccion(carta)
    }

    private fun toggleSeleccion(carta: CardEntity) {
        if (seleccionadas.contains(carta)) {
            seleccionadas.remove(carta)
        } else {
            seleccionadas.add(carta)
        }
        actionMode?.title = "${seleccionadas.size} seleccionadas"
        adapter.notifyDataSetChanged()

        if (seleccionadas.isEmpty()) {
            actionMode?.finish()
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.menu_seleccion_multiple, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item?.itemId) {
                R.id.action_eliminar -> confirmarEliminarMultiple()
                R.id.action_mover_edicion -> mostrarDialogoMoverEdicion()
                R.id.action_marcar_tengo -> marcarSeleccionadas(true)
                R.id.action_marcar_falta -> marcarSeleccionadas(false)
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            seleccionadas.clear()
            adapter.notifyDataSetChanged()
        }
    }

    private fun confirmarEliminarMultiple() {
        AlertDialog.Builder(this)
          .setTitle("Eliminar ${seleccionadas.size} cartas")
          .setMessage("¿Borrar estas cartas de tu colección?\n\nLas fotos también se eliminarán.")
          .setPositiveButton("ELIMINAR") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    seleccionadas.forEach { carta ->
                        try {
                            File(Uri.parse(carta.fotoUri).path!!).delete()
                        } catch (e: Exception) {
                            Log.e("MYL", "Error borrando archivo", e)
                        }
                        db.cardDao().delete(carta)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ColeccionActivity, "${seleccionadas.size} cartas eliminadas", Toast.LENGTH_SHORT).show()
                        actionMode?.finish()
                    }
                }
            }
          .setNegativeButton("CANCELAR", null)
          .show()
    }

    private fun mostrarDialogoMoverEdicion() {
        val ediciones = listaEdiciones.map { "${it.sigla} - ${it.nombre}" }.toTypedArray()
        AlertDialog.Builder(this)
          .setTitle("Mover a edición")
          .setItems(ediciones) { _, which ->
                val edicionSeleccionada = listaEdiciones[which]
                lifecycleScope.launch(Dispatchers.IO) {
                    seleccionadas.forEach { carta ->
                        db.cardDao().update(carta.copy(edicionSeleccionada = edicionSeleccionada.nombre))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ColeccionActivity, "${seleccionadas.size} cartas movidas a ${edicionSeleccionada.sigla}", Toast.LENGTH_SHORT).show()
                        actionMode?.finish()
                    }
                }
            }
          .setNegativeButton("CANCELAR", null)
          .show()
    }

    private fun marcarSeleccionadas(tengo: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            seleccionadas.forEach { carta ->
                db.cardDao().update(carta.copy(enColeccion = tengo))
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ColeccionActivity, "${seleccionadas.size} cartas actualizadas", Toast.LENGTH_SHORT).show()
                actionMode?.finish()
            }
        }
    }

    private fun escanearCarpetaPendientes() {
        if (listaEdiciones.isEmpty()) {
            Toast.makeText(this, "Agrega al menos 1 edición primero con el botón +", Toast.LENGTH_LONG).show()
            return
        }

        val imagenes = obtenerImagenesDePendientes()

        if (imagenes.isEmpty()) {
            AlertDialog.Builder(this)
              .setTitle("Carpeta vacía")
              .setMessage("No hay fotos en:\n\n${carpetaPendientes.absolutePath}\n\nMete las fotos ahí con cualquier explorador de archivos y vuelve a escanear.")
              .setPositiveButton("OK", null)
              .show()
            return
        }

        mostrarDialogoImportarMasivo(imagenes)
    }

    private fun obtenerImagenesDePendientes(): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DATA} LIKE?"
        val selectionArgs = arrayOf("${carpetaPendientes.absolutePath}%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                uris.add(contentUri)
            }
        }
        return uris
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
          .setMessage("Las fotos se copiarán a carpeta privada invisible.\nEl nombre se detectará automático.")
          .setView(dialogView)
          .setPositiveButton("IMPORTAR") { _, _ ->
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
        var procesadas = 0
        var exitosas = 0
        var fallidas = 0
        var numeroActual = numeroInicial

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@ColeccionActivity)

            for (uri in imagenes) {
                var insercionExitosa = false
                try {
                    val nuevoNombre = "MyL_${System.currentTimeMillis()}_${procesadas}.jpg"
                    val archivoDestino = File(carpetaPrivada, nuevoNombre)

                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(archivoDestino).use { output ->
                            input.copyTo(output)
                        }
                    }?: throw Exception("No se pudo abrir InputStream")

                    val uriNueva = Uri.fromFile(archivoDestino)

                    val image = InputImage.fromFilePath(this@ColeccionActivity, uriNueva)
                    val visionText = Tasks.await(recognizer.process(image))

                    val imageWidth = visionText.textBlocks.maxOfOrNull { it.boundingBox?.right?: 0 }?: 1000
                    val leftZoneLimit = (imageWidth * 0.4).toInt()

                    val nombre = visionText.textBlocks
                      .filter { block ->
                            val box = block.boundingBox
                            if (box == null) false else box.left < leftZoneLimit && box.right < leftZoneLimit
                        }
                      .flatMap { it.lines }
                      .map { it.text.trim() }
                      .filter { it.length > 2 &&!it.contains(" ") && it.any { c -> c.isLetter() } }
                      .maxByOrNull { it.length }?: "SinNombre_$procesadas"

                    val numeroCompleto = if (numeroActual!= null) "${edicion.sigla}-$numeroActual" else null
                    if (numeroActual!= null) numeroActual++

                    val id = db.cardDao().insert(
                        CardEntity(
                            nombreDetectado = nombre,
                            fotoUri = uriNueva.toString(),
                            edicionSeleccionada = edicion.nombre,
                            numeroColeccionista = numeroCompleto,
                            enColeccion = true
                        )
                    )

                    Log.d("MYL", "Insertada carta ID: $id - Nombre: $nombre")
                    insercionExitosa = true

                    try {
                        contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        Log.w("MYL", "No se pudo borrar original, pero se importó", e)
                    }

                } catch (e: Exception) {
                    Log.e("MYL", "Error procesando imagen $uri", e)
                }

                procesadas++
                if (insercionExitosa) exitosas++ else fallidas++

                withContext(Dispatchers.Main) {
                    dialogProgreso.setMessage("Procesando $procesadas/${imagenes.size}...")
                }
            }

            withContext(Dispatchers.Main) {
                dialogProgreso.dismiss()
                val mensaje = if (fallidas > 0) {
                    "Importadas: $exitosas\nFallidas: $fallidas"
                } else {
                    "Importadas: $exitosas cartas\nMovidas a carpeta privada"
                }
                Toast.makeText(this@ColeccionActivity, mensaje, Toast.LENGTH_LONG).show()
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
          .setMessage("¿Borrar ${carta.nombreDetectado} de tu colección?\n\nLa foto también se eliminará.")
          .setPositiveButton("ELIMINAR") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        File(Uri.parse(carta.fotoUri).path!!).delete()
                    } catch (e: Exception) {
                        Log.e("MYL", "Error borrando archivo", e)
                    }
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
        private val onAction: (CardEntity, String) -> Unit,
        private val getSeleccionadas: () -> Set<CardEntity>
    ) : ListAdapter<CardEntity, CartasAdapter.ViewHolder>(DiffCallback()) {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.imgFoto)
            val nombre: TextView = view.findViewById(R.id.txtNombre)
            val edicion: TextView = view.findViewById(R.id.txtEdicion)
            val numero: TextView = view.findViewById(R.id.txtNumero)
            val fecha: TextView = view.findViewById(R.id.txtFecha)
            val switch: Switch = view.findViewById(R.id.switchColeccion)
            val checkbox: CheckBox = view.findViewById(R.id.checkboxSeleccion)
            val cardView: androidx.cardview.widget.CardView = view.findViewById(R.id.cardView)

            init {
                view.setOnLongClickListener {
                    val carta = getItem(adapterPosition)
                    onAction(carta, "longClick")
                    true
                }

                view.setOnClickListener {
                    val carta = getItem(adapterPosition)
                    onAction(carta, "click")
                }

                checkbox.setOnClickListener {
                    val carta = getItem(adapterPosition)
                    onAction(carta, "click")
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
            val estaSeleccionada = getSeleccionadas().contains(carta)
            val modoSeleccion = getSeleccionadas().isNotEmpty()

            holder.nombre.text = carta.nombreDetectado
            holder.edicion.text = "Edición: ${carta.edicionSeleccionada}"
            holder.numero.text = "N°: ${carta.numeroColeccionista?: "---"}"
            holder.fecha.text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
              .format(Date(carta.fechaRegistro))

            holder.switch.setOnCheckedChangeListener(null)
            holder.switch.isChecked = carta.enColeccion
            holder.switch.text = if (carta.enColeccion) "Tengo" else "Falta"
            holder.switch.setOnCheckedChangeListener { _, _ -> onAction(carta, "toggle") }

            holder.checkbox.visibility = if (modoSeleccion) View.VISIBLE else View.GONE
            holder.checkbox.isChecked = estaSeleccionada
            holder.cardView.strokeWidth = if (estaSeleccionada) 6 else 0
            holder.cardView.strokeColor = if (estaSeleccionada) 0xFF6200EE.toInt() else 0x00000000

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
