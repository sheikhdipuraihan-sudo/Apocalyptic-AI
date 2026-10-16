package com.arm.aichat

import android.content.Context
import com.arm.aichat.InferenceEngine.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object LocalModelBridge {
    interface Callback { fun onToken(token: String, done: Boolean); fun onError(message: String) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    @Volatile private var engine: InferenceEngine? = null
    private var loadedPath: String? = null
    private var loadedContextSize: Int? = null
    private var loadedSystemPrompt: String? = null
    private val stopRequested = AtomicBoolean(false)
    private val cancellationLock = Any()

    @JvmStatic
    fun cancelGeneration() {
        synchronized(cancellationLock) {
            stopRequested.set(true)
            engine?.cancelGeneration()
        }
    }

    @JvmStatic
    fun startNewConversation() {
        scope.launch {
            val e = engine ?: return@launch
            try {
                if (e.state.value is State.ModelReady || e.state.value is State.Error) {
                    e.cleanUp()
                    loadedPath = null
                    loadedContextSize = null
                    loadedSystemPrompt = null
                }
            } catch (_: IllegalStateException) { }
        }
    }

    @JvmStatic
    fun unloadModel() {
        cancelGeneration()
        scope.launch {
            val e = engine ?: return@launch
            try {
                if (e.state.value is State.ModelReady || e.state.value is State.Error) {
                    e.cleanUp()
                    loadedPath = null
                    loadedContextSize = null
                    loadedSystemPrompt = null
                }
            } catch (_: IllegalStateException) { }
        }
    }

    @JvmStatic
    fun generate(context: Context, path: String, systemPrompt: String, prompt: String, maxNewTokens: Int,
                 contextSize: Int, temperature: Float, callback: Callback) {
        synchronized(cancellationLock) { stopRequested.set(false) }
        scope.launch {
            try {
                val e = engine ?: AiChat.getInferenceEngine(context.applicationContext).also { engine = it }
                if (e.state.value is State.Error) {
                    e.cleanUp()
                    loadedPath = null
                    loadedContextSize = null
                    loadedSystemPrompt = null
                }
                var attempts = 0
                while (e.state.value !is State.Initialized && e.state.value !is State.ModelReady) {
                    if (stopRequested.get()) {
                        withContext(Dispatchers.Main) { callback.onToken("", true) }
                        return@launch
                    }
                    val state = e.state.value
                    if (state is State.Error) {
                        e.cleanUp()
                        loadedPath = null
                        loadedContextSize = null
                        loadedSystemPrompt = null
                        break
                    }
                    check(attempts++ < 300) { "Native llama.cpp engine did not initialize" }
                    delay(100)
                }
                if (loadedPath != path || loadedContextSize != contextSize || loadedSystemPrompt != systemPrompt) {
                    if (e.state.value is State.ModelReady || e.state.value is State.Error) e.cleanUp()
                    e.loadModel(path, contextSize)
                    e.setSystemPrompt(systemPrompt)
                    loadedPath = path
                    loadedContextSize = contextSize
                    loadedSystemPrompt = systemPrompt
                }
                if (stopRequested.get()) {
                    withContext(Dispatchers.Main) { callback.onToken("", true) }
                    return@launch
                }
                val canStart = synchronized(cancellationLock) {
                    if (stopRequested.get()) false
                    else { e.resetCancellation(); true }
                }
                if (!canStart) {
                    withContext(Dispatchers.Main) { callback.onToken("", true) }
                    return@launch
                }
                e.sendUserPrompt(prompt, maxNewTokens, temperature).collect { token ->
                    withContext(Dispatchers.Main) { callback.onToken(token, false) }
                }
                withContext(Dispatchers.Main) { callback.onToken("", true) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { callback.onError(t.message ?: "Local inference failed") }
            }
        }
    }
}
