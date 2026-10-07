package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.core.util.FileLogger
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 二进制 AndroidManifest.xml (AXML) 修改器
 *
 * 支持修改：
 * - package 属性（包名）
 * - android:label 属性（应用名）
 *
 * AXML 格式：
 * - 文件头：magic(0x00080003) + file_size
 * - String Pool Chunk：字符串池，所有文本存储于此
 * - Resource Map Chunk：资源ID映射（可选）
 * - XML Content Chunks：START_NAMESPACE / START_ELEMENT / END_ELEMENT / TEXT
 *
 * 修改策略：
 * 1. 解析 String Pool，找到目标字符串
 * 2. 若新字符串编码后长度 ≤ 旧字符串，原地替换（后面补零）
 * 3. 若新字符串更长，重建整个 String Pool 并重新组装文件
 */
class ManifestEditor {

    companion object {
        private const val TAG = "ManifestEditor"

        /** AXML 文件魔数 */
        private const val AXML_MAGIC = 0x00080003

        /** String Pool chunk 类型 */
        private const val CHUNK_STRING_POOL = 0x0001

        /** String Pool 标志：UTF-8 编码 */
        private const val FLAG_UTF8 = 0x00000100

        /**
         * 修改 APK 中 AndroidManifest.xml 的包名和应用名
         *
         * @param manifestFile AndroidManifest.xml 文件（二进制 AXML 格式）
         * @param newPackageName 新包名
         * @param newAppName 新应用名
         * @return 是否有修改
         */
        fun modifyManifest(
            manifestFile: File,
            newPackageName: String,
            newAppName: String
        ): Boolean {
            FileLogger.d(TAG, "修改 AndroidManifest.xml: 包名=$newPackageName, 应用名=$newAppName")
            val data = manifestFile.readBytes()
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // 验证 AXML 魔数
            val magic = buffer.int
            if (magic != AXML_MAGIC) {
                throw IllegalArgumentException("不是有效的 AXML 文件，magic=0x${magic.toString(16)}")
            }
            val fileSize = buffer.int

            // 解析 String Pool
            val stringPoolStart = buffer.position()
            val spChunkType = buffer.short.toInt() and 0xFFFF
            if (spChunkType != CHUNK_STRING_POOL) {
                throw IllegalArgumentException("期望 String Pool chunk，实际=0x${spChunkType.toString(16)}")
            }
            val spHeaderSize = buffer.short.toInt() and 0xFFFF
            val spChunkSize = buffer.int
            val stringCount = buffer.int
            val styleCount = buffer.int
            val flags = buffer.int
            val stringsStart = buffer.int
            val stylesStart = buffer.int

            val isUtf8 = (flags and FLAG_UTF8) != 0

            // 读取字符串偏移量表
            val stringOffsets = IntArray(stringCount)
            for (i in 0 until stringCount) {
                stringOffsets[i] = buffer.int
            }

            // 跳过 style 偏移量
            buffer.position(stringPoolStart + stringsStart)

            // 读取所有字符串
            val strings = Array(stringCount) { "" }
            val stringByteLengths = IntArray(stringCount)
            for (i in 0 until stringCount) {
                val strStart = stringPoolStart + stringsStart + stringOffsets[i]
                buffer.position(strStart)
                if (isUtf8) {
                    val (str, byteLen) = readUtf8String(buffer)
                    strings[i] = str
                    stringByteLengths[i] = byteLen
                } else {
                    val (str, byteLen) = readUtf16String(buffer)
                    strings[i] = str
                    stringByteLengths[i] = byteLen
                }
            }

            // 找到需要修改的字符串索引
            val packageIndex = strings.indexOfFirst { it == "com.minime.template" }
            val appNameIndex = strings.indexOfFirst { it == "MiniMe Template" }

            if (packageIndex < 0 && appNameIndex < 0) {
                // 没有找到目标字符串，可能模版已被修改过
                FileLogger.w(TAG, "未找到目标字符串（com.minime.template / MiniMe Template），模版可能已被修改")
                return false
            }

            FileLogger.d(TAG, "找到目标字符串: 包名索引=$packageIndex, 应用名索引=$appNameIndex")

            // 判断是否需要重建 String Pool
            var needRebuild = false
            if (packageIndex >= 0) {
                val newLen = if (isUtf8) newPackageName.toByteArray(Charsets.UTF_8).size else newPackageName.length * 2
                if (newLen > stringByteLengths[packageIndex]) needRebuild = true
            }
            if (appNameIndex >= 0) {
                val newLen = if (isUtf8) newAppName.toByteArray(Charsets.UTF_8).size else newAppName.length * 2
                if (newLen > stringByteLengths[appNameIndex]) needRebuild = true
            }

            val resultData = if (needRebuild) {
                FileLogger.d(TAG, "新字符串更长，需要重建 String Pool")
                rebuildStringPool(
                    data, buffer, stringPoolStart, spHeaderSize, spChunkSize,
                    stringCount, styleCount, flags, stringsStart, stylesStart,
                    stringOffsets, strings, isUtf8,
                    packageIndex, newPackageName,
                    appNameIndex, newAppName
                )
            } else {
                FileLogger.d(TAG, "新字符串长度足够，原地替换")
                // 原地替换
                val newData = data.copyOf()
                val newBuffer = ByteBuffer.wrap(newData).order(ByteOrder.LITTLE_ENDIAN)

                if (packageIndex >= 0) {
                    val strStart = stringPoolStart + stringsStart + stringOffsets[packageIndex]
                    replaceStringInPlace(newBuffer, strStart, strings[packageIndex], newPackageName, isUtf8)
                }
                if (appNameIndex >= 0) {
                    val strStart = stringPoolStart + stringsStart + stringOffsets[appNameIndex]
                    replaceStringInPlace(newBuffer, strStart, strings[appNameIndex], newAppName, isUtf8)
                }
                newData
            }

            manifestFile.writeBytes(resultData)
            FileLogger.d(TAG, "AndroidManifest.xml 修改完成")
            return true
        }

        /**
         * 读取 UTF-8 字符串
         * 返回 (字符串, 编码后字节长度，包含长度前缀和结束符)
         */
        private fun readUtf8String(buffer: ByteBuffer): Pair<String, Int> {
            val startPos = buffer.position()

            // 字符长度（1或2字节）
            var charLen = buffer.get().toInt() and 0xFF
            if (charLen and 0x80 != 0) {
                charLen = ((charLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
            }

            // 字节长度（1或2字节）
            var byteLen = buffer.get().toInt() and 0xFF
            if (byteLen and 0x80 != 0) {
                byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
            }

            val bytes = ByteArray(byteLen)
            buffer.get(bytes)
            // 跳过结束符 0x00
            buffer.get()

            val str = String(bytes, Charsets.UTF_8)
            val totalLen = buffer.position() - startPos
            return Pair(str, totalLen)
        }

        /**
         * 读取 UTF-16 字符串
         * 返回 (字符串, 编码后字节长度，包含长度前缀和结束符)
         */
        private fun readUtf16String(buffer: ByteBuffer): Pair<String, Int> {
            val startPos = buffer.position()

            // 字符串长度（2字节，如果 > 0x7FFF 则用 4 字节）
            var charLen = buffer.short.toInt() and 0xFFFF
            if (charLen and 0x8000 != 0) {
                charLen = ((charLen and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
            }

            val chars = CharArray(charLen)
            for (i in 0 until charLen) {
                chars[i] = buffer.short.toChar()
            }
            // 跳过结束符 0x0000
            buffer.short

            val str = String(chars)
            val totalLen = buffer.position() - startPos
            return Pair(str, totalLen)
        }

        /**
         * 原地替换字符串（新字符串长度 ≤ 旧字符串）
         */
        private fun replaceStringInPlace(
            buffer: ByteBuffer,
            strStart: Int,
            oldStr: String,
            newStr: String,
            isUtf8: Boolean
        ) {
            buffer.position(strStart)

            if (isUtf8) {
                // 读取旧的字符长度和字节长度位置
                val charLenPos = strStart
                var oldCharLen = buffer.get().toInt() and 0xFF
                var charLenBytes = 1
                if (oldCharLen and 0x80 != 0) {
                    oldCharLen = ((oldCharLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                    charLenBytes = 2
                }

                val byteLenPos = buffer.position()
                var oldByteLen = buffer.get().toInt() and 0xFF
                var byteLenBytes = 1
                if (oldByteLen and 0x80 != 0) {
                    oldByteLen = ((oldByteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                    byteLenBytes = 2
                }

                val dataPos = buffer.position()
                val newBytes = newStr.toByteArray(Charsets.UTF_8)

                // 写入新长度（保持原有的长度字节数）
                buffer.position(charLenPos)
                if (charLenBytes == 1) {
                    buffer.put(newStr.length.toByte())
                } else {
                    buffer.put(((newStr.length shr 8) or 0x80).toByte())
                    buffer.put((newStr.length and 0xFF).toByte())
                }
                buffer.position(byteLenPos)
                if (byteLenBytes == 1) {
                    buffer.put(newBytes.size.toByte())
                } else {
                    buffer.put(((newBytes.size shr 8) or 0x80).toByte())
                    buffer.put((newBytes.size and 0xFF).toByte())
                }

                // 写入新字符串数据
                buffer.position(dataPos)
                buffer.put(newBytes)
                // 剩余空间补零
                for (i in newBytes.size until oldByteLen) {
                    buffer.put(0)
                }
                // 写入结束符
                buffer.put(0)
            } else {
                // UTF-16
                val lenPos = strStart
                var oldCharLen = buffer.short.toInt() and 0xFFFF
                var lenBytes = 2
                if (oldCharLen and 0x8000 != 0) {
                    oldCharLen = ((oldCharLen and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
                    lenBytes = 4
                }

                val dataPos = buffer.position()
                val newChars = newStr.toCharArray()

                // 写入新长度
                buffer.position(lenPos)
                if (lenBytes == 2) {
                    buffer.putShort(newChars.size.toShort())
                } else {
                    buffer.putShort(((newChars.size shr 16) or 0x8000).toShort())
                    buffer.putShort((newChars.size and 0xFFFF).toShort())
                }

                // 写入新字符串
                buffer.position(dataPos)
                for (c in newChars) {
                    buffer.putShort(c.code.toShort())
                }
                // 剩余空间补零
                for (i in newChars.size until oldCharLen) {
                    buffer.putShort(0)
                }
                // 写入结束符
                buffer.putShort(0)
            }
        }

        /**
         * 重建 String Pool（当新字符串比旧字符串长时）
         */
        private fun rebuildStringPool(
            originalData: ByteArray,
            originalBuffer: ByteBuffer,
            stringPoolStart: Int,
            spHeaderSize: Int,
            oldSpChunkSize: Int,
            stringCount: Int,
            styleCount: Int,
            flags: Int,
            oldStringsStart: Int,
            stylesStart: Int,
            oldStringOffsets: IntArray,
            oldStrings: Array<String>,
            isUtf8: Boolean,
            packageIndex: Int,
            newPackageName: String,
            appNameIndex: Int,
            newAppName: String
        ): ByteArray {
            // 构建新的字符串数组
            val newStrings = oldStrings.copyOf()
            if (packageIndex >= 0) newStrings[packageIndex] = newPackageName
            if (appNameIndex >= 0) newStrings[appNameIndex] = newAppName

            // 构建新的 string_data 和 string_offsets
            val stringDataList = mutableListOf<ByteArray>()
            val newStringOffsets = IntArray(stringCount)
            var currentOffset = 0

            for (i in 0 until stringCount) {
                newStringOffsets[i] = currentOffset
                val encoded = encodeString(newStrings[i], isUtf8)
                stringDataList.add(encoded)
                currentOffset += encoded.size
            }

            // 计算新的 strings_start（相对于 stringPoolStart）
            // header_size + string_offsets + style_offsets
            val newStringsStart = spHeaderSize + stringCount * 4 + styleCount * 4
            // 对齐到 4 字节边界
            val stringsStartAligned = (newStringsStart + 3) and 3.inv()
            val paddingBeforeStrings = stringsStartAligned - newStringsStart

            // 新的 string_data 总大小
            val stringDataSize = currentOffset
            // 对齐到 4 字节
            val stringDataAligned = (stringDataSize + 3) and 3.inv()

            // 新的 styles_start（如果有 style）
            val newStylesStart = if (styleCount > 0) stringsStartAligned + stringDataAligned else 0
            // style data 大小（从原数据复制）
            val styleDataSize = if (styleCount > 0) oldSpChunkSize - stylesStart else 0

            // 新的 String Pool chunk 大小
            val newSpChunkSize = stringsStartAligned + stringDataAligned + styleDataSize
            // 对齐到 4 字节
            val newSpChunkSizeAligned = (newSpChunkSize + 3) and 3.inv()

            // 读取后续 chunk（String Pool 之后的所有数据）
            val afterStringPool = stringPoolStart + oldSpChunkSize
            val remainingData = originalData.copyOfRange(afterStringPool, originalData.size)

            // 构建新文件
            val newFileSize = stringPoolStart + newSpChunkSizeAligned + remainingData.size
            val newData = ByteArray(newFileSize)
            val newBuffer = ByteBuffer.wrap(newData).order(ByteOrder.LITTLE_ENDIAN)

            // 复制文件头
            newBuffer.put(originalData, 0, stringPoolStart)
            // 更新 file_size
            newBuffer.putInt(4, newFileSize)

            // 写入 String Pool header
            newBuffer.position(stringPoolStart)
            newBuffer.putShort(CHUNK_STRING_POOL.toShort())
            newBuffer.putShort(spHeaderSize.toShort())
            newBuffer.putInt(newSpChunkSizeAligned)
            newBuffer.putInt(stringCount)
            newBuffer.putInt(styleCount)
            newBuffer.putInt(flags)
            newBuffer.putInt(stringsStartAligned)
            newBuffer.putInt(newStylesStart)

            // 写入 string_offsets
            for (offset in newStringOffsets) {
                newBuffer.putInt(offset)
            }

            // 写入 style_offsets（从原数据复制）
            if (styleCount > 0) {
                originalBuffer.position(stringPoolStart + spHeaderSize + stringCount * 4)
                for (i in 0 until styleCount) {
                    newBuffer.putInt(originalBuffer.int)
                }
            }

            // 填充对齐字节
            for (i in 0 until paddingBeforeStrings) {
                newBuffer.put(0)
            }

            // 写入 string_data
            for (encoded in stringDataList) {
                newBuffer.put(encoded)
            }
            // 填充对齐
            for (i in stringDataSize until stringDataAligned) {
                newBuffer.put(0)
            }

            // 写入 style_data（从原数据复制）
            if (styleCount > 0 && styleDataSize > 0) {
                val oldStyleDataStart = stringPoolStart + stylesStart
                newBuffer.put(originalData, oldStyleDataStart, styleDataSize)
            }

            // 写入剩余数据
            newBuffer.put(remainingData)

            return newData
        }

        /**
         * 编码字符串为 AXML 格式
         */
        private fun encodeString(str: String, isUtf8: Boolean): ByteArray {
            return if (isUtf8) {
                val bytes = str.toByteArray(Charsets.UTF_8)
                val charLenBytes = encodeLength(str.length)
                val byteLenBytes = encodeLength(bytes.size)
                charLenBytes + byteLenBytes + bytes + byteArrayOf(0)
            } else {
                val chars = str.toCharArray()
                val lenBytes = encodeLengthUtf16(chars.size)
                val charBytes = ByteArray(chars.size * 2)
                val cb = ByteBuffer.wrap(charBytes).order(ByteOrder.LITTLE_ENDIAN)
                for (c in chars) cb.putShort(c.code.toShort())
                lenBytes + charBytes + byteArrayOf(0, 0)
            }
        }

        /**
         * 编码长度为 UTF-8 AXML 格式（1或2字节）
         */
        private fun encodeLength(length: Int): ByteArray {
            return if (length > 0x7F) {
                byteArrayOf(
                    ((length shr 8) or 0x80).toByte(),
                    (length and 0xFF).toByte()
                )
            } else {
                byteArrayOf(length.toByte())
            }
        }

        /**
         * 编码长度为 UTF-16 AXML 格式（2或4字节）
         */
        private fun encodeLengthUtf16(length: Int): ByteArray {
            return if (length > 0x7FFF) {
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(((length shr 16) or 0x8000).toShort())
                    .putShort((length and 0xFFFF).toShort())
                    .array()
            } else {
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(length.toShort())
                    .array()
            }
        }
    }
}
