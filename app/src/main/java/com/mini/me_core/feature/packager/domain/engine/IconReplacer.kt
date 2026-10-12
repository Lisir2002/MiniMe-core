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
 *  - foreground 图层是一张位图（模版中为 res/pu.png，860×860）。
 *
 * 旧实现直接把用户 PNG 字节覆盖到 res/BW.xml，破坏了二进制 XML 结构，
 * 资源解析回退后图标保持不变。正确做法是定位 foreground 引用的位图资源，
 * 把用户图标按自适应图标安全区规则合成后写回该位图。
 *
 * 处理流程：
 *  1. 解析 res/BW.xml 二进制 XML，取出 foreground 元素 drawable 属性的资源 ID；
 *  2. 解析 resources.arsc 的 package 字符串池，把资源 ID 换算成条目名（如 pu）；
 *  3. 定位 res/<entryName>.png；
 *  4. 将用户图标缩放到前景图尺寸、按 66% 安全区居中合成到透明画布；
 *  5. 以 PNG 覆盖写回。
 *
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

        // === 字符串池 flag ===
        private const val UTF8_FLAG = 0x100

        // 自适应图标安全区占比：72dp 可见 / 108dp 画布
        private const val SAFE_ZONE_RATIO = 0.66f

        /**
         * 替换模版 APK 的启动图标前景位图。
         *
         * @param extractedDir 模版 APK 解压根目录（包含 res/ 与 resources.arsc）
         * @param iconFile 用户提供的 PNG/JPG 图标文件
         * @return true 表示成功写回前景位图；false 表示任何一步失败
         */
        @JvmStatic
        @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount", "TooGenericExceptionCaught", "LoopWithTooManyJumpStatements", "ComplexCondition")
        fun replaceIcon(extractedDir: File, iconFile: File): Boolean {
            if (!extractedDir.isDirectory || !iconFile.isFile) {
                FileLogger.e(TAG, "参数非法: extractedDir=${extractedDir.exists()}, iconFile=${iconFile.exists()}")
                return false
            }

            var foregroundFile: File? = null
            var canvasW = 0
            var canvasH = 0

            try {
                // 1. 从 BW.xml 取出 foreground 引用的资源 ID
                val bwXml = File(extractedDir, "res/BW.xml")
                val resId = if (bwXml.isFile) parseForegroundDrawableResId(bwXml) else -1

                // 2. 资源 ID -> 条目名 -> 实际位图文件
                if (resId != -1) {
                    val arsc = File(extractedDir, "resources.arsc")
                    val entryName = if (arsc.isFile) mapResIdToEntryName(arsc, resId) else null
                    if (!entryName.isNullOrBlank()) {
                        foregroundFile = pickDrawableFile(File(extractedDir, "res"), entryName)
                        FileLogger.i(TAG, "前景资源: resId=0x${Integer.toHexString(resId)}, entry=$entryName, file=${foregroundFile?.name}")
                    }
                }

                // 3. 兜底：解析失败时取 res/ 下尺寸最大的正方形 PNG
                if (foregroundFile == null || !foregroundFile!!.isFile) {
                    FileLogger.w(TAG, "精确解析失败，启用兜底扫描 res/ 下最大正方形 PNG")
                    foregroundFile = findLargestSquarePng(File(extractedDir, "res"))
                    if (foregroundFile == null) {
                        FileLogger.e(TAG, "未找到任何候选前景位图")
                        return false
                    }
                }

                // 4. 读取原前景位图尺寸作为画布尺寸
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(foregroundFile!!.absolutePath, bounds)
                canvasW = bounds.outWidth
                canvasH = bounds.outHeight
                if (canvasW <= 0 || canvasH <= 0) {
                    FileLogger.e(TAG, "原前景位图尺寸无效: ${foregroundFile!!.name} ${canvasW}x${canvasH}")
                    return false
                }

                // 5. 解码用户图标并合成
                val userBitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (userBitmap == null || userBitmap.isRecycled || userBitmap.width <= 0 || userBitmap.height <= 0) {
                    FileLogger.e(TAG, "用户图标解码失败: ${iconFile.absolutePath}")
                    return false
                }

                val composed = composeSafeZone(userBitmap, canvasW, canvasH)
                userBitmap.recycle()

                FileOutputStream(foregroundFile!!).use { out ->
                    if (!composed.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        composed.recycle()
                        FileLogger.e(TAG, "PNG 压缩失败: ${foregroundFile!!.name}")
                        return false
                    }
                }
                composed.recycle()

                FileLogger.i(TAG, "图标已写回: ${foregroundFile!!.name} (${canvasW}x${canvasH}, 安全区${(SAFE_ZONE_RATIO * 100).toInt()}%)")
                return true
            } catch (e: Exception) {
                FileLogger.e(TAG, "图标替换异常: ${e.message}", e)
                return false
            }
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
            // chunk header: type(2) headerSize(2) size(4)
            // body: lineNumber(4) comment(4)
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
         * 把资源 ID（如 0x7f040006）映射为条目名（如 "pu"）。
         *
         * 资源 ID = 0xppttteeee：
         *  - tt 为 type index（1-based），查 typeStrings[tt-1-typeIdOffset] 得类型名；
         *  - eeee 为 entry index，查 keyStrings[eeee] 得条目名。
         */
        @Suppress("ReturnCount")
        private fun mapResIdToEntryName(arsc: File, resId: Int): String? {
            val data = arsc.readBytes()
            if (data.size < 12 || getShortLE(data, 0) != RES_TABLE_TYPE) return null

            // 跳过全局字符串池，定位 package chunk
            val globalPoolSize = getIntLE(data, 12 + 4)
            val packageOffset = 12 + globalPoolSize
            if (packageOffset + 288 > data.size) return null
            if (getShortLE(data, packageOffset) != RES_TABLE_PACKAGE_TYPE) return null

            val headerSize = getShortLE(data, packageOffset + 2)
            // ResTable_package: header(8) + id(4) + name[128](256) + typeStrings(4) + ...
            val typeStringsOff = getIntLE(data, packageOffset + 268)
            val keyStringsOff = getIntLE(data, packageOffset + 276)
            val typeIdOffset = if (headerSize >= 292) getIntLE(data, packageOffset + 284) else 0

            val typeStrings = ArrayList<String>()
            parseStringPool(data, packageOffset + typeStringsOff, typeStrings)
            val keyStrings = ArrayList<String>()
            parseStringPool(data, packageOffset + keyStringsOff, keyStrings)

            val typeIdx = ((resId ushr 16) and 0xFF) - 1 - typeIdOffset
            val entryIdx = resId and 0xFFFF

            if (typeIdx < 0 || typeIdx >= typeStrings.size) {
                FileLogger.w(TAG, "type index 越界: $typeIdx / ${typeStrings.size}")
                return null
            }
            if (entryIdx < 0 || entryIdx >= keyStrings.size) {
                FileLogger.w(TAG, "entry index 越界: $entryIdx / ${keyStrings.size}")
                return null
            }
            return keyStrings[entryIdx]
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

        // ==================== 前景位图定位 ====================

        /**
         * 根据条目名在 res/ 下挑出实际位图文件。优先 .png，其次 .webp、.9.png、.jpg。
         */
        private fun pickDrawableFile(resDir: File, entryName: String): File? {
            if (!resDir.isDirectory) return null
            val candidates = listOf(
                "$entryName.png",
                "$entryName.webp",
                "$entryName.9.png",
                "$entryName.jpg",
                "$entryName.jpeg"
            )
            for (name in candidates) {
                val f = File(resDir, name)
                if (f.isFile) return f
            }
            return null
        }

        /**
         * 兜底：扫描 res/ 下所有 PNG，取面积最大的正方形那张作为前景图。
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

        /**
         * 把用户图标按自适应图标安全区合成到透明画布上。
         *
         * 画布尺寸与原前景位图一致（模版为 860×860）；用户图标先按比例裁剪填充到
         * 画布的 [SAFE_ZONE_RATIO] 区域，再居中绘制，避免被系统蒙版裁掉边缘。
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
