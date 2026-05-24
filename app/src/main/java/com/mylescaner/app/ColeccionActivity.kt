package com.mylescaner.app

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
    private var listaEdiciones = listOf<String>()

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
            }
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAgregarEdicion)
           .setOnClickListener { mostrarDialogoNuevaEdicion() }

        // Cargar ediciones
        lifecycleScope.launch {
            db.edicionDao().getAll().collectLatest { ediciones ->
                listaEdiciones = ediciones.map { it.nombre }
                if (listaEdiciones.isEmpty()) {
                    // Primera vez: insertar ediciones base
                    val edicionesBase = listOf(
                        "Aguila Imperial", "Primer Bloque", "Espada Sagrada", "Otras"
                    )
                    edicionesBase.forEach { db.edicionDao().insert(EdicionEntity(nombre = it)) }
                }
            }
        }

        // Cargar cartas
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

    private fun mostrarDialogoNuevaEdicion() {
        val input = EditText(this)
        input.hint = "Nombre de la nueva edición"
        AlertDialog.Builder(this)
           .setTitle("Agregar Edición")
           .setView(input)
           .setPositiveButton("AGREGAR") { _, _ ->
                val nombre = input.text.toString()
                if (nombre.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.edicionDao().insert(EdicionEntity(nombre = nombre))
                    }
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
        edtNumero.setText(carta.numeroColeccionista)

        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_item, listaEdiciones)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapterSpinner
        spinner.setSelection(listaEdiciones.indexOf(carta.edicionSeleccionada))

        AlertDialog.Builder(this)
           .setTitle("Editar Carta")
           .setView(dialogView)
           .setPositiveButton("GUARDAR") { _, _ ->
                val nuevaCarta = carta.copy(
                    nombreDetectado = edtNombre.text.toString(),
                    edicionSeleccionada = spinner.selectedItem.toString(),
                    numeroColeccionista = edtNumero.text.toString().ifEmpty { null }
                )
                lifecycleScope.launch(Dispatchers.IO) {
                    db.cardDao().update(nuevaCarta)
                }
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
    ) : RecyclerView.Adapter<CartasAdapter.ViewHolder>() {

        private var lista = listOf<CardEntity>()

        fun submitList(nuevaLista: List<CardEntity>) {
            lista = nuevaLista
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.imgFoto)
            val nombre: TextView = view.findViewById(R.id.txtNombre)
            val edicion: TextView = view.findViewById(R.id.txtEdicion)
            val numero: TextView = view.findViewById(R.id.txtNumero)
            val fecha: TextView = view.findViewById(R.id.txtFecha)
            val switch: Switch = view.findViewById(R.id.switchColeccion)

            init {
                view.setOnLongClickListener {
                    val carta = lista[adapterPosition]
                    AlertDialog.Builder(itemView.context)
                       .setTitle(carta.nombreDetectado)
                       .setItems(arrayOf("Editar", "Eliminar")) { _, which ->
                            if (which == 0) onAction(carta, "editar") else onAction(carta, "eliminar")
                        }
                       .show()
                    true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
               .inflate(R.layout.item_carta_coleccion, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val carta = lista[position]
            holder.nombre.text = carta.nombreDetectado
            holder.edicion.text = "Edición: ${carta.edicionSeleccionada}"
            holder.numero.text = "N°: ${carta.numeroColeccionista?: "---"}"
            holder.fecha.text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
               .format(Date(carta.fechaRegistro))

            holder.switch.isChecked = carta.enColeccion
            holder.switch.text = if (carta.enColeccion) "Tengo" else "Falta"
            holder.switch.setOnCheckedChangeListener { _, _ -> onAction(carta, "toggle") }

            Glide.with(holder.itemView.context)
               .load(carta.fotoUri)
               .into(holder.img)
               holder.img.setOnClickListener {
    val intent = Intent(holder.itemView.context, VisorImagenActivity::class.java)
    intent.putExtra("URI", carta.fotoUri)
    holder.itemView.context.startActivity(intent)
               }
        }

        override fun getItemCount() = lista.size
    }
}
