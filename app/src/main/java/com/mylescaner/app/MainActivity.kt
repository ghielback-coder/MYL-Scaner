package com.mylescaner.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var resultText: TextView
    private lateinit var debugText: TextView
    private var imageCapture: ImageCapture? = null
    
    // Regex más flexible: acepta espacios, guiones, O vs 0, I vs 1
    private val MYL_PATTERN = Pattern.compile("T\\s*A\\s*[-_]?\\s*([A-Z0-9O0I1]{3})", Pattern.CASE_INSENSITIVE)
    private val scannedCards = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        previewView = PreviewView(this)
        
        resultText = TextView(this).apply {
            text = "Apunta a una carta TA-XXX"
            textSize = 22f
            setPadding(32, 32, 32, 16)
        }
        
        debugText = TextView(this).apply {
            text = "Debug OCR: esperando..."
            textSize = 12f
            setPadding(32, 0, 32, 32)
            setTextColor(0xFF888888.toInt())
        }
        
        val scrollView = ScrollView(this).apply {
            addView(debugText)
        }
        
        layout.addView(previewView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            0, 
            1f
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
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, TextAnalyzer())
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
            } catch(exc: Exception) {
                Log.e("Camera", "Error al iniciar cámara", exc)
                Toast.makeText(this, "Error cámara: ${exc.message}", Toast.LENGTH_LONG).show()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class TextAnalyzer : ImageAnalysis.Analyzer {
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        @androidx.annotation.OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val rawText = visionText.text
                        runOnUiThread {
                            debugText.text = "Debug OCR:\n$rawText"
                        }
                        
                        val matcher = MYL_PATTERN.matcher(rawText)
                        if (matcher.find()) {
                            var codigo = matcher.group(0) ?: ""
                            // Limpiar: quitar espacios, convertir O->0, I->1
                            codigo = codigo.replace(" ", "").replace("_", "-")
                                .replace("O", "0").replace("I", "1")
                                .uppercase()
                            
                            if (!scannedCards.contains(codigo)) {
                                scannedCards.add(codigo)
                                runOnUiThread {
                                    resultText.text = "✅ $codigo\nTotal: ${scannedCards.size} cartas"
                                }
                                Log.d("OCR", "Encontrado: $codigo")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("OCR", "Error OCR", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_SHORT).show()
                finish()
            }
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
