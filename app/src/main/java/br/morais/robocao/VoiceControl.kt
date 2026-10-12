package br.morais.robocao

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.concurrent.Executors

/**
 * Reconhecimento de voz 100% offline com Vosk. O modelo vem dentro do APK e o app
 * não tem permissão de INTERNET, então o áudio nunca sai do celular.
 *
 * A permissão RECORD_AUDIO é verificada na MainActivity antes de chamar start().
 */
@SuppressLint("MissingPermission")
class VoiceControl(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onVoiceReady()
        fun onVoiceHeard(text: String, command: Int?)
        fun onVoiceError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var model: Model? = null
    private var speechService: SpeechService? = null

    val isListening get() = speechService != null

    /** Prepara o modelo em segundo plano. Na primeira vez copia ~50 MB dos assets (alguns segundos). */
    fun load() {
        if (model != null) {
            listener.onVoiceReady()
            return
        }
        executor.execute {
            try {
                val dir = copyModelIfNeeded()
                val loaded = Model(dir.absolutePath)
                mainHandler.post {
                    model = loaded
                    listener.onVoiceReady()
                }
            } catch (e: Exception) {
                mainHandler.post { listener.onVoiceError(context.getString(R.string.voice_model_failed, e.message)) }
            }
        }
    }

    fun start() {
        val currentModel = model ?: return
        if (speechService != null) return
        try {
            val recognizer = Recognizer(currentModel, SAMPLE_RATE, VoiceCommandParser.grammarJson())
            speechService = SpeechService(recognizer, SAMPLE_RATE).also { it.startListening(recognitionListener) }
        } catch (e: Exception) {
            listener.onVoiceError(context.getString(R.string.voice_mic_failed, e.message))
        }
    }

    fun stop() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }

    fun release() {
        stop()
        executor.shutdown()
        model?.close()
        model = null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResult(hypothesis: String) = handleResult(hypothesis)
        override fun onFinalResult(hypothesis: String) = handleResult(hypothesis)
        override fun onPartialResult(hypothesis: String) = Unit
        override fun onError(exception: Exception) = listener.onVoiceError(context.getString(R.string.voice_recognition_failed, exception.message))
        override fun onTimeout() = Unit
    }

    private fun handleResult(hypothesis: String) {
        val text = JSONObject(hypothesis).optString("text").replace("[unk]", "").trim()
        if (text.isEmpty()) return
        listener.onVoiceHeard(text, VoiceCommandParser.parse(text))
    }

    private fun copyModelIfNeeded(): File {
        val target = File(context.filesDir, MODEL_ASSET_DIR)
        val marker = File(target, ".version-$MODEL_VERSION")
        if (marker.exists()) return target
        target.deleteRecursively()
        copyAssetDir(MODEL_ASSET_DIR, target)
        marker.createNewFile()
        return target
    }

    private fun copyAssetDir(assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> target.outputStream().use { input.copyTo(it) } }
            return
        }
        target.mkdirs()
        children.forEach { copyAssetDir("$assetPath/$it", File(target, it)) }
    }

    companion object {
        private const val SAMPLE_RATE = 16000f
        private const val MODEL_ASSET_DIR = "model-pt"
        private const val MODEL_VERSION = "small-pt-0.3"
    }
}
