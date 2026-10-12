package com.mini.me_core.feature.packager.domain.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.mini.me_core.core.util.FileLogger
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 自适应图标替换器
 *
 * 模版 APK 的启动图标是一个自适应图标（adaptive-icon）：
 *  - res/BW.xml 是编译后的二进制 XML，引用 background 与 foreground 两个图层；
 *  - foreground 图层在模版中是矢量 drawable（res/Qr.xml）。
 *
 * Android 按文件扩展名决定资源解析方式：把 PNG 字节写入 .xml 文件，系统仍按
 * 二进制 XML 解析，解析失败后回退默认图标。因此不能覆盖 .xml 前景文件。
 *
 * 正确做法（arsc 字符串池重定向）：
 *  1. 解析 res/BW.xml 取出 foreground 的资源 ID，再查 resources.arsc 得到其文件
 *     路径字符串（模版中为 "res/Qr.xml"）；
 *  2. 在 resources.arsc 的全局字符串池中把该路径原地改写为等长的 .png 路径
 *     （"res/Qr.xml" -> "res/Qr.png"，均为 10 字节，不改变字符串池长度与任何偏移）；
 *  3. 把用户图标按自适应图标安全区合成为 432×432 PNG，作为新文件 res/Qr.png
 *     写入解压目录，原 res/Qr.xml 保留不删（已不被引用，无害）；
 *  4. 重新打包时新文件随目录一起进入 APK，aapt/系统按 .png 扩展名以位图解码。
 *
 * 若前景本身就是位图（.png/.webp），则直接按安全区合成后覆盖该文件即可。
 * 任意一步失败都会打日志并返回 false，由调用方决定是否中断构建。
 */
@Suppress("UtilityClassWithPublicConstructor", "LoopWithTooManyJumpStatements")
class IconReplacer {

