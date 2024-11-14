package com.example.app_topicos.DialogFlowService

import android.content.Context
import android.util.Log
import com.example.app_topicos.R
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.dialogflow.v2.*
import java.util.*

class DialogflowClient(context: Context) {
    private val sessionsClient: SessionsClient
    private val session: SessionName
    private val TAG = "DialogflowClient"

    init {
        val stream = context.resources.openRawResource(R.raw.credenciales)
        val credentials = GoogleCredentials.fromStream(stream)
        val serviceAccountCredentials = credentials as? ServiceAccountCredentials
            ?: throw IllegalArgumentException("Credenciales no son de tipo ServiceAccount")

        val projectId = serviceAccountCredentials.projectId
        val settings = SessionsSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build()

        sessionsClient = SessionsClient.create(settings)
        session = SessionName.of(projectId, UUID.randomUUID().toString())
    }

    // Cambia TextToSpeechManager a (String) -> Unit para aceptar una función de callback que recibe un String
    fun sendToDialogflow(text: String, callback: (String) -> Unit) {
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
            callback(fulfillmentText)  // Llama al callback con fulfillmentText
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar mensaje a Dialogflow: ${e.message}")
        }
    }
}
