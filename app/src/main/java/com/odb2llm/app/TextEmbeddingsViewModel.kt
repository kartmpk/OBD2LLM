package com.odb2llm.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

sealed class State {
    data object Loading : State()
    data object Success : State()
    data object Error : State()
    data object Empty : State()
}

data class SentenceWithCode(val code: String, val sentence: String)

data class TextEmbeddingsUiState(
    val sentencesWithCodes: List<SentenceWithCode> = emptyList(),  // Define the list here
    val similaritySentences: List<SentenceSimilarity> = emptyList(),
    val state: State = State.Empty,
    val errorMessage: String = String()
)

class TextEmbeddingsViewModel : ViewModel() {

    private lateinit var mediaPipeEmbeddings: MediaPipeEmbeddings

    private var uiStateTextEmbeddings by mutableStateOf(TextEmbeddingsUiState(state = State.Empty))

    fun setUpMLModel(context: Context) {
        mediaPipeEmbeddings = MediaPipeEmbeddings()
        uiStateTextEmbeddings = uiStateTextEmbeddings.copy(
            state = State.Loading,
            similaritySentences = emptyList()
        )
        viewModelScope.launch {
            mediaPipeEmbeddings.setUpMLModel(context)
            uiStateTextEmbeddings = uiStateTextEmbeddings.copy(
                sentencesWithCodes = listOf(
                    SentenceWithCode("0101", "check Engine light on"),
                    SentenceWithCode("0101", "Check if malfunction indicator light is on"),
                    SentenceWithCode("0101", "Get engine warning status"),
                    SentenceWithCode("0101", "Is the check engine light active?"),

                    SentenceWithCode("0102", "Read DTC error codes"),
                    SentenceWithCode("0102", "Get diagnostic trouble codes"),
                    SentenceWithCode("0102", "Show vehicle health status"),
                    SentenceWithCode("0102", "Scan for check engine light codes"),
                    SentenceWithCode("0102", "Retrieve fault codes"),
                    SentenceWithCode("0102", "Read engine error codes"),

                    SentenceWithCode("0103", "Read fuel system status"),
                    SentenceWithCode("0103", "Get fuel system condition"),
                    SentenceWithCode("0103", "Show fuel system state"),

                    SentenceWithCode("0104", "Read engine load"),
                    SentenceWithCode("0104", "Get calculated load value"),
                    SentenceWithCode("0104", "Show engine load percentage"),

                    SentenceWithCode("0105", "Read engine coolant temperature"),
                    SentenceWithCode("0105", "Get coolant temp"),
                    SentenceWithCode("0105", "Show engine temperature"),
                    SentenceWithCode("0105", "What is the coolant temperature?"),

                    SentenceWithCode("0106", "Read short term fuel trim for Bank 1"),
                    SentenceWithCode("0106", "Get STFT Bank 1"),
                    SentenceWithCode("0106", "Show short term fuel trim bank one"),

                    SentenceWithCode("0107", "Read long term fuel trim for Bank 1"),
                    SentenceWithCode("0107", "Get LTFT Bank 1"),
                    SentenceWithCode("0107", "Show long term fuel trim bank one"),

                    SentenceWithCode("0108", "Read short term fuel trim for Bank 2"),
                    SentenceWithCode("0108", "Get STFT Bank 2"),
                    SentenceWithCode("0108", "Show short term fuel trim bank two"),

                    SentenceWithCode("0109", "Read long term fuel trim for Bank 2"),
                    SentenceWithCode("0109", "Get LTFT Bank 2"),
                    SentenceWithCode("0109", "Show long term fuel trim bank two"),

                    SentenceWithCode("010A", "Read fuel pressure"),
                    SentenceWithCode("010A", "Get fuel rail pressure"),
                    SentenceWithCode("010A", "Show fuel system pressure"),

                    SentenceWithCode("010B", "Read intake manifold pressure"),
                    SentenceWithCode("010B", "Get manifold absolute pressure"),
                    SentenceWithCode("010B", "Show intake pressure"),

                    SentenceWithCode("010C", "Read engine rpm"),
                    SentenceWithCode("010C", "Get revolutions per minute"),
                    SentenceWithCode("010C", "Show current RPM"),
                    SentenceWithCode("010C", "What is the engine speed?"),

                    SentenceWithCode("010D", "Read vehicle speed"),
                    SentenceWithCode("010D", "Get car speed"),
                    SentenceWithCode("010D", "Show speedometer value"),
                    SentenceWithCode("010D", "What is the vehicle velocity?"),

                    SentenceWithCode("010E", "Read timing advance"),
                    SentenceWithCode("010E", "Get ignition timing advance"),
                    SentenceWithCode("010E", "Show timing advance degrees"),

                    SentenceWithCode("010F", "Read intake air temperature"),
                    SentenceWithCode("010F", "Get air intake temp"),
                    SentenceWithCode("010F", "Show intake air temp"),

                    SentenceWithCode("0110", "Read MAF air flow rate"),
                    SentenceWithCode("0110", "Get mass air flow"),
                    SentenceWithCode("0110", "Show air flow sensor data"),

                    SentenceWithCode("0111", "Read throttle position"),
                    SentenceWithCode("0111", "Get absolute throttle position"),
                    SentenceWithCode("0111", "Show throttle opening percentage"),

                    SentenceWithCode("0112", "Read commanded secondary air status"),
                    SentenceWithCode("0112", "Get secondary air injection status"),
                    SentenceWithCode("0112", "Show status of secondary air system"),

                    SentenceWithCode("0113", "How many oxygen sensors are present in the 2 banks"),
                    SentenceWithCode("0113", "Number of O2 sensors in banks"),
                    SentenceWithCode("0113", "Count oxygen sensors"),

                    SentenceWithCode("0114", "Read status of Oxygen Sensor 1"),
                    SentenceWithCode("0114", "Get oxygen sensor bank 1 sensor 1 status"),
                    SentenceWithCode("0114", "Show O2 sensor 1 data"),

                    SentenceWithCode("0115", "Read status of Oxygen Sensor 2"),
                    SentenceWithCode("0115", "Get oxygen sensor bank 1 sensor 2 status"),
                    SentenceWithCode("0115", "Show O2 sensor 2 data")
                ),
                state = State.Empty
            )
        }
    }

