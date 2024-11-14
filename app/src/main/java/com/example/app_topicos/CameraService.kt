package com.example.app_topicos

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import android.content.pm.PackageManager

class CameraService : Service() {

    private val logTag = "CameraService"
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private var cameraId: String = ""
    private var lastCaptureTime: Long = 0
    private val captureInterval = 3000L // 3 segundos

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("CameraBackground").also { it.start() }
        handler = Handler(handlerThread.looper)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0] // Selecciona la primera cámara

        openCamera()
    }

    private fun openCamera() {
        try {
            // Verifica si el permiso ha sido otorgado
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(logTag, "Permiso de cámara no otorgado.")
                stopSelf() // Detiene el servicio si no hay permiso
                return
            }

            // Abre la cámara si el permiso ha sido otorgado
            // cameraManager.openCamera(cameraId, cameraStateCallback, handler)
        } catch (e: CameraAccessException) {
            Log.e(logTag, "Error al abrir la cámara: ${e.message}")
            stopSelf()
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startRepeatingCapture()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
            stopSelf()
        }
    }

    private fun startRepeatingCapture() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCaptureTime >= captureInterval) {
                    lastCaptureTime = currentTime
                    takePicture()
                }
                handler.postDelayed(this, captureInterval)
            }
        }, captureInterval)
    }

    private fun takePicture() {
        // Lógica de captura de foto
        Log.d(logTag, "Foto capturada")
        Toast.makeText(this, "Foto capturada", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraDevice?.close()
        handlerThread.quitSafely()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
