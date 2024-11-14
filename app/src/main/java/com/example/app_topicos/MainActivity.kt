package com.example.app_topicos

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.app_topicos.AppService.AppOpeningService
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.dialogflow.v2.*
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private val uuid = UUID.randomUUID().toString()
    private lateinit var recognizerIntent: Intent
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var sessionsClient: SessionsClient
    private lateinit var session: SessionName
    private val TAG = "MainActivity"
    private var showDialog = false

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAccessibilityPermission()
        checkPermissions()  // Verifica permisos de audio y cámara

        initializeDialogflow()
        initializeTextToSpeech()
        initializeSpeechRecognizer()
        initializeShakeService()

        if (showDialog) {
            showAccessibilityDialog()
        }

        val btnSpeak: Button = findViewById(R.id.btnSpeak)
        btnSpeak.visibility = View.VISIBLE
        btnSpeak.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> startListening()
                android.view.MotionEvent.ACTION_UP -> speechRecognizer.stopListening()
            }
            true
        }
    }

    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun initializeDialogflow() {
        try {
            val stream = resources.openRawResource(R.raw.credenciales)
            val credentials = GoogleCredentials.fromStream(stream)
            val serviceAccountCredentials = credentials as? ServiceAccountCredentials
                ?: throw IllegalArgumentException("Credenciales no son de tipo ServiceAccount")

            val projectId = serviceAccountCredentials.projectId
            val settings = SessionsSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()

            sessionsClient = SessionsClient.create(settings)
            session = SessionName.of(projectId, uuid)

            Log.d(TAG, "Dialogflow inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar Dialogflow: ${e.message}")
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "es-ES"
            ) // Configura el reconocimiento en español
        }
    }

    private fun initializeShakeService() {
        val shakeServiceIntent = Intent(this, ShakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(shakeServiceIntent)
        } else {
            startService(shakeServiceIntent)
        }
    }

    private fun startListening() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "Habla ahora", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.get(0) ?: ""
                Log.d(TAG, "Texto reconocido: $spokenText")

                if (spokenText.equals("abrir cámara", ignoreCase = true)) {
                    openCamera()
                } else {
                    sendToDialogflow(spokenText)
                }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se reconoció ninguna coincidencia"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocimiento de voz está ocupado"
                    SpeechRecognizer.ERROR_CLIENT -> "Error del cliente de reconocimiento de voz"
                    else -> "Error en SpeechRecognizer: $error"
                }
                Log.e(TAG, errorMessage)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
        })
        speechRecognizer.startListening(recognizerIntent)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivity(cameraIntent)
        } else {
            checkPermissions()
        }
    }

    private fun sendToDialogflow(text: String) {
        try {
            val textInput = TextInput.newBuilder().setText(text).setLanguageCode("es").build()
            val queryInput = QueryInput.newBuilder().setText(textInput).build()

            val request = DetectIntentRequest.newBuilder()
                .setSession(session.toString())
                .setQueryInput(queryInput)
                .build()

            val response = sessionsClient.detectIntent(request)
            val fulfillmentText = response.queryResult.fulfillmentText

            Log.d(TAG, "Respuesta de Dialogflow: $fulfillmentText")
            speak(fulfillmentText)
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar mensaje a Dialogflow: ${e.message}")
        }
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale("es", "ES")
        } else {
            Log.e(TAG, "Error al inicializar TextToSpeech")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            permissions.forEachIndexed { index, permission ->
                val isGranted = grantResults[index] == PackageManager.PERMISSION_GRANTED
                val message = if (isGranted) "concedido" else "denegado"
                Toast.makeText(this, "Permiso de $permission $message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled(AppOpeningService::class.java)) {
            showDialog = true
        }
    }

    private fun isAccessibilityServiceEnabled(service: Class<out AccessibilityService>): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service.name) ?: false
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de Accesibilidad Requerido")
            .setMessage("Esta aplicación requiere acceso a los servicios de accesibilidad para funcionar correctamente. ¿Quieres activarlos ahora?")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
    }
}