    companion object {
        private const val TAG = "IconReplacer"

        // === AXML chunk 类型 ===
        private const val RES_XML_TYPE = 0x0003
        private const val RES_STRING_POOL_TYPE = 0x0001
        private const val RES_XML_START_ELEMENT_TYPE = 0x0102

        // === arsc chunk 类型 ===
        private const val RES_TABLE_TYPE = 0x0002
        private const val RES_TABLE_PACKAGE_TYPE = 0x0200
        private const val RES_TABLE_TYPE_TYPE = 0x0201

        // === 字符串池 flag ===
        private const val UTF8_FLAG = 0x100

        // 自适应图标安全区占比：72dp 可见 / 108dp 画布
        private const val SAFE_ZONE_RATIO = 0.66f

        // 矢量前景无法解码尺寸时使用的标准自适应画布（108dp × xxxhdpi 4x）
        private const val STANDARD_CANVAS = 432

        // 当前模版前景的默认路径（arsc 精确解析失败时的兜底）
        private const val DEFAULT_FOREGROUND_PATH = "res/Qr.xml"

        /**
         * 替换模版 APK 的启动图标前景。
         *
         * @param extractedDir 模版 APK 解压根目录（包含 res/ 与 resources.arsc）
         * @param iconFile 用户提供的 PNG/JPG 图标文件
         * @return true 表示替换成功；false 表示任何一步失败
         */
        @JvmStatic
        @Suppress("LongMethod", "ReturnCount", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
        fun replaceIcon(extractedDir: File, iconFile: File): Boolean {
            if (!extractedDir.isDirectory || !iconFile.isFile) {
                FileLogger.e(TAG, "参数非法: extractedDir=${extractedDir.exists()}, iconFile=${iconFile.exists()}")
                return false
            }

            try {
                val arscFile = File(extractedDir, "resources.arsc")
                val resDir = File(extractedDir, "res")

                // 1. 发现前景资源路径（BW.xml -> 资源 ID -> arsc 文件路径）
                val foregroundPath = discoverForegroundPath(extractedDir, arscFile)
                FileLogger.i(TAG, "前景资源路径: $foregroundPath")

                // 2. 解码用户图标
                val userBitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (!isValidBitmap(userBitmap)) {
                    FileLogger.e(TAG, "用户图标解码失败: ${iconFile.absolutePath}")
                    return false
                }

                // 3. 按前景类型选择替换策略
                val targetFile: File
                val canvas: Int
                when {
                    // 3a. 矢量/XML 前景：重定向 arsc 字符串到新的 .png 文件
                    foregroundPath != null && foregroundPath.endsWith(".xml") -> {
                        val newPath = foregroundPath.removeSuffix(".xml") + ".png"
                        if (!redirectArscPath(arscFile, foregroundPath, newPath)) {
                            userBitmap.recycle()
                            return false
                        }
                        targetFile = File(extractedDir, newPath)
                        canvas = STANDARD_CANVAS
                        FileLogger.i(TAG, "arsc 重定向: $foregroundPath -> $newPath")
                    }
                    // 3b. 位图前景：直接覆盖原文件，画布沿用原位图尺寸
                    foregroundPath != null -> {
                        targetFile = File(extractedDir, foregroundPath)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(targetFile.absolutePath, bounds)
                        canvas = if (bounds.outWidth > 0 && bounds.outHeight == bounds.outWidth) {
                            bounds.outWidth
                        } else {
                            STANDARD_CANVAS
                        }
                    }
                    // 3c. 兜底：取 res/ 下面积最大的正方形位图
                    else -> {
                        FileLogger.w(TAG, "精确解析失败，启用兜底扫描 res/ 下最大正方形位图")
                        targetFile = findLargestSquarePng(resDir) ?: run {
                            userBitmap.recycle()
                            FileLogger.e(TAG, "未找到任何候选前景位图")
                            return false
                        }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(targetFile.absolutePath, bounds)
                        canvas = if (bounds.outWidth > 0) bounds.outWidth else STANDARD_CANVAS
                    }
                }

                // 4. 按安全区合成并写回
                val composed = composeSafeZone(userBitmap, canvas, canvas)
                userBitmap.recycle()

                targetFile.parentFile?.mkdirs()
                FileOutputStream(targetFile).use { out ->
                    if (!composed.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        composed.recycle()
                        FileLogger.e(TAG, "PNG 压缩失败: ${targetFile.name}")
                        return false
                    }
                }
                composed.recycle()

                FileLogger.i(
                    TAG,
                    "图标已写入: ${targetFile.name} (${canvas}x${canvas}, 安全区${(SAFE_ZONE_RATIO * 100).toInt()}%)"
                )
                return true
            } catch (e: Exception) {
                FileLogger.e(TAG, "图标替换异常: ${e.message}", e)
                return false
            }
        }

        // ==================== 前景路径发现 ====================

        /**
         * 解析 BW.xml 取 foreground 资源 ID，再查 arsc 得到文件相对路径。
         * 任一环节失败时回退到模版默认路径 [DEFAULT_FOREGROUND_PATH]（若该文件存在）。
         */
        private fun discoverForegroundPath(extractedDir: File, arscFile: File): String? {
            val bwXml = File(extractedDir, "res/BW.xml")
            if (bwXml.isFile && arscFile.isFile) {
                val resId = parseForegroundDrawableResId(bwXml)
                if (resId != -1) {
                    val path = mapResIdToFilePath(arscFile, resId)
                    if (!path.isNullOrBlank() && File(extractedDir, path).exists()) {
                        return path
                    }
                }
            }
            // 兜底：当前模版固定的矢量前景
            val fallback = File(extractedDir, DEFAULT_FOREGROUND_PATH)
            return if (fallback.exists()) DEFAULT_FOREGROUND_PATH else null
        }

        // ==================== arsc 字符串池重定向 ====================

        /**
         * 在 resources.arsc 字节中把 [oldPath] 原地替换为等长的 [newPath]。
         *
         * 要求两者 UTF-8 字节长度完全相同（不改变字符串池长度与后续偏移），
         * 且旧路径在 arsc 中恰好出现一次，否则不做修改并返回 false。
         */
        @Suppress("ReturnCount")
        fun redirectArscPath(arscFile: File, oldPath: String, newPath: String): Boolean {
            if (!arscFile.isFile) {
                FileLogger.e(TAG, "resources.arsc 不存在")
                return false
            }
            val oldBytes = oldPath.toByteArray(Charsets.UTF_8)
            val newBytes = newPath.toByteArray(Charsets.UTF_8)
            if (oldBytes.size != newBytes.size) {
                FileLogger.e(
                    TAG,
                    "路径长度不一致，无法原地重定向: '$oldPath'(${oldBytes.size}) vs '$newPath'(${newBytes.size})"
                )
                return false
            }

            val data = arscFile.readBytes()

            // 统计旧路径出现次数，必须恰好一次
            val occurrences = ArrayList<Int>()
            var from = 0
            while (true) {
                val idx = indexOfBytes(data, oldBytes, from)
                if (idx == -1) break
                occurrences.add(idx)
                from = idx + 1
            }
            if (occurrences.isEmpty()) {
                FileLogger.e(TAG, "arsc 中未找到路径 '$oldPath'")
                return false
            }
            if (occurrences.size > 1) {
                FileLogger.e(TAG, "arsc 中路径 '$oldPath' 出现 ${occurrences.size} 次，拒绝歧义替换")
                return false
            }

            // 原地替换（等长，不改变文件大小）
            System.arraycopy(newBytes, 0, data, occurrences[0], newBytes.size)
            FileOutputStream(arscFile).use { it.write(data) }
            FileLogger.i(TAG, "arsc 字符串池已重定向 @0x${Integer.toHexString(occurrences[0])}")
            return true
        }

        /** 在 [data] 中从 [start] 起查找 [needle]，返回首个下标，未找到返回 -1。 */
        private fun indexOfBytes(data: ByteArray, needle: ByteArray, start: Int): Int {
            outer@ for (i in start..data.size - needle.size) {
                for (j in needle.indices) {
                    if (data[i + j] != needle[j]) continue@outer
                }
                return i
            }
            return -1
        }

        // ==================== AXML 解析 ====================

        /**
         * 从二进制 adaptive-icon XML 中取 foreground 元素 drawable 属性的资源 ID。
         * 遍历 START_ELEMENT chunk，命中 name=="foreground" 后读取 name=="drawable"
         * 属性的 typedValue.data（即资源 ID 整数）。
         */
        private fun parseForegroundDrawableResId(xml: File): Int {
            val data = xml.readBytes()
            if (data.size < 8 || getShortLE(data, 0) != RES_XML_TYPE) {
                FileLogger.e(TAG, "BW.xml 不是合法二进制 XML")
                return -1
            }

            // 先解析字符串池，拿到 name -> index 映射
            val strings = ArrayList<String>()
            var pos = 8
            while (pos + 8 <= data.size) {
                val type = getShortLE(data, pos)
                val headerSize = getShortLE(data, pos + 2)
                val chunkSize = getIntLE(data, pos + 4)
                if (chunkSize <= 0 || pos + chunkSize > data.size) break

                if (type == RES_STRING_POOL_TYPE) {
                    parseStringPool(data, pos, strings)
                } else if (type == RES_XML_START_ELEMENT_TYPE) {
                    val resId = readStartElementDrawableResId(data, pos, strings)
                    if (resId != -1) return resId
                }

                pos += chunkSize
            }
            return -1
        }

        /**
         * 读取 START_ELEMENT chunk。仅当元素名为 foreground 时，扫描其属性中
         * 名为 drawable 的属性，返回 typedValue.data。
         */
        private fun readStartElementDrawableResId(
            data: ByteArray,
            chunkStart: Int,
            strings: List<String>
        ): Int {
            val headerSize = getShortLE(data, chunkStart + 2)
            // ResXMLTree_attrExt 从 chunkStart + headerSize + 8 开始
            val attrExtStart = chunkStart + headerSize + 8
            val elementNameIdx = getIntLE(data, attrExtStart + 4)
            val attributeStart = getShortLE(data, attrExtStart + 8)
            val attributeCount = getShortLE(data, attrExtStart + 12)

            val elementName = strings.getOrNull(elementNameIdx) ?: return -1
            if (elementName != "foreground") return -1

            val attrsStart = attrExtStart + attributeStart
            for (i in 0 until attributeCount) {
                val attrPos = attrsStart + i * 20
                if (attrPos + 20 > data.size) break
                val attrNameIdx = getIntLE(data, attrPos + 4)
                val attrName = strings.getOrNull(attrNameIdx) ?: continue
                if (attrName == "drawable") {
                    // typedValue: size(2) res0(1) dataType(1) data(4)，起始于 attrPos+12
                    return getIntLE(data, attrPos + 16)
                }
            }
            return -1
        }

        // ==================== arsc 解析 ====================

        /**
         * 把资源 ID（如 0x7f040006）映射为其在 APK 中的相对文件路径（如 "res/Qr.xml"）。
         *
         * 资源 ID = 0xppttteeee：tt 是类型 ID（1-based），eeee 是类型内条目索引。
         * 正确链路：解析全局字符串池 -> 遍历默认配置（density==0）的 type chunk ->
         * 读 entry 索引数组（位于 headerSize 之后）-> entry 数据（位于 entriesStart）->
         * 内联 Res_value 的 TYPE_STRING data 即全局字符串池索引。
         */
        @Suppress("ReturnCount", "CyclomaticComplexMethod", "CognitiveComplexMethod", "NestedBlockDepth")
        private fun mapResIdToFilePath(arsc: File, resId: Int): String? {
            val data = arsc.readBytes()
            if (data.size < 12 || getShortLE(data, 0) != RES_TABLE_TYPE) return null

            // 全局字符串池在偏移 12，解析出文件路径字符串数组
            val globalStrings = ArrayList<String>()
            parseStringPool(data, 12, globalStrings)

            // 跳过全局字符串池，定位 package chunk
            val globalPoolSize = getIntLE(data, 12 + 4)
            val packageOffset = 12 + globalPoolSize
            if (packageOffset + 8 > data.size) return null
            if (getShortLE(data, packageOffset) != RES_TABLE_PACKAGE_TYPE) return null

            val packageSize = getIntLE(data, packageOffset + 4)
            val packageHeaderSize = getShortLE(data, packageOffset + 2)

            val targetTypeId = (resId ushr 16) and 0xFF
            val targetEntryIdx = resId and 0xFFFF

            // 遍历 package 内部的 type chunks
            var pos = packageOffset + packageHeaderSize
            val packageEnd = packageOffset + packageSize
            while (pos + 20 <= data.size && pos + 8 <= packageEnd) {
                val chunkType = getShortLE(data, pos)
                val chunkSize = getIntLE(data, pos + 4)
                if (chunkSize <= 0 || pos + chunkSize > data.size) break

                if (chunkType == RES_TABLE_TYPE_TYPE) {
                    val headerSize = getShortLE(data, pos + 2)
                    val typeId = data[pos + 8].toInt() and 0xFF
                    val entryCount = getIntLE(data, pos + 12)
                    val entriesStart = getIntLE(data, pos + 16)
                    // ResTable_config 内 density 在偏移 14（size+0,mcc+4,...,touchscreen+13,density+14）
                    val density = getShortLE(data, pos + 20 + 14)

                    if (typeId == targetTypeId && density == 0 &&
                        targetEntryIdx in 0 until entryCount
                    ) {
                        // entry 索引数组紧跟 header（pos + headerSize），
                        // entriesStart 指向 entry 数据区（索引数组之后）。
                        val entryArrayStart = pos + headerSize
                        if (entryArrayStart + targetEntryIdx * 4 + 4 <= data.size) {
                            val entryOffset = getIntLE(data, entryArrayStart + targetEntryIdx * 4)
                            // 0xFFFFFFFF 表示该条目不存在
                            if (entryOffset != -1) {
                                val entryPos = pos + entriesStart + entryOffset
                                if (entryPos + 16 <= data.size) {
                                    val flags = getShortLE(data, entryPos + 2)
                                    // Res_value 内联在 entry + 8：size(2) res0(1) dataType(1) data(4)
                                    val valueDataType = data[entryPos + 11].toInt() and 0xFF
                                    val valueData = getIntLE(data, entryPos + 12)
                                    if (flags == 0 && valueDataType == 0x03 &&
                                        valueData in globalStrings.indices
                                    ) {
                                        return globalStrings[valueData]
                                    }
                                }
                            }
                        }
                    }
                }

                pos += chunkSize
            }
            FileLogger.w(TAG, "未在 arsc 中找到资源 0x${Integer.toHexString(resId)} 的文件路径")
            return null
        }

        // ==================== 字符串池 ====================

        /**
         * 解析 RES_STRING_POOL chunk，把字符串追加到 [out]。
         * 同时兼容 UTF-16 与 UTF-8 两种编码。
         */
        private fun parseStringPool(data: ByteArray, poolStart: Int, out: MutableList<String>) {
            if (poolStart + 28 > data.size) return
            val headerSize = getShortLE(data, poolStart + 2)
            val stringCount = getIntLE(data, poolStart + 8)
            val flags = getIntLE(data, poolStart + 16)
            val stringsStart = getIntLE(data, poolStart + 20)
            val isUtf8 = (flags and UTF8_FLAG) != 0

            val offsetsBase = poolStart + headerSize
            val stringsDataBase = poolStart + stringsStart

            for (i in 0 until stringCount) {
                val offsetPos = offsetsBase + i * 4
                if (offsetPos + 4 > data.size) break
                var pos = stringsDataBase + getIntLE(data, offsetPos)
                if (pos >= data.size) {
                    out.add("")
                    continue
                }

                val s: String
                if (isUtf8) {
                    // charLen（1或2字节）
                    var charLen = data[pos].toInt() and 0xFF; pos++
                    if (charLen and 0x80 != 0) {
                        charLen = ((charLen and 0x7F) shl 8) or (data[pos].toInt() and 0xFF); pos++
                    }
                    // byteLen（1或2字节）
                    var byteLen = data[pos].toInt() and 0xFF; pos++
                    if (byteLen and 0x80 != 0) {
                        byteLen = ((byteLen and 0x7F) shl 8) or (data[pos].toInt() and 0xFF); pos++
                    }
                    s = String(data, pos, byteLen, Charsets.UTF_8)
                } else {
                    // UTF-16: u16 长度（可能双字节）
                    var len = getShortLE(data, pos); pos += 2
                    if (len and 0x8000 != 0) {
                        len = ((len and 0x7FFF) shl 16) or getShortLE(data, pos); pos += 2
                    }
                    val byteLen = len * 2
                    s = String(data, pos, byteLen, Charsets.UTF_16LE)
                }
                out.add(s)
            }
        }

        // ==================== 前景位图定位（兜底） ====================

        /**
         * 兜底：扫描 res/ 下所有 PNG/webp，取面积最大的正方形那张作为前景图。
         */
        private fun findLargestSquarePng(resDir: File): File? {
            if (!resDir.isDirectory) return null
            var best: File? = null
            var bestArea = 0L
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resDir.listFiles { f -> f.isFile && (f.name.endsWith(".png") || f.name.endsWith(".webp")) }
                ?.forEach { f ->
                    opts.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(f.absolutePath, opts)
                    val w = opts.outWidth
                    val h = opts.outHeight
                    if (w > 0 && h > 0 && w == h) {
                        val area = w.toLong() * h
                        if (area > bestArea) {
                            bestArea = area
                            best = f
                        }
                    }
                }
            return best
        }

        // ==================== 图标合成 ====================

        /** 位图是否可用于合成（非空、未回收、尺寸为正）。 */
        private fun isValidBitmap(bmp: Bitmap?): Boolean {
            return bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0
        }

        /**
         * 把用户图标按自适应图标安全区合成到透明画布上。
         *
         * 用户图标先按比例裁剪填充到画布的 [SAFE_ZONE_RATIO] 区域，再居中绘制，
         * 避免被系统蒙版裁掉边缘。
         */
        private fun composeSafeZone(src: Bitmap, canvasW: Int, canvasH: Int): Bitmap {
            val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)

            val safeW = (canvasW * SAFE_ZONE_RATIO).roundToInt()
            val safeH = (canvasH * SAFE_ZONE_RATIO).roundToInt()

            // 按最大比例缩放 src，使其短边覆盖安全区，长边居中裁剪
            val scale = maxOf(safeW.toFloat() / src.width, safeH.toFloat() / src.height)
            val scaledW = (src.width * scale).roundToInt()
            val scaledH = (src.height * scale).roundToInt()

            val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
            val left = ((canvasW - scaledW) / 2f).roundToInt().toFloat()
            val top = ((canvasH - scaledH) / 2f).roundToInt().toFloat()
            canvas.drawBitmap(scaled, left, top, null)
            if (scaled != src) scaled.recycle()

            return out
        }

        // ==================== 小端序辅助 ====================

        private fun getShortLE(data: ByteArray, offset: Int): Int {
            return (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8)
        }

        private fun getIntLE(data: ByteArray, offset: Int): Int {
            return (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    ((data[offset + 2].toInt() and 0xFF) shl 16) or
                    ((data[offset + 3].toInt() and 0xFF) shl 24)
        }
    }
}
