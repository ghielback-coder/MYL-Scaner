package com.mylescaner.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class MylCard(
    val code: String,
    val name: String,
    val type: String,
    val rarity: String,
    val race: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var resultText: TextView
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var lastDetectedText = ""
    private var lastDetectionTime = 0L
    private var cardList: List<MylCard> = emptyList()
    private var isDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        resultText = findViewById(R.id.resultText)
        cameraExecutor = Executors.newSingleThreadExecutor()

        cardList = loadCardsFromAssets()
        Toast.makeText(this, "Cargadas ${cardList.size} cartas", Toast.LENGTH_LONG).show()
        Log.d("MYL", "Cartas cargadas: ${cardList.size}")

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        findViewById<Button>(R.id.btnImport).setOnClickListener {
            Toast.makeText(this, "Base: ${cardList.size} cartas MyL", Toast.LENGTH_LONG).show()
        }
        
        findViewById<Button>(R.id.btnCollection).setOnClickListener {
            Toast.makeText(this, "Mi Colección pronto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCardsFromAssets(): List<MylCard> {
        return try {
            assets.open("cartas_myl.csv").bufferedReader().useLines { lines ->
                lines.drop(1) // Saltar header
                    .mapNotNull { line ->
                        val parts = line.split(",")
                        if (parts.size >= 5) {
                            Myl
