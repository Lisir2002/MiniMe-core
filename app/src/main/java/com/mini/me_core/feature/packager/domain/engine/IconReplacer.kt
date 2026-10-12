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
        private const val RES_TABLE_TYPE_TYPE = 0x0201

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

                // 2. 资源 ID -> resources.arsc 中的实际文件路径（如 res/Qr.xml）
                if (resId != -1) {
                    val arsc = File(extractedDir, "resources.arsc")
                    val foregroundPath = if (arsc.isFile) mapResIdToFilePath(arsc, resId) else null
                    if (!foregroundPath.isNullOrBlank()) {
                        foregroundFile = File(extractedDir, foregroundPath)
                        FileLogger.i(
                            TAG,
                            "前景资源: resId=0x${Integer.toHexString(resId)} -> $foregroundPath"
                        )
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

                // 4. 读取原前景文件尺寸作为画布尺寸
                //    若前景是 vector/shape drawable XML，BitmapFactory 无法解码，
                //    使用自适应图标标准画布尺寸兜底
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(foregroundFile!!.absolutePath, bounds)
                canvasW = bounds.outWidth
                canvasH = bounds.outHeight
                if (canvasW <= 0 || canvasH <= 0) {
                    // vector/shape XML 前景：自适应图标前景层标准为 108dp，
                    // 以 xxxhdpi(4x) 换算为 432px 作为画布，保证各密度清晰
                    canvasW = 432
                    canvasH = 432
                    FileLogger.i(TAG, "前景为矢量/XML，使用标准画布 ${canvasW}x${canvasH}")
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
         * 把资源 ID（如 0x7f040006）映射为其在 APK 中的相对文件路径（如 "res/Qr.xml"）。
         *
         * 资源 ID = 0xppttteeee：tt 是类型 ID（1-based），eeee 是类型内条目索引。
         * 不能直接用 keyStrings[eeee] 查条目名——keyStrings 的下标是 ResTable_entry
         * 里的 keyIdx，不是条目数组下标。正确链路：
         *  1. 解析全局字符串池（value strings，含 "res/Qr.xml" 这类文件路径）；
         *  2. 在 package chunk 之后遍历 RES_TABLE_TYPE_TYPE(0x0201) chunk；
         *  3. 找到 chunk.id == tt 且 density == 0（默认配置）的那个；
         *  4. entry 数组第 eeee 项的偏移指向 ResTable_entry，其内联 Res_value
         *     （flags==0 时）dataType==0x03(TYPE_STRING)，data 即全局字符串池索引；
         *  5. 返回全局字符串池[data]。
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
                    // ResTable_config 紧随其后（config 内偏移：size+0,mcc+4,mnc+6,language+8,
                    // country+10,orientation+12,touchscreen+13,density+14,keyboard+16...）
                    val density = getShortLE(data, pos + 20 + 14)

                    if (typeId == targetTypeId && density == 0 &&
                        targetEntryIdx in 0 until entryCount
                    ) {
                        // entry 索引数组紧跟 header 之后（pos + headerSize），
                        // entriesStart 是 entry 数据区的起始偏移（索引数组之后）。
                        val entryArrayStart = pos + headerSize
                        if (entryArrayStart + targetEntryIdx * 4 + 4 <= data.size) {
                            val entryOffset = getIntLE(data, entryArrayStart + targetEntryIdx * 4)
                            // 0xFFFFFFFF 表示该条目不存在
                            if (entryOffset != -1) {
                                val entryPos = pos + entriesStart + entryOffset
                                if (entryPos + 16 <= data.size) {
                                    val flags = getShortLE(data, entryPos + 2)
                                    val keyIdx = getIntLE(data, entryPos + 4)
                                    // Res_value 内联在 entry + 8：size(2) res0(1) dataType(1) data(4)
                                    val valueDataType = data[entryPos + 11].toInt() and 0xFF
                                    val valueData = getIntLE(data, entryPos + 12)
                                    if (flags == 0 && valueDataType == 0x03 &&
                                        valueData in globalStrings.indices
                                    ) {
                                        val path = globalStrings[valueData]
                                        FileLogger.i(
                                            TAG,
                                            "arsc: type=$typeId entry=$targetEntryIdx " +
                                                "keyIdx=$keyIdx -> $path"
                                        )
                                        return path
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

        // ==================== 前景位图定位 ====================

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
