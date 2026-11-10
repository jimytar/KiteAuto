package com.jimytar.kiteauto.car

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Speech-to-text for the car toolbar's mic button (system speech recognizer; needs the RECORD_AUDIO permission,
 * granted from the phone app). [onStatus] gets short user-facing status strings, null when done.
 */
class VoiceInput(private val context: Context, private val onStatus: (String?) -> Unit) {
    private var recognizer: SpeechRecognizer? = null

    fun listen(onText: (String) -> Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return flash("Allow the microphone in the KiteAuto app on the phone")
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return flash("No speech recognition on this phone")
        cancel()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onStatus("Listening…")
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                cancel()
                onStatus(null)
                if (!text.isNullOrBlank()) onText(text)
            }

            override fun onError(error: Int) {
                Log.i("KiteAuto", "Speech recognition error $error")
                cancel()
                flash(if (error == SpeechRecognizer.ERROR_NO_MATCH) "Didn't catch that" else "Voice input failed ($error)")
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onStatus("…")
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        )
    }

    fun cancel() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun flash(message: String) {
        onStatus(message)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onStatus(null) }, 3000)
    }
}
