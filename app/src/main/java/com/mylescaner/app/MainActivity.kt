package com.mylescaner.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        textView.text = "MyL Scaner v1.0\n\nAPK compilado con éxito ✅\n\nSiguiente paso: agregar cámara + base de datos de cartas"
        textView.textSize = 18f
        textView.setPadding(50, 200, 50, 50)
        setContentView(textView)
    }
}
