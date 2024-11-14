package com.example.app_topicos

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper


class VolumeButtonService : Service() {

    private var volumeUpPressed = false
    private var volumeDownPressed = false
    private var startTime: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun checkIfBothButtonsPressed() {
        if (volumeUpPressed && volumeDownPressed) {
            startTime = System.currentTimeMillis()
            Handler(Looper.getMainLooper()).postDelayed({
                if (volumeUpPressed && volumeDownPressed) {
                    launchApp()
                }
            }, 2000) // Esperar 2 segundos
        }
    }

    private fun launchApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
