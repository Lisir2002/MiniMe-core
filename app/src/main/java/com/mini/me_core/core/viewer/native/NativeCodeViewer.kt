package com.mini.me_core.core.viewer.native

import com.mini.me_core.core.viewer.native.dto.FileInfoDto
import com.mini.me_core.core.viewer.native.dto.FoldRegion
import com.mini.me_core.core.viewer.native.dto.HighlightCategory
import com.mini.me_core.core.viewer.native.dto.HighlightSpan
import com.mini.me_core.core.viewer.native.dto.SymbolNode
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 只读代码查看器的 Kotlin 友好封装（Closeable）。
 * 内部持有 native CodeViewerSession 句柄，close() 幂等释放。
 */
class NativeCodeViewer private constructor(
    private var handle: Long,
    val language: String?,
) : AutoCloseable {

    val spans: List<HighlightSpan>
    val folds: List<FoldRegion>
    val outline: List<SymbolNode>

    init {
        check(handle != 0L) { "native open failed: " + NativeViewerBridge.nativeGetLastError() }
        spans = parseSpans(NativeViewerBridge.nativeGetSpans(handle))
        folds = parseFolds(NativeViewerBridge.nativeGetFolds(handle))
        outline = parseOutline(NativeViewerBridge.nativeGetOutline(handle))
    }

    /** 用 DirectByteBuffer 零拷贝读取 [startLine, endLine) 行文本。 */
    fun readLines(startLine: Long, endLine: Long): List<String> {
        if (handle == 0L) return emptyList()
        // 视口缓冲：预分配足够大的 DirectByteBuffer（每块 ~64KB，按 50 行估算）
        val buf = ByteBuffer.allocateDirect(256 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        val rows = NativeViewerBridge.nativeReadLinesDirect(handle, startLine, endLine, buf)
        if (rows <= 0) return emptyList()
        buf.position(0)
        val out = ArrayList<String>(rows)
        repeat(rows) {
            val len = buf.int  // 4 字节小端长度前缀
            val bytes = ByteArray(len)
            buf.get(bytes)
            out.add(String(bytes, Charsets.UTF_8))
        }
        return out
    }

    override fun close() {
        if (handle != 0L) {
            NativeViewerBridge.nativeCloseCodeViewer(handle)
            handle = 0L
        }
    }

    private fun parseSpans(arr: IntArray?): List<HighlightSpan> {
        if (arr == null || arr.size < 3) return emptyList()
        val n = arr.size / 3
        val out = ArrayList<HighlightSpan>(n)
        for (i in 0 until n) {
            out.add(
                HighlightSpan(
                    arr[i * 3], arr[i * 3 + 1],
                    HighlightCategory.fromId(arr[i * 3 + 2]),
                )
            )
        }
        return out
    }

    private fun parseFolds(arr: IntArray?): List<FoldRegion> {
        if (arr == null || arr.size < 3) return emptyList()
        val n = arr.size / 3
        val out = ArrayList<FoldRegion>(n)
        for (i in 0 until n) {
            out.add(FoldRegion(arr[i * 3], arr[i * 3 + 1], arr[i * 3 + 2]))
        }
        return out
    }

    private fun parseOutline(json: String): List<SymbolNode> {
        val arr = JSONArray(json)
        val out = ArrayList<SymbolNode>(arr.length())
        for (i in 0 until arr.length()) {
            val o: JSONObject = arr.getJSONObject(i)
            out.add(
                SymbolNode(
                    o.getString("name"),
                    o.getInt("startLine"),
                    o.getInt("endLine"),
                    o.getInt("kind"),
                )
            )
        }
        return out
    }

    companion object {
        fun open(path: String, languageHint: String?): NativeCodeViewer {
            val h = NativeViewerBridge.nativeOpenCodeViewer(path, languageHint)
            return NativeCodeViewer(h, languageHint)
        }
    }
}
