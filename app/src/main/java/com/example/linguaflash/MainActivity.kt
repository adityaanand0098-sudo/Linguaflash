package com.example.linguaflash

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var tts: TextToSpeech

    private val languages = linkedMapOf(
        "English" to "en",
        "Hindi" to "hi",
        "Gujarati" to "gu",
        "Bengali" to "bn",
        "Marathi" to "mr",
        "Tamil" to "ta",
        "Telugu" to "te",
        "Kannada" to "kn",
        "Malayalam" to "ml",
        "Punjabi" to "pa",
        "Odia" to "or",
        "Urdu" to "ur",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Chinese (Simplified)" to "zh",
        "Japanese" to "ja",
        "Arabic" to "ar"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        val captureButton = findViewById<android.widget.Button>(R.id.captureButton)
        val speakButton = findViewById<android.widget.Button>(R.id.speakButton)
        val spinner = findViewById<android.widget.Spinner>(R.id.targetSpinner)
        val resultText = findViewById<android.widget.TextView>(R.id.resultText)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages.keys.toList())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e("TTS", "Initialization failed")
            } else {
                tts.language = Locale.ENGLISH
            }
        }

        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        captureButton.setOnClickListener {
            val photoFile = File.createTempFile("temp", ".jpg", cacheDir)
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object: ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = Uri.fromFile(photoFile)
                    val image = InputImage.fromFilePath(applicationContext, uri)
                    runTextRecognition(image) { detectedText ->
                        runOnUiThread { resultText.text = detectedText }
                        val languageIdentifier = LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.40f).build())
                        languageIdentifier.identifyLanguage(detectedText)
                            .addOnSuccessListener { langCode ->
                                val target = languages.values.toList()[spinner.selectedItemPosition]
                                translateText(detectedText, langCode, target) { translated ->
                                    runOnUiThread { resultText.text = translated }
                                }
                            }
                            .addOnFailureListener { e ->
                                runOnUiThread { resultText.text = "Lang ID failed: ${e.message}" }
                            }
                    }
                }
            })
        }

        speakButton.setOnClickListener {
            val text = resultText.text.toString()
            if (text.isNotBlank()) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1")
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("CameraX", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun runTextRecognition(image: com.google.mlkit.vision.common.InputImage, callback: (String) -> Unit) {
        val recognizer = TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText -> callback(visionText.text) }
            .addOnFailureListener { e -> callback("OCR failed: ${e.message}") }
    }

    private fun translateText(text: String, sourceTag: String, targetTag: String, callback: (String) -> Unit) {
        val source = if (sourceTag == "und") com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH else sourceTag
        val target = targetTag
        val options = TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        val translator = Translation.getClient(options)
        val conditions = com.google.mlkit.common.model.DownloadConditions.Builder().requireWifi().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated -> callback(translated) }
                    .addOnFailureListener { e -> callback("Translate failed: ${e.message}") }
            }
            .addOnFailureListener { e -> callback("Model download failed: ${e.message}") }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
    }
}
