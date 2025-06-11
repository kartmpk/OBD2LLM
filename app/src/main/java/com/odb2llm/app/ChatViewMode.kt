package com.odb2llm.app

import android.app.Application
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Instantiates the View Model for the chat view. */
class ChatViewModel constructor(private val application: Application) :
    AndroidViewModel(application) {
    private val ragPipeline = RagPipeline(application)
    internal val messages = emptyList<MessageData>().toMutableStateList()
    private val executorService = Executors.newSingleThreadExecutor()
    private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor()

    private val _lastResponseText = MutableLiveData<String>()
    val lastResponseText: LiveData<String> get() = _lastResponseText

    @SuppressWarnings("FutureReturnValueIgnored")
    fun requestResponse(prompt: String) {
        appendMessage(MessageOwner.User, prompt)
        executorService.submit { viewModelScope.launch { requestResponseFromModel(prompt) } }
    }

    suspend fun requestResponseFromModel(prompt: String) =
        withContext(backgroundExecutor.asCoroutineDispatcher()) {
            ragPipeline.generateResponse(
                prompt,
            ) { response, done ->
                updateLastMessage(MessageOwner.Model, response.text)
                    _lastResponseText.postValue(response.text);
            }
        }

    suspend fun memorizeChunks(filename: String) {
        withContext(backgroundExecutor.asCoroutineDispatcher()) {
            ragPipeline.memorizeChunks(application.applicationContext, filename)
        }
    }

    fun memorizeChunksFromJava(filename: String, onComplete: Runnable) {
        viewModelScope.launch {
            memorizeChunks(filename)
            onComplete.run()
        }
    }

    private fun appendMessage(role: MessageOwner, message: String) {
        messages.add(MessageData(role, message))
    }

    private fun updateLastMessage(role: MessageOwner, message: String) {
        if (messages.isNotEmpty() && messages[messages.lastIndex].owner == role) {
            messages[messages.lastIndex] = MessageData(role, message)
        } else {
            appendMessage(role, message)
        }
    }

}

enum class MessageOwner {
    User,
    Model,
}

data class MessageData(val owner: MessageOwner, val message: String)