    fun calculateSimilarity(mainSentence: String): String {
        val deferred = CompletableDeferred<String>()
        val similarityThreshold = 0.95  // Fixed threshold value

        uiStateTextEmbeddings = uiStateTextEmbeddings.copy(
            state = State.Loading,
            similaritySentences = emptyList()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sentences = uiStateTextEmbeddings.sentencesWithCodes.map { it.sentence }
                val similarities = mediaPipeEmbeddings.getSimilarities(mainSentence, sentences)

                // Map similarities and filter by threshold
                val sentencesWithSimilarity = similarities.mapNotNull { similarity ->
                    if (similarity.resultSimilarity >= similarityThreshold) {
                        similarity.copy(
                            mainSentenceEmbeddings = similarity.mainSentenceEmbeddings,
                            sentenceEmbeddings = similarity.sentenceEmbeddings,
                            resultSimilarity = similarity.resultSimilarity
                        )
                    } else {
                        null // Ignore if below the threshold
                    }
                }

                val mostSimilarSentence = sentencesWithSimilarity.maxByOrNull { it.resultSimilarity }

                val codeOfMostSimilarSentence = mostSimilarSentence?.let {
                    uiStateTextEmbeddings.sentencesWithCodes.find { code -> code.sentence == it.sentence }?.code
                } ?: "No match found"

                Log.d(OBDUtils.TAG, "Most Similar Sentence: ${mostSimilarSentence?.sentence}")
                Log.d(OBDUtils.TAG, "Code of Most Similar Sentence: $codeOfMostSimilarSentence")

                uiStateTextEmbeddings = uiStateTextEmbeddings.copy(
                    state = State.Success,
                    similaritySentences = sentencesWithSimilarity,
                    errorMessage = codeOfMostSimilarSentence
                )

                deferred.complete(codeOfMostSimilarSentence)
            } catch (e: Exception) {
                uiStateTextEmbeddings = uiStateTextEmbeddings.copy(
                    state = State.Error,
                    errorMessage = e.message ?: "Error getting similarities"
                )
                deferred.complete("Error: ${e.message}")
            }
        }

        return runBlocking { deferred.await() }
    }


}
