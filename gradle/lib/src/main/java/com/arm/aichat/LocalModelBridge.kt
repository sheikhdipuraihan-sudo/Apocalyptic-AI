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
    fun generate(context: Context, path: String, systemPrompt: String, prompt: String, callback: Callback) {
        scope.launch {
            try {
                val e = engine ?: AiChat.getInferenceEngine(context.applicationContext).also { engine = it }
                var ready = false
                repeat(300) {
                    if (e.state.value is State.Initialized || e.state.value is State.ModelReady) { ready = true; return@repeat }
                    delay(100)
                }
                check(ready) { "Native llama.cpp engine did not initialize" }
                if (loadedPath != path) { e.loadModel(path); e.setSystemPrompt(systemPrompt); loadedPath = path }
                e.sendUserPrompt(prompt, 384).collect { token -> withContext(Dispatchers.Main) { callback.onToken(token, false) } }
                withContext(Dispatchers.Main) { callback.onToken("", true) }
            } catch (t: Throwable) { withContext(Dispatchers.Main) { callback.onError(t.message ?: "Local inference failed") } }
        }
    }
}
