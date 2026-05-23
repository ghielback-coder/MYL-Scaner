package com.mylescaner.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class ColeccionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coleccion)

        db = AppDatabase.getDatabase(this)

        val recycler = findViewById<RecyclerView>(R.id.recyclerColeccion)
        recycler.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val cartas = db.cardDao().getAll()
            recycler.adapter = ColeccionAdapter(cartas)
            findViewById<TextView>(R.id.txtTotal).text = "Total: ${cartas.size} cartas"
        }

        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }
    }
}

class ColeccionAdapter(private val cartas: List<CardEntity>) :
    RecyclerView.Adapter<ColeccionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgFoto: ImageView = view.findViewById(R.id.imgFoto)
        val txtNombre: TextView = view.findViewById(R.id.txtNombre)
        val txtFecha: TextView = view.findViewById(R.id.txtFecha)
        val txtEdicion: TextView = view.findViewById(R.id.txtEdicion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
           .inflate(R.layout.item_carta_coleccion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val carta = cartas[position]
        holder.txtNombre.text = carta.nombreDetectado
        holder.txtFecha.text = android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", carta.fecha)
        holder.txtEdicion.text = carta.edicionSeleccionada?: "Sin edición asignada"

        Glide.with(holder.itemView.context)
           .load(carta.fotoUri)
           .into(holder.imgFoto)
    }

    override fun getItemCount() = cartas.size
    }
