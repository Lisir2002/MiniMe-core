package com.mini.me_core.core.viewer.native

import java.nio.ByteBuffer

/**
 * MiniMe native viewer 桥接层：与 C++ libminimeviewer.so 一一对应的 external 声明。
 * 仅做 JNI 映射，业务封装见 [NativeCodeViewer]。
 */
internal object NativeViewerBridge {
    init {
        System.loadLibrary("minimeviewer")
    }

    external fun nativeGetVersion(): String
    external fun nativeGetLastError(): String

    // ── 句柄通用 ──
    external fun nativeClose(handle: Long)

    // ── 文件加载（FileLoader）──
    external fun nativeOpenFile(path: String): Long
    external fun nativeGetFileInfo(handle: Long): String
    external fun nativeGetLineCount(handle: Long): Long
    external fun nativeReadLines(handle: Long, start: Long, end: Long): Array<String>?
    external fun nativeCloseFile(handle: Long)

    /** 零拷贝行读取：行数据按 [4字节小端长度][UTF-8 字节] 填入 DirectByteBuffer。返回行数。 */
    external fun nativeReadLinesDirect(
        handle: Long, start: Long, end: Long, buffer: ByteBuffer
    ): Int

    /** 手动切换编码（0=UTF8 1=UTF16LE 2=UTF16BE 3=GB18030 4=LATIN1）。 */
    external fun nativeSetEncoding(handle: Long, encoding: Int): Boolean

    // ── 代码查看（只读）──
    external fun nativeOpenCodeViewer(path: String, languageHint: String?): Long
    external fun nativeGetSpans(handle: Long): IntArray?
    external fun nativeGetFolds(handle: Long): IntArray?
    external fun nativeGetOutline(handle: Long): String
    external fun nativeCloseCodeViewer(handle: Long)
}
