package com.mini.me_core.feature.agent.presentation.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.mini.me_core.core.util.FileLogger
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 消息朗读（TTS）封装。
 *
 * 设计要点：
 * - [android.speech.tts.TextToSpeech] 初始化异步，这里用回调桥接为「调用即读」；
 * - 同一时刻只朗读一条消息，[speak] 会先 [stop] 掉上一条，避免叠音；
 * - Markdown 正文里的代码块/表格/链接标记会被剥离，只朗读自然语言部分（否则引擎会念出星号和井号）；
 * - 无 TTS 引擎或初始化失败时静默降级，不打断对话流程。
 */
class MessageSpeechReader(context: Context) {

    private val appContext = context.applicationContext
    private val initialized = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)

    private var tts: TextToSpeech? = null

    /** 朗读完成回调（TTS 回调在 binder 线程，需切回主线程）。 */
    private var pendingDone: (() -> Unit)? = null

    /** 是否可用（引擎已就绪且未失败）。 */
    val isAvailable: Boolean get() = initialized.get() && !failed.get()

    /** 朗读纯文本（内部自动清洗 Markdown 标记）。 */
    fun speak(rawText: String, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        val text = sanitizeForSpeech(rawText)
        if (text.isBlank()) {
            onDone()
            return
        }
        pendingDone = onDone
        val engine = tts
        if (engine == null) {
            // 首次调用（或上次失败后重试）：初始化并在其回调中继续朗读。
            if (failed.get()) {
                pendingDone = null
                onDone()
                return
            }
            init(onReady = { speak(text, onStart, onDone) }, onFail = onDone)
            return
        }
        engine.stop()
        val r = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (r != TextToSpeech.SUCCESS) {
            FileLogger.e(TAG, "TTS speak 返回非 SUCCESS：$r")
            pendingDone = null
            onDone()
            return
        }
        onStart()
    }

    /** 停止朗读。 */
    fun stop() {
        try {
            tts?.stop()
        } catch (e: IllegalStateException) {
            // 引擎在 shutdown 后调用 stop/speak 会抛 IllegalStateException，属预期降级路径
            FileLogger.e(TAG, "TTS stop 失败", e)
        }
        pendingDone = null
    }

    /** 释放引擎（页面销毁时调用）。 */
    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: IllegalStateException) {
            FileLogger.e(TAG, "TTS shutdown 失败", e)
        }
        tts = null
        initialized.set(false)
    }

    /** 把 Markdown 正文清洗为适合朗读的纯文本。 */
    fun sanitizeForSpeech(raw: String): String {
        var s = raw
        // 代码块整体剔除（模型输出里代码占比高，逐字朗读体验最差）
        s = Regex("(?s)```.*?```").replace(s, " ")
        // 未闭合的代码围栏：其后内容全部丢弃
        val fence = s.indexOf("```")
        if (fence >= 0) s = s.substring(0, fence)
        // 图片
        s = Regex("!\\[[^\\]]*]\\([^)]*\\)").replace(s, " ")
        // 行内代码保留内容
        s = s.replace("`", "")
        // 链接只留文字部分
        s = Regex("\\[([^]]*)]\\([^)]*\\)").replace(s, "$1")
        // 裸 URL 剔除
        s = Regex("https?://\\S+").replace(s, " ")
        // 标题井号
        s = Regex("(?m)^\\s*#{1,6}\\s*").replace(s, "")
        // 强调符号
        s = s.replace("**", "").replace("__", "")
        // 引用与列表符号
        s = Regex("(?m)^\\s*>\\s?").replace(s, "")
        s = Regex("(?m)^\\s*[-*+]\\s+").replace(s, "")
        s = Regex("(?m)^\\s*\\d+[.)]\\s+").replace(s, "")
        // 表格分隔线
        s = Regex("(?m)^\\s*\\|?\\s*[-: ]+\\|[-| :]*$").replace(s, " ")
        // 水平分隔线
        s = Regex("(?m)^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$").replace(s, " ")
        // 压缩空白
        s = s.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{2,}"), "\n").trim()
        return if (s.length > MAX_SPEECH_CHARS) s.take(MAX_SPEECH_CHARS) else s
    }

    private fun init(onReady: () -> Unit, onFail: () -> Unit) {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    failed.set(true)
                    FileLogger.e(TAG, "TTS 初始化失败，status=$status")
                    pendingDone = null
                    onFail()
                    return@TextToSpeech
                }
                val langResult = tts?.setLanguage(Locale.getDefault())
                    ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    failed.set(true)
                    FileLogger.e(TAG, "TTS 不支持当前语言 Locale：${Locale.getDefault()}")
                    pendingDone = null
                    onFail()
                    return@TextToSpeech
                }
                installProgressListener()
                failed.set(false)
                initialized.set(true)
                onReady()
            }
        } catch (e: IllegalStateException) {
            // 设备无 TTS 引擎时构造即抛异常，属预期降级路径
            failed.set(true)
            FileLogger.e(TAG, "TTS 构造异常", e)
            pendingDone = null
            onFail()
        }
    }

    private fun installProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == UTTERANCE_ID) {
                    pendingDone?.invoke()
                    pendingDone = null
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == UTTERANCE_ID) {
                    pendingDone?.invoke()
                    pendingDone = null
                }
            }
        })
    }

    private companion object {
        const val TAG = "MessageSpeechReader"

        /** 单次朗读的最大字符数：超出部分会被截断，避免引擎长文本超时无响应。 */
        const val MAX_SPEECH_CHARS = 4_000

        const val UTTERANCE_ID = "mini_me_message"
    }
}
