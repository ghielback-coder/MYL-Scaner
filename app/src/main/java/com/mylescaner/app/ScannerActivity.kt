package com.mylescaner.app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var resultText: TextView
    private lateinit var imageCapture: ImageCapture
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentDetectedName = ""

    // LISTA CORREGIDA - SIN ERRORES DE SINTAXIS
    private val ediciones = listOf(
        "Aguila Imperial",
        "Alianza",
        "Arsenal",
        "Asgard",
        "Barbarie",
        "Bestia",
        "Calavera",
        "Celtibero",
        "Compendium",
        "Concilio",
        "Conquista",
        "Contraataque",
        "Corsarios",
        "Cronicas",
        "Dharma",
        "Dominios de Ra",
        "El Reto",
        "Encrucijada",
        "Espada Sagrada",
        "Espiritu de Dragon",
        "Expansion",
        "Expediciones",
        "Extension Compendium",
        "Exodo",
        "Guerrero Jaguar",
        "Helenica",
        "Heroes",
        "Hijos de Daana",
        "Hordas",
        "Inmortales",
        "Invasion Oscura",
        "Kemeth",
        "Kilimanjaro",
        "La Cofradia",
        "La Ira del Nahual",
        "Legado Gotico",
        "Mechadas",
        "Midgard",
        "Mundo Gotico",
        "Olimpia",
        "Otras",
        "Piratas",
        "Primer Bloque",
        "Ragnarok",
        "Ragnarok 2.0",
        "Relatos de Inframundo",
        "Segundo Bloque",
        "Steampunk",
        "Tercer Bloque",
        "Tesoros",
        "ToolKit",
        "Troya",
        "Valhalla",
        "Vendaval",
        "Vikings"
    )

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                reconocerTextoDeUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        previewView = findViewById(R.id.previewView)
        resultText = findViewById(R.id.resultText)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCapturar).setOnClickListener { capturarFoto() }
        findViewById<Button>(R.id.btnSubirFoto).setOnClickListener { subirFotoGaleria() }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun subirFotoGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun reconocerTextoDeUri(uri: Uri) {
        val image = InputImage.fromFilePath(this, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val imageWidth = visionText.textBlocks.maxOfOrNull { it.boundingBox?.right ?: 0 } ?: 1000
                val leftZoneLimit = (imageWidth * 0.4).toInt()

                val nameBlocks = visionText.textBlocks
                    .filter { block ->
                        val box = block.boundingBox ?: return@filter false
                        box.left < leftZoneLimit && box.right < leftZoneLimit
                    }
                    .flatMap { it.lines }
                    .map { it.text.trim() }
                    .filter { it.length > 2 && !it.contains(" ") && it.any { c -> c.isLetter() } }

                val detectedName = nameBlocks.maxByOrNull { it.length } ?: ""

                if (detectedName.isNotEmpty()) {
                    currentDetectedName = detectedName
                    resultText.text = detectedName
                    Toast.makeText(this, "Detectado: $detectedName", Toast.LENGTH_SHORT).show()
                    mostrarDialogoGuardar(detectedName, uri.toString())
                } else {
                    mostrarDialogoGuardarManual(uri.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al procesar imagen", Toast.LENGTH_SHORT).show()
                mostrarDialogoGuardarManual(uri.toString())
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(previewView.surfaceProvider)

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val imageWidth = mediaImage.width
                        val leftZoneLimit = imageWidth * 0.4

                        val nameBlocks = visionText.textBlocks
                            .filter { block ->
                                val box = block.boundingBox ?: return@filter false
                                box.left < leftZoneLimit && box.right < leftZoneLimit
                            }
                            .flatMap { it.lines }
                            .map { it.text.trim() }
                            .filter { it.length > 2 }

                        val detectedName = nameBlocks.maxByOrNull { it.length } ?: ""

                        if (detectedName.isNotEmpty()) {
                            currentDetectedName = detectedName
                            runOnUiThread {
                                resultText.text = detectedName
                            }
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
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                this as LifecycleOwner, cameraSelector, preview, imageCapture, imageAnalysis
            )
        } catch (e: Exception) {
            Log.e("MYL", "Error al iniciar cámara", e)
        }
    }

    private fun capturarFoto() {
        if (currentDetectedName.isEmpty()) {
            Toast.makeText(this, "Apunta al nombre de la carta primero", Toast.LENGTH_SHORT).show()
            return
        }

        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(100)

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "MyL_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyLScanner")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("MYL", "Error al capturar: ${exc.message}", exc)
                    Toast.makeText(baseContext, "Error al guardar foto", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(baseContext, "Foto guardada", Toast.LENGTH_SHORT).show()
                    mostrarDialogoGuardar(currentDetectedName, output.savedUri.toString())
                }
            }
        )
    }

    private fun mostrarDialogoGuardar(nombreDetectado: String, uriFoto: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_guardar_carta, null)
        val spinnerEdicion = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerEdicion)
        val edtNumero = dialogView.findViewById<EditText>(R.id.edtNumero)

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, ediciones)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEdicion.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Guardar: $nombreDetectado")
            .setView(dialogView)
            .setPositiveButton("GUARDAR") { _, _ ->
                val edicionSeleccionada = spinnerEdicion.selectedItem.toString()
                val numeroColeccionista = edtNumero.text.toString().ifEmpty { null }

                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@ScannerActivity)
                    db.cardDao().insert(
                        CardEntity(
                            nombreDetectado = nombreDetectado,
                            fotoUri = uriFoto,
                            edicionSeleccionada = edicionSeleccionada,
                            numeroColeccionista = numeroColeccionista
                        )
                    )

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ScannerActivity,
                            "Guardada: $nombreDetectado",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun mostrarDialogoGuardarManual(uriFoto: String) {
        val input = EditText(this)
        input.hint = "Escribe el nombre de la carta"
        
        AlertDialog.Builder(this)
            .setTitle("No detecté el nombre")
            .setMessage("Escribe el nombre manualmente:")
            .setView(input)
            .setPositiveButton("SIGUIENTE") { _, _ ->
                val nombreManual = input.text.toString()
                if (nombreManual.isNotEmpty()) {
                    mostrarDialogoGuardar(nombreManual, uriFoto)
                } else {
                    Toast.makeText(this, "Debes escribir un nombre", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCELAR", null)
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
