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
import kotlin.math.min

data class MylCard(
    val code: String, // KEL-194-200
    val name: String, // Cúchulainn
    val type: String, // Aliado
    val rarity: String, // Legendaria
    val race: String // Héroe
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
        Toast.makeText(this, "Cargadas ${cardList.size} cartas", Toast.LENGTH_SHORT).show()

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
            assets.open("cartas_myl.csv").bufferedReader().readLines()
             .drop(1) // Saltar header
             .mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size >= 5) {
                        MylCard(
                            code = parts[0].trim(),
                            name = parts[1].trim(),
                            type = parts[2].trim(),
                            rarity = parts[3].trim(),
                            race = parts[4].trim()
                        )
                    } else null
                }
        } catch (e: Exception) {
            Log.e("MYL", "Error cargando CSV", e)
            emptyList()
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.Builder()
         .requireLensFacing(CameraSelector.LENS_FACING_BACK)
         .build()

        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
         .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
         .build()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage!= null &&!isDialogShowing) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                 .addOnSuccessListener { visionText ->
                        val detectedText = visionText.text.lines()
                          .firstOrNull { it.trim().length > 3 }?.trim()?: ""
                        if (detectedText.isNotEmpty()) {
                            processDetectedText(detectedText)
                        }
                    }
                 .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this as LifecycleOwner, cameraSelector, preview, imageAnalysis
            )
        } catch (e: Exception) {
            Log.e("MYL-Scaner", "Error al iniciar cámara", e)
        }
    }

    private fun processDetectedText(text: String) {
        val currentTime = System.currentTimeMillis()
        if (text == lastDetectedText && currentTime - lastDetectionTime < 4000) return

        lastDetectedText = text
        lastDetectionTime = currentTime

        runOnUiThread {
            resultText.text = text
            val matches = findMatchingCards(text)
            if (matches.isNotEmpty() &&!isDialogShowing) {
                showCardDialog(matches)
            }
        }
    }

    private fun findMatchingCards(text: String): List<MylCard> {
        val cleanText = text.lowercase()
          .replace(Regex("[^a-z0-9áéíóúñ ]"), "")
          .trim()

        return cardList.filter { card ->
            val cleanCard = card.name.lowercase()
              .replace(Regex("[^a-z0-9áéíóúñ ]"), "")
              .trim()

            // Match exacto o contiene todas las palabras
            if (cleanText == cleanCard) return@filter true

            val cardWords = cleanCard.split(" ").filter { it.length > 2 }
            if (cardWords.isEmpty()) return@filter false

            val matchCount = cardWords.count { word -> cleanText.contains(word) }
            matchCount == cardWords.size // Todas las palabras deben estar
        }.distinctBy { it.code }.take(8) // Máximo 8 opciones
    }

    private fun showCardDialog(matches: List<MylCard>) {
        isDialogShowing = true
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(100)

        val items = matches.map {
            "${it.name}\n${it.code} - ${it.rarity} - ${it.race}"
        }.toTypedArray()

        AlertDialog.Builder(this)
         .setTitle("¿Cuál es la carta?")
         .setItems(items) { _, which ->
                val selected = matches[which]
                Toast.makeText(
                    this,
                    "Seleccionada: ${selected.name} ${selected.code}",
                    Toast.LENGTH_LONG
                ).show()
                isDialogShowing = false
            }
         .setNegativeButton("Ninguna") { _, _ -> isDialogShowing = false }
         .setOnCancelListener { isDialogShowing = false }
         .show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            startCamera()
        }
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
