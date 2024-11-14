package com.example.app_topicos.DialogFlowService

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {

    private val textToSpeech: TextToSpeech = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale("es", "ES")
        } else {
            Log.e("TextToSpeechManager", "Error al inicializar TextToSpeech")
        }
    }

    fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun shutdown() {
        textToSpeech.shutdown()
    }
}
