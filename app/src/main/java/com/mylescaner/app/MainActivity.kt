package com.mylescaner.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var isPaused = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var resumeRunnable: Runnable? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var resultText: TextView
    private lateinit var debugText: TextView
    private lateinit var cardListLayout: LinearLayout
    private val scannedCards = mutableSetOf<String>()
    private var lastDetectedName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        previewView = PreviewView(this)
        
        resultText = TextView(this).apply {
            text = "Apunta al nombre de la carta"
            textSize = 20f
            setPadding(32, 32, 32, 16)
        }
        
        // Lista de cartas encontradas
        cardListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 16)
        }
        
        debugText = TextView(this).apply {
            text = "Debug OCR: esperando..."
            textSize = 11f
            setPadding(32, 0, 32, 32)
            setTextColor(0xFF888888.toInt())
        }
        
        val scrollView = ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(debugText)
                addView(cardListLayout)
            })
        }
        
        layout.addView(previewView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        layout.addView(resultText)
        layout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        setContentView(layout)
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, TextAnalyzer())
                }
            
            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
                camera.cameraControl.setZoomRatio(1.5f) // Zoom para leer mejor
            } catch(exc: Exception) {
                Log.e("Camera", "Error", exc)
            }
            
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class TextAnalyzer : ImageAnalysis.Analyzer {
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        private var lastProcessTime = 0L
        
        @androidx.annotation.OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            // Procesar solo cada 500ms para no saturar
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessTime < 500) {
                imageProxy.close()
                return
            }
            lastProcessTime = currentTime
            
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val rawText = visionText.text
                        runOnUiThread { debugText.text = "Debug OCR:\n$rawText" }
                        
                        // Buscar líneas que parezcan nombres de carta
                        visionText.textBlocks.forEach { block ->
                            block.lines.forEach { line ->
                                val texto = line.text.trim()
                                // Filtrar: nombres suelen tener 5-30 caracteres, primera mayúscula
                                if (texto.length in 5..30 && texto[0].isUpperCase()) {
                                    buscarCarta(texto)
                                }
                            }
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }
    
    private fun buscarCarta(nombreDetectado: String) {
        if (nombreDetectado == lastDetectedName) return // Evitar spam
        lastDetectedName = nombreDetectado
        
        val resultados = CardDatabase.buscarPorNombre(nombreDetectado)
        
        runOnUiThread {
            cardListLayout.removeAllViews()
            
            if (resultados.isNotEmpty()) {
                resultText.text = "Detectado: $nombreDetectado"
                
                resultados.forEach { carta ->
                    val btn = Button(this).apply {
                        text = "${carta.nombre} - ${carta.codigo} [${carta.edicion}]"
                        setOnClickListener {
                            agregarCarta(carta)
                        }
                    }
                    cardListLayout.addView(btn)
                }
            }
        }
    }
    
    private fun agregarCarta(carta: Card) {
        if (!scannedCards.contains(carta.codigo)) {
            scannedCards.add(carta.codigo)
            resultText.text = "✅ Agregada: ${carta.nombre}\nTotal: ${scannedCards.size} cartas"
            cardListLayout.removeAllViews()
            lastDetectedName = "" // Reset para detectar otra
            Toast.makeText(this, "Guardada: ${carta.codigo}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (allPermissionsGranted()) startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
