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

object LocalModelBridge {
    interface Callback { fun onToken(token: String, done: Boolean); fun onError(message: String) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var engine: InferenceEngine? = null
    private var loadedPath: String? = null

    @JvmStatic
    fun startNewConversation() {
        scope.launch {
            val e = engine ?: return@launch
            try {
                if (e.state.value is State.ModelReady || e.state.value is State.Error) {
                    e.cleanUp()
                    loadedPath = null
                }
            } catch (_: IllegalStateException) { }
        }
    }

    @JvmStatic
    fun generate(context: Context, path: String, systemPrompt: String, prompt: String, callback: Callback) {
        scope.launch {
            try {
                val e = engine ?: AiChat.getInferenceEngine(context.applicationContext).also { engine = it }
                var attempts = 0
                while (e.state.value !is State.Initialized && e.state.value !is State.ModelReady) {
                    val state = e.state.value
                    if (state is State.Error) throw state.exception
                    check(attempts++ < 300) { "Native llama.cpp engine did not initialize" }
                    delay(100)
                }
                if (loadedPath != path) { e.loadModel(path); e.setSystemPrompt(systemPrompt); loadedPath = path }
                e.sendUserPrompt(prompt, 384).collect { token -> withContext(Dispatchers.Main) { callback.onToken(token, false) } }
                withContext(Dispatchers.Main) { callback.onToken("", true) }
            } catch (t: Throwable) { withContext(Dispatchers.Main) { callback.onError(t.message ?: "Local inference failed") } }
        }
    }
}
