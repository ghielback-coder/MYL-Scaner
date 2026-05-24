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

    // LISTA COMPLETA DE EDICIONES MYL - ORDENADA ALFABÉTICAMENTE
    private val ediciones = listOf(
        "Águila Imperial", "Alianza", "Arsenal", "Asgard", "Barbarie", "Bestia", 
        "Calavera", "Celtíbero", "Compendium", "Concilio", "Conquista", "Contraataque",
        "Corsarios", "Crónicas", "Dharma", "Dominios de Ra", "El Reto", "Encrucijada",
        "Espada Sagrada", "Espíritu de Dragón", "Expansión", "Expediciones", "Extensión Compendium",
        "Exodo", "Guerrero Jaguar", "Helénica", "Héroes", "Hijos de Daana", "Hordas",
        "Inmortales", "Invasión Oscura", "Kemeth", "Kilimanjaro", "La Cofradía", 
        "La Ira del Nahual", "Legado Gótico", "Mechadas", "Midgard", "Mundo Gótico",
        "Olimpia", "Otras", "Piratas", "Primer Bloque", "Ragnarok", "Ragnarok 2.0",
        "Relatos de Inframundo", "Segundo Bloque", "Steampunk", "Tercer Bloque", 
        "Tesoros", "ToolKit", "Troya", "Valhalla", "Vendaval", "Vikings"
    )

    // Lanzador para elegir foto de galería
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.result
