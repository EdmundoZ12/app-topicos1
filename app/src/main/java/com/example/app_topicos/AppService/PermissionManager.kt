package com.example.app_topicos.AppService

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    fun checkPermissions(permissions: Array<String>, requestCode: Int = 1) {
        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(context as Activity, permissionsNeeded.toTypedArray(), requestCode)
        }
    }

    fun handlePermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1) {
            permissions.forEachIndexed { index, permission ->
                val isGranted = grantResults[index] == PackageManager.PERMISSION_GRANTED
                val message = if (isGranted) "concedido" else "denegado"
                Toast.makeText(context, "Permiso de $permission $message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun isAccessibilityEnabled(serviceClass: Class<out AccessibilityService>): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceClass.name) ?: false
    }
}
