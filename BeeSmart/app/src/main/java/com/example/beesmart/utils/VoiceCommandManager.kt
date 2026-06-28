package com.example.beesmart.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

/**
 * Manager for handling voice commands and speech recognition
 * Provides a clean API for voice input in Compose UI
 */
class VoiceCommandManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val resultChannel = Channel<VoiceResult>(Channel.BUFFERED)
    val resultFlow: Flow<VoiceResult> = resultChannel.receiveAsFlow()

    /**
     * Check if speech recognition is available on this device
     */
    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Start listening for voice input
     * @param language Language code (default: Romanian "ro-RO")
     * @param prompt Optional prompt text to show to user
     */
    fun startListening(
        language: String = "ro-RO",
        prompt: String = "Spune comanda..."
    ) {
        // Release previous instance
        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.startListening(intent)
    }

    /**
     * Stop listening and release resources
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * Release all resources
     */
    fun release() {
        stopListening()
        resultChannel.close()
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            resultChannel.trySend(VoiceResult.Ready)
        }

        override fun onBeginningOfSpeech() {
            resultChannel.trySend(VoiceResult.Speaking)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changed - can be used for visualization
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received - not needed for basic implementation
        }

        override fun onEndOfSpeech() {
            resultChannel.trySend(VoiceResult.Processing)
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Eroare audio"
                SpeechRecognizer.ERROR_CLIENT -> "Eroare client"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisiune microfon necesară"
                SpeechRecognizer.ERROR_NETWORK -> "Eroare rețea"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout rețea"
                SpeechRecognizer.ERROR_NO_MATCH -> "Nu am înțeles. Încearcă din nou."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recunoaștere ocupată"
                SpeechRecognizer.ERROR_SERVER -> "Eroare server"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nu am detectat vorbire"
                else -> "Eroare necunoscută"
            }
            resultChannel.trySend(VoiceResult.Error(errorMessage))
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                resultChannel.trySend(VoiceResult.Success(matches))
            } else {
                resultChannel.trySend(VoiceResult.Error("Nu am înțeles"))
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                resultChannel.trySend(VoiceResult.Partial(matches[0]))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Additional events - not needed for basic implementation
        }
    }

    /**
     * Parse voice commands for form fields
     * Returns a map of field names to their recognized values
     */
    fun parseFormCommand(text: String, fields: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lowerText = text.lowercase(Locale.ROOT)

        // Romanian voice command patterns
        fields.forEach { field ->
            val patterns = when (field) {
                "name", "nume" -> listOf("numele este", "nume", "se numește", "se cheamă")
                "description", "descriere" -> listOf("descrierea este", "descriere", "detalii")
                "location", "locație", "locatie" -> listOf("locația este", "locație", "la", "în")
                "notes", "notițe", "notite" -> listOf("notițe", "note", "observații")
                "type", "tip" -> listOf("tipul este", "tip", "de tip", "tip stup")
                "status", "status stup", "stare" -> listOf("statusul este", "status", "starea este", "stare")
                "temperature", "temperatura" -> listOf("temperatura este", "temperatura", "gradele sunt")
                "frames_total" -> listOf("rame totale", "total rame", "numarul de rame", "rame in total")
                "brood_frames" -> listOf("rame cu puiet", "puiet", "rame de puiet")
                "honey_frames" -> listOf("rame cu miere", "miere", "rame de miere")
                "pollen_frames" -> listOf("rame cu polen", "polen", "rame de polen")
                "queen_seen" -> listOf("regina", "regina este", "regina vazuta")
                "eggs_seen" -> listOf("oua", "ouale", "oua vazute", "oua sunt")
                "larvae_seen" -> listOf("larve", "larvele", "larve vazute", "larve sunt")
                "queen_cells_seen" -> listOf("botci", "botci observate", "botci vazute")
                "queen_cells_with_eggs" -> listOf("botci cu oua", "botci insamantate")
                "bearding_at_entrance" -> listOf("barba la urdinis", "aglomerare la urdinis", "albine la urdinis")
                "space_needed" -> listOf("lipsa spatiu", "spatiu insuficient", "nu are spatiu")
                "brood_pattern" -> listOf("uniformitate puiet", "model puiet", "puietul este", "puiet")
                "feeding_given" -> listOf("hranire", "hranire efectuata", "am hranit")
                "water_available" -> listOf("apa", "apa disponibila", "adapator")
                "moisture_or_mold" -> listOf("umezeala", "mucegai", "umezeala sau mucegai")
                "dead_bees_at_entrance" -> listOf("mortalitate la urdinis", "albine moarte", "moarte la urdinis")
                "unusual_behavior" -> listOf("comportament neobisnuit", "comportament", "agitatie")
                "temperament" -> listOf("temperament", "temperamentul este", "familia este")
                "honey_capping_percent" -> listOf("capacire miere", "miere capacita", "procent capacire")
                "old_combs_to_replace" -> listOf("faguri vechi", "faguri de schimbat", "rame de schimbat")
                "hive", "stup" -> listOf("stupul este", "stupul", "stupul numit", "stup")
                else -> listOf(field)
            }

            for (pattern in patterns) {
                val index = lowerText.indexOf(pattern)
                if (index != -1) {
                    val valueStart = index + pattern.length
                    val remaining = text.substring(valueStart).trim()

                    // Extract value until next field or end
                    val nextFieldIndex = fields
                        .filter { it != field }
                        .mapNotNull { nextField ->
                            val nextPatterns = when (nextField) {
                                "name", "nume" -> listOf("numele este", "nume", "se numește")
                                "description", "descriere" -> listOf("descrierea este", "descriere")
                                "location", "locație", "locatie" -> listOf("locația este", "locație")
                                "notes", "notițe", "notite" -> listOf("notițe", "note")
                                "type", "tip" -> listOf("tipul este", "tip", "de tip", "tip stup")
                                "status", "status stup", "stare" -> listOf("statusul este", "status", "starea este", "stare")
                                "temperature", "temperatura" -> listOf("temperatura este", "temperatura", "gradele sunt")
                                "frames_total" -> listOf("rame totale", "total rame", "numarul de rame", "rame in total")
                                "brood_frames" -> listOf("rame cu puiet", "puiet", "rame de puiet")
                                "honey_frames" -> listOf("rame cu miere", "miere", "rame de miere")
                                "pollen_frames" -> listOf("rame cu polen", "polen", "rame de polen")
                                "queen_seen" -> listOf("regina", "regina este", "regina vazuta")
                                "eggs_seen" -> listOf("oua", "ouale", "oua vazute", "oua sunt")
                                "larvae_seen" -> listOf("larve", "larvele", "larve vazute", "larve sunt")
                                "queen_cells_seen" -> listOf("botci", "botci observate", "botci vazute")
                                "queen_cells_with_eggs" -> listOf("botci cu oua", "botci insamantate")
                                "bearding_at_entrance" -> listOf("barba la urdinis", "aglomerare la urdinis", "albine la urdinis")
                                "space_needed" -> listOf("lipsa spatiu", "spatiu insuficient", "nu are spatiu")
                                "brood_pattern" -> listOf("uniformitate puiet", "model puiet", "puietul este", "puiet")
                                "feeding_given" -> listOf("hranire", "hranire efectuata", "am hranit")
                                "water_available" -> listOf("apa", "apa disponibila", "adapator")
                                "moisture_or_mold" -> listOf("umezeala", "mucegai", "umezeala sau mucegai")
                                "dead_bees_at_entrance" -> listOf("mortalitate la urdinis", "albine moarte", "moarte la urdinis")
                                "unusual_behavior" -> listOf("comportament neobisnuit", "comportament", "agitatie")
                                "temperament" -> listOf("temperament", "temperamentul este", "familia este")
                                "honey_capping_percent" -> listOf("capacire miere", "miere capacita", "procent capacire")
                                "old_combs_to_replace" -> listOf("faguri vechi", "faguri de schimbat", "rame de schimbat")
                                "hive", "stup" -> listOf("stupul este", "stupul", "stupul numit", "stup")
                                else -> listOf(nextField)
                            }
                            nextPatterns.mapNotNull { np ->
                                remaining.lowercase().indexOf(np).takeIf { it > 0 }
                            }.minOrNull()
                        }
                        .minOrNull()

                    val value = if (nextFieldIndex != null) {
                        remaining.substring(0, nextFieldIndex).trim()
                    } else {
                        remaining
                    }

                    if (value.isNotBlank()) {
                        result[field] = value.trim('.' , ',', '!')
                        android.util.Log.d("VoiceCommandManager", "Parsed field '$field' = '${result[field]}'")
                        break
                    }
                }
            }
        }

        android.util.Log.i("VoiceCommandManager", "Parse result: $result")
        return result
    }
}

/**
 * Result types for voice recognition
 */
sealed class VoiceResult {
    object Ready : VoiceResult()
    object Speaking : VoiceResult()
    object Processing : VoiceResult()
    data class Partial(val text: String) : VoiceResult()
    data class Success(val results: List<String>) : VoiceResult()
    data class Error(val message: String) : VoiceResult()
}
