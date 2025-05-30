package com.odb2llm.app

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale

data class SentenceSimilarity(
    val mainSentence: String,
    val sentence: String,
    val mainSentenceEmbeddings: String,
    val sentenceEmbeddings: String,
    val resultSimilarity: Double,
)

class MediaPipeEmbeddings {

    companion object {
        const val MODEL_NAME = "universal_sentence_encoder.tflite"
        const val MIN_SIMILARITY_VALUE = 0.95
    }

    private lateinit var textEmbedder: TextEmbedder

    suspend fun setUpMLModel(context: Context) {
        suspendCancellableCoroutine { continuation ->
            try {
                val baseOptions = BaseOptions
                    .builder()
                    .setModelAssetPath(MODEL_NAME)
                    .setDelegate(Delegate.CPU)
                    .build()
                val optionsBuilder =
                    TextEmbedder.TextEmbedderOptions.builder().setBaseOptions(baseOptions)
                val options = optionsBuilder.build()
                textEmbedder = TextEmbedder.createFromOptions(context, options)
                continuation.resume(true)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun getSimilarities(
        mainSentence: String,
        sentences: List<String>
    ): List<SentenceSimilarity> {
        return suspendCancellableCoroutine { continuation ->
            try {
                textEmbedder.let {
                    val normalizedMainSentence = mainSentence.lowercase(Locale.getDefault())
                    val mainSentenceEmbed = getEmbeddings(normalizedMainSentence)

                    val similaritySentences: MutableList<SentenceSimilarity> =
                        ArrayList<SentenceSimilarity>().apply {
                            sentences.forEach {
                                val normalizedSentence = it.lowercase(Locale.getDefault()) // Normalize each sentence
                                val sentenceEmbed = getEmbeddings(normalizedSentence) // Use the normalized version

                                val similarity =
                                    TextEmbedder.cosineSimilarity(mainSentenceEmbed, sentenceEmbed)

                                if (similarity > MIN_SIMILARITY_VALUE) {
                                    this.add(
                                        SentenceSimilarity(
                                            mainSentence = mainSentence,
                                            sentence = it,
                                            mainSentenceEmbeddings = mainSentenceEmbed.toString(),
                                            sentenceEmbeddings = sentenceEmbed.toString(),
                                            resultSimilarity = similarity
                                        )
                                    )
                                }
                            }
                        }

                    similaritySentences.sortByDescending { it.resultSimilarity }
                    continuation.resume(similaritySentences)
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun getEmbeddings(sentence: String) = textEmbedder
        .embed(sentence)
        .embeddingResult()
        .embeddings()
        .first()

}
