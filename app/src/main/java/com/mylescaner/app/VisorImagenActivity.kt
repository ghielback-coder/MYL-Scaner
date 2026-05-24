package com.mylescaner.app

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class VisorImagenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visor_imagen)

        val uri = intent.getStringExtra("URI")
        val img = findViewById<ImageView>(R.id.imgGrande)

        Glide.with(this).load(uri).into(img)

        findViewById<ImageButton>(R.id.btnCerrar).setOnClickListener { finish() }
    }
}
