package com.mylescaner.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
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
    private lateinit var btnCopiar: Button
    private lateinit var btnCompartir: Button
    private val scannedCards = mutableListOf<Card>()
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
        
        previewView =
