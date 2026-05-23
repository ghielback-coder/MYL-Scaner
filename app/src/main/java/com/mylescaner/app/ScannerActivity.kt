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
import java.text.Normalizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class MylCard(
    val code: String,
    val name: String,
    val type: String,
    val rarity: String,
    val race: String
)

class ScannerActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_scanner)

        previewView = findViewById(R.id.previewView)
        resultText = findViewById(R.id.resultText)
        cameraExecutor = Executors.newSingleThreadExecutor()

        cardList = loadCardsFromAssets()
        Log.d("MYL", "Cartas cargadas: ${cardList.size}")

        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun loadCardsFromAssets(): List<MylCard> {
        return try {
            assets.open("cartas_myl.csv").bufferedReader().useLines { lines ->
                lines.drop(1)
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
                    }.toList()
            }
        } catch (e: Exception) {
            Log.e("MYL", "Error cargando CSV", e)
            emptyList()
        }
    }

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
           .replace(Regex("\\p{M}"), "")
           .lowercase()
           .replace(Regex("[^a-z0-9 ]"), "")
           .replace(Regex("\\s+"), " ")
           .trim()
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
            if (isDialogShowing) {
                imageProxy.close()
                return@setAnalyzer
            }

            val mediaImage = imageProxy.image
            if (mediaImage!= null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                   .addOnSuccessListener { visionText ->
                        // FIX 1: JUNTAR TODAS LAS LÍNEAS - ANTES SOLO TOMABA LA PRIMERA
                        val allText = visionText.textBlocks
                           .flatMap { it.lines }
                           .joinToString(" ") { it.text.trim() }
                           .replace(Regex("\\s+"), " ")
                           .trim()

                        if (allText.length > 2) {
                            processDetectedText(allText)
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
            Log.e("MYL", "Error al iniciar cámara", e)
        }
    }

    private fun processDetectedText(text: String) {
        val currentTime = System.currentTimeMillis()
        if (text == lastDetectedText && currentTime - lastDetectionTime < 2000) return

        lastDetectedText = text
        lastDetectionTime = currentTime

        runOnUiThread {
            resultText.text = text
            Log.d("MYL", "Texto detectado: '$text'")

            val matches = findMatchingCards(text)
            Log.d("MYL", "Matches: ${matches.size} -> ${matches.map { it.name }}")

            if (matches.isNotEmpty() &&!isDialogShowing) {
                showCardDialog(matches)
            } else if (matches.isEmpty()) {
                Log.d("MYL", "No hubo matches para: '$text'")
            }
        }
    }

    private fun findMatchingCards(text: String): List<MylCard> {
        val cleanText = normalizeText(text)
        if (cleanText.length < 3) return emptyList()

        Log.d("MYL", "Buscando: '$cleanText'")

        // FIX 2: MATCHING MÁS AGRESIVO
        val matches = cardList.filter { card ->
            val cleanCard = normalizeText(card.name)

            // 1. Match exacto
            if (cleanCard == cleanText) return@filter true

            // 2. La carta contiene todo el texto detectado
            if (cleanCard.contains(cleanText)) return@filter true

            // 3. El texto detectado contiene la carta completa
            if (cleanText.contains(cleanCard)) return@filter true

            // 4. Match por palabras: al menos 2 palabras coinciden
            val wordsText = cleanText.split(" ").filter { it.length > 2 }
            val wordsCard = cleanCard.split(" ").filter { it.length > 2 }
            val commonWords = wordsText.intersect(wordsCard.toSet())
            if (wordsText.isNotEmpty() && commonWords.size >= 2) return@filter true
            if (wordsCard.size == 1 && commonWords.isNotEmpty()) return@filter true

            false
        }.distinctBy { it.code }.take(10)

        return matches
    }

    private fun showCardDialog(matches: List<MylCard>) {
        if (isDialogShowing) return
        isDialogShowing = true

        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(100)

        val items = matches.map {
            "${it.name}\n${it.code} | ${it.rarity} | ${it.race}"
        }.toTypedArray()

        AlertDialog.Builder(this)
           .setTitle("¿Cuál es la carta?")
           .setItems(items) { dialog, which ->
                val selected = matches[which]
                Toast.makeText(
                    this,
                    "Seleccionada: ${selected.name}",
                    Toast.LENGTH_LONG
                ).show()
                dialog.dismiss()
            }
           .setNegativeButton("Ninguna") { dialog, _ ->
                dialog.dismiss()
            }
           .setOnDismissListener {
                isDialogShowing = false
            }
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
