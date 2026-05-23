package com.mylescaner.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
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
    
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var resultText: TextView
    private lateinit var debugText: TextView
    private lateinit var cardListLayout: LinearLayout
    private lateinit var scannedListText: TextView
    private lateinit var btnLimpiar: Button
    private val scannedCards = mutableListOf<Card>() // Lista pa ver duplicadas
    private var lastDetectedName = ""
    private var detectionEnabled = true
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var toneGen: ToneGenerator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        previewView = PreviewView(this)
        
        resultText = TextView(this).apply {
            text = "Apunta al nombre de la carta"
            textSize = 20f
            setPadding(32, 32, 32, 8)
        }
        
        // Botón limpiar lista
        btnLimpiar = Button(this).apply {
            text = "🗑️ Limpiar Lista"
            setOnClickListener {
                scannedCards.clear()
                updateScannedList()
                Toast.makeText(this@MainActivity, "Lista vaciada", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Lista de cartas escaneadas
        scannedListText = TextView(this).apply {
            text = "Cartas escaneadas: 0"
            textSize = 14f
            setPadding(32, 8, 32, 8)
            setBackgroundColor(0xFF222222.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        
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
        
        mainLayout.addView(previewView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        mainLayout.addView(resultText)
        mainLayout.addView(btnLimpiar)
        mainLayout.addView(scannedListText)
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        setContentView(mainLayout)
        
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
                camera.cameraControl.setZoomRatio(1.5f)
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
            if (!detectionEnabled) {
                imageProxy.close()
                return
            }
            
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessTime < 800) {
                imageProxy.close()
                return
            }
            lastProcessTime = currentTime
            
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        runOnUiThread { 
                            debugText.text = "Debug OCR:\n${visionText.text}" 
                        }
                        
                        if (detectionEnabled) {
                            visionText.textBlocks.forEach { block ->
                                block.lines.forEach { line ->
                                    val texto = line.text.trim()
                                    if (texto.length in 5..30 && texto[0].isUpperCase()) {
                                        buscarCarta(texto)
                                    }
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
        if (nombreDetectado == lastDetectedName || !detectionEnabled) return
        lastDetectedName = nombreDetectado
        
        val resultados = CardDatabase.buscarPorNombre(nombreDetectado)
        
        if (resultados.isNotEmpty()) {
            detectionEnabled = false
            
            runOnUiThread {
                cardListLayout.removeAllViews()
                resultText.text = "Detectado: $nombreDetectado"
                
                resultados.forEach { carta ->
                    val btn = Button(this).apply {
                        text = "${carta.nombre}\n${carta.codigo} - ${carta.edicion}"
                        textSize = 16f
                        setPadding(16, 24, 16, 24)
                        setOnClickListener {
                            agregarCarta(carta)
                        }
                    }
                    cardListLayout.addView(btn)
                }
                
                // Botón Cancelar/Saltar
                val btnCancelar = Button(this).apply {
                    text = "❌ Cancelar / Saltar"
                    setBackgroundColor(0xFF555555.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(16, 24, 16, 24)
                    setOnClickListener {
                        cancelarDeteccion()
                    }
                }
                cardListLayout.addView(btnCancelar)
            }
        }
    }
    
    private fun cancelarDeteccion() {
        cardListLayout.removeAllViews()
        detectionEnabled = true
        lastDetectedName = ""
        resultText.text = "Apunta al nombre de la carta\nTotal: ${scannedCards.size} cartas"
    }
    
    private fun agregarCarta(carta: Card) {
        scannedCards.add(carta) // Ahora permite duplicadas
        updateScannedList()
        cardListLayout.removeAllViews()
        
        // Sonido + Vibración
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(100)
        
        Toast.makeText(this, "✅ ${carta.codigo}", Toast.LENGTH_SHORT).show()
        
        // Re-activar después de 1 seg
        handler.postDelayed({
            detectionEnabled = true
            lastDetectedName = ""
            resultText.text = "Apunta al nombre de la carta\nTotal: ${scannedCards.size} cartas"
        }, 1000)
    }
    
    private fun updateScannedList() {
        val conteo = scannedCards.groupingBy { it.codigo }.eachCount()
        val texto = if (conteo.isEmpty()) {
            "Cartas escaneadas: 0"
        } else {
            "Cartas escaneadas: ${scannedCards.size}\n" + 
            conteo.entries.joinToString("\n") { "${it.key} x${it.value}" }
        }
        scannedListText.text = texto
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
        toneGen.release()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
