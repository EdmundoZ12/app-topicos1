package com.example.app_topicos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt
import android.speech.tts.TextToSpeech
import java.util.*

class ShakeService : Service(), SensorEventListener,TextToSpeech.OnInitListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var shakeThreshold = 15f // Sensibilidad para la detección de sacudidas
    private var lastShakeTime: Long = 0
    private lateinit var textToSpeech: TextToSpeech;
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        // Hablar el mensaje "Aplicación detenida"
        speak("Aplicación detenida")

        // Detener el servicio en primer plano después de que se pronuncie el mensaje
        handler.postDelayed({
            stopForeground(true)
            stopSelf()
        }, 2000) // 2 segundos para que pueda hablar

    }

    override fun onCreate() {
        super.onCreate()

        // Inicializamos el TextToSpeech
        textToSpeech = TextToSpeech(this, this) // <-- Inicializar TextToSpeech
        // Inicializamos el sensor de acelerómetro
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Registramos el listener para el acelerómetro
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        Log.d("ShakeService", "Servicio creado y acelerómetro registrado")

        // Iniciar el servicio en primer plano
        startForegroundService()
        // Iniciar el temporizador de 1 minuto
        handler.postDelayed(timeoutRunnable, 30_000) // 30 segundos en milisegundos
    }

    // Iniciar el servicio en primer plano con una notificación
    private fun startForegroundService() {
        val notificationChannelId = "SHAKE_SERVICE_CHANNEL"

        // Crear la notificación y el canal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Shake Detection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // Intent para abrir la actividad principal si se toca la notificación
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        // Crear la notificación
        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Detección de Sacudida")
            .setContentText("El servicio está ejecutándose en segundo plano.")
            .setSmallIcon(R.mipmap.ic_launcher) // Usa tu propio ícono
            .setContentIntent(pendingIntent)
            .build()

        // Iniciar el servicio en primer plano
        startForeground(1, notification)
        handler.postDelayed(timeoutRunnable, 30_000) // 30 segundos en milisegundos
    }

    override fun onDestroy() {
        super.onDestroy()
        // Desregistramos el sensor cuando el servicio es destruido
        sensorManager.unregisterListener(this)
        // Liberamos el TextToSpeech
        if (textToSpeech.isSpeaking) { // <-- Detener y liberar TextToSpeech
            textToSpeech.stop()
        }
        textToSpeech.shutdown()
        // Eliminar cualquier Runnable pendiente
        handler.removeCallbacks(timeoutRunnable)
        Log.d("ShakeService", "Servicio destruido y acelerómetro desregistrado")
    }

    // Este método se llama cuando el sensor detecta un cambio
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            // Calculamos la aceleración total
            val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            if (acceleration > shakeThreshold) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShakeTime > 1000) { // Evitamos detectar sacudidas consecutivas muy rápidas
                    lastShakeTime = currentTime
                    onShakeDetected()
                }
            }
        }
    }

    private fun onShakeDetected() {
        Log.d("ShakeService", "¡Sacudida detectada!")

        // Vibración como feedback (opcional)
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(500) // Vibrar por 500 milisegundos
        // Sintetizar la voz
        speak("Hola") // <-- Llamar a la función speak para decir "Hola"
        // Reiniciar el temporizador de 1 minuto
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, 60_000) // 1 minuto

        // Intent para lanzar la actividad
        val launchIntent = Intent(this, MainActivity::class.java)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(launchIntent)
        handler.postDelayed(timeoutRunnable, 30_000) // 30 segundos en milisegundos

        try {
            startActivity(launchIntent)
            handler.postDelayed(timeoutRunnable, 30_000) // 30 segundos en milisegundos
        } catch (e: Exception) {
            Log.e("ShakeService", "Error al lanzar la actividad: ${e.message}")
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No necesitamos manejar cambios en la precisión
    }

    override fun onBind(intent: Intent?): IBinder? {
        // No usamos este método ya que es un servicio sin conexión directa (no Bound Service)
        return null
    }


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Configurar el idioma a español (puedes cambiar esto según el idioma)
            val result = textToSpeech.setLanguage(Locale("es", "ES")) // <-- Configurar idioma

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("ShakeService", "El idioma no es compatible")
            } else {
                Log.d("ShakeService", "TextToSpeech inicializado correctamente")
            }
        } else {
            Log.e("ShakeService", "Error al inicializar TextToSpeech")
        }
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) // <-- Función para sintetizar la voz
    }
}
