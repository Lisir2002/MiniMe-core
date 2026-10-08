package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.core.util.FileLogger
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * resources.arsc 编辑器
 *
 * 用于修改 APK 中 resources.arsc 全局字符串池里的应用名称。
 *
 * 背景：模版 APK 的应用名称"应用模版"存储在 resources.arsc 的全局字符串池中，
 * AndroidManifest.xml 通过 @string/app_name 引用，仅修改 AXML 不会改变应用名称。
 *
 * 实现策略：
 * 1. 解析全局字符串池（RES_STRING_POOL_TYPE，位于 arsc 开头 table header 之后）
 * 2. 定位目标字符串
 * 3. 若新名称 UTF-8 字节数不大于原字符串，原地替换并补零
 * 4. 若更长，重建整个全局字符串池并平移后续 chunk
 */
class ArscEditor {

    companion object {
        private const val TAG = "ArscEditor"

        // RES_TABLE_TYPE: 0x0002
        private const val RES_TABLE_TYPE = 0x0002

        // RES_STRING_POOL_TYPE: 0x0001
        private const val RES_STRING_POOL_TYPE = 0x0001

        // UTF-8 flag
        private const val UTF8_FLAG = 0x100

        // 全局字符串池在 arsc 中的偏移（table header 固定 12 字节）
        private const val GLOBAL_STRING_POOL_OFFSET = 12

        /**
         * 修改 resources.arsc 中的应用名称
         *
         * @param arscFile resources.arsc 文件
         * @param oldName 原应用名称（模版中为"应用模版"）
         * @param newName 新应用名称
         * @return true 表示修改成功
         */
        fun modifyAppName(arscFile: File, oldName: String, newName: String): Boolean {
            val data = arscFile.readBytes()
            FileLogger.d(TAG, "开始修改 resources.arsc 应用名称: '$oldName' -> '$newName'")

            // 验证全局字符串池类型
            val spType = getShortLE(data, GLOBAL_STRING_POOL_OFFSET)
            if (spType != RES_STRING_POOL_TYPE) {
                FileLogger.e(TAG, "全局字符串池类型错误: 0x${spType.toString(16)}")
                return false
            }

            // 解析字符串池头
            val spHeaderSize = getShortLE(data, GLOBAL_STRING_POOL_OFFSET + 2)
            val spSize = getIntLE(data, GLOBAL_STRING_POOL_OFFSET + 4)
            val stringCount = getIntLE(data, GLOBAL_STRING_POOL_OFFSET + 8)
            val styleCount = getIntLE(data, GLOBAL_STRING_POOL_OFFSET + 12)
            val flags = getIntLE(data, GLOBAL_STRING_POOL_OFFSET + 16)
            val stringsStart = getIntLE(data, GLOBAL_STRING_POOL_OFFSET + 20)
            val isUtf8 = (flags and UTF8_FLAG) != 0

            FileLogger.d(
                TAG,
                "字符串池: count=$stringCount, styles=$styleCount, utf8=$isUtf8, size=$spSize"
            )

            if (!isUtf8) {
                FileLogger.e(TAG, "当前仅支持 UTF-8 字符串池")
                return false
            }

            // 读取所有字符串偏移量
            val offsets = IntArray(stringCount) { i ->
                getIntLE(data, GLOBAL_STRING_POOL_OFFSET + spHeaderSize + i * 4)
            }

            val stringsDataBase = GLOBAL_STRING_POOL_OFFSET + stringsStart

            // 解析所有字符串，找到目标字符串
            var targetIndex = -1
            val parsedStrings = ArrayList<String>(stringCount)
            for (i in 0 until stringCount) {
                var pos = stringsDataBase + offsets[i]
                // UTF-8: 跳过字符长度（1或2字节）
                var charLen = data[pos].toInt() and 0xFF; pos++
                if (charLen and 0x80 != 0) {
                    charLen = ((charLen and 0x7F) shl 8) or (data[pos].toInt() and 0xFF); pos++
                }
                // 字节长度（1或2字节）
                var byteLen = data[pos].toInt() and 0xFF; pos++
                if (byteLen and 0x80 != 0) {
                    byteLen = ((byteLen and 0x7F) shl 8) or (data[pos].toInt() and 0xFF); pos++
                }
                val s = String(data, pos, byteLen, Charsets.UTF_8)
                parsedStrings.add(s)
                if (s == oldName && targetIndex == -1) {
                    targetIndex = i
                }
            }

            if (targetIndex == -1) {
                FileLogger.e(TAG, "未在全局字符串池中找到应用名称: '$oldName'")
                return false
            }

            FileLogger.d(TAG, "找到目标字符串索引: $targetIndex")

            val newNameBytes = newName.toByteArray(Charsets.UTF_8)
            val oldNameBytes = oldName.toByteArray(Charsets.UTF_8)

            // === 策略1：原地替换（新名称不超过原字节数）===
            if (newNameBytes.size <= oldNameBytes.size) {
                FileLogger.d(TAG, "使用原地替换策略: ${newNameBytes.size} <= ${oldNameBytes.size}")
                val result = data.copyOf()
                var pos = stringsDataBase + offsets[targetIndex]
                // 字符长度字节（新名称字符数，简单情况 < 128，单字节）
                val newCharLen = newName.length
                // 原字符长度位置
                var charLenPos = pos
                val oldCharLenFirst = result[charLenPos].toInt() and 0xFF
                // 判断原长度是否为双字节
                val oldCharLenTwoByte = oldCharLenFirst and 0x80 != 0
                // 字节长度位置
                var byteLenPos = charLenPos + if (oldCharLenTwoByte) 2 else 1
                val oldByteLenFirst = result[byteLenPos].toInt() and 0xFF
                val oldByteLenTwoByte = oldByteLenFirst and 0x80 != 0
                val stringStart = byteLenPos + if (oldByteLenTwoByte) 2 else 1

                // 由于新名称更短，长度字段保持单字节即可
                // 写入字符长度
                result[charLenPos] = (newCharLen and 0x7F).toByte()
                // 写入字节长度
                result[byteLenPos] = (newNameBytes.size and 0x7F).toByte()
                // 写入新字符串
                System.arraycopy(newNameBytes, 0, result, stringStart, newNameBytes.size)
                // 剩余空间补零（覆盖旧字符串剩余字节 + 结尾0）
                val oldTotalStringBytes = if (oldByteLenTwoByte) {
                    ((oldByteLenFirst and 0x7F) shl 8) or (result[byteLenPos + 1].toInt() and 0xFF)
                } else {
                    oldByteLenFirst
                }
                for (k in newNameBytes.size until oldTotalStringBytes + 1) {
                    result[stringStart + k] = 0
                }

                arscFile.writeBytes(result)
                FileLogger.d(TAG, "原地替换完成")
                return true
            }

            // === 策略2：重建全局字符串池（新名称更长）===
            FileLogger.d(TAG, "使用字符串池重建策略: ${newNameBytes.size} > ${oldNameBytes.size}")
            parsedStrings[targetIndex] = newName

            // 重新编码所有字符串（UTF-8，带长度前缀）
            val encodedStrings = ByteArray(0)
            val newStringData = java.io.ByteArrayOutputStream()
            val newOffsets = IntArray(stringCount)
            for (i in 0 until stringCount) {
                newOffsets[i] = newStringData.size()
                val sBytes = parsedStrings[i].toByteArray(Charsets.UTF_8)
                val charLen = parsedStrings[i].length
                // 字符长度（单字节，假设 < 128）
                newStringData.write(charLen and 0x7F)
                // 字节长度（单字节，假设 < 128；超长名称极少见）
                if (sBytes.size >= 128) {
                    FileLogger.e(TAG, "字符串字节数超过127，暂不支持: ${sBytes.size}")
                    return false
                }
                newStringData.write(sBytes.size and 0x7F)
                newStringData.write(sBytes)
                newStringData.write(0) // 结尾 null
            }

            val newStringDataBytes = newStringData.toByteArray()

            // 计算新字符串池布局
            // header(28) + offsets(stringCount*4) + stringData
            val newStringsStart = spHeaderSize + stringCount * 4
            val newSpSize = newStringsStart + newStringDataBytes.size
            // 字符串池大小需要 4 字节对齐
            val padding = (4 - (newSpSize % 4)) % 4
            val alignedNewSpSize = newSpSize + padding

            val newStringPool = ByteArray(alignedNewSpSize)
            // 写入 header
            putShortLE(newStringPool, 0, RES_STRING_POOL_TYPE)
            putShortLE(newStringPool, 2, spHeaderSize)
            putIntLE(newStringPool, 4, alignedNewSpSize)
            putIntLE(newStringPool, 8, stringCount)
            putIntLE(newStringPool, 12, styleCount)
            putIntLE(newStringPool, 16, flags)
            putIntLE(newStringPool, 20, newStringsStart)
            putIntLE(newStringPool, 24, 0) // stylesStart = 0（无样式）

            // 写入偏移量数组
            for (i in 0 until stringCount) {
                putIntLE(newStringPool, spHeaderSize + i * 4, newOffsets[i])
            }

            // 写入字符串数据
            System.arraycopy(
                newStringDataBytes, 0, newStringPool,
                newStringsStart, newStringDataBytes.size
            )

            // 拼接：table header(12) + 新字符串池 + 原字符串池之后的所有数据
            val afterPool = data.copyOfRange(GLOBAL_STRING_POOL_OFFSET + spSize, data.size)
            val tableHeader = data.copyOfRange(0, GLOBAL_STRING_POOL_OFFSET)

            val output = java.io.ByteArrayOutputStream()
            output.write(tableHeader)
            output.write(newStringPool)
            output.write(afterPool)
            val finalData = output.toByteArray()

            // 更新 arsc 总大小（table header 偏移4处）
            putIntLE(finalData, 4, finalData.size)

            arscFile.writeBytes(finalData)
            FileLogger.d(
                TAG,
                "字符串池重建完成: 旧池=$spSize, 新池=$alignedNewSpSize, 总大小=${finalData.size}"
            )
            return true
        }

        // === 小端序读写辅助 ===

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

        private fun putShortLE(data: ByteArray, offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        private fun putIntLE(data: ByteArray, offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
            data[offset + 2] = ((value shr 16) and 0xFF).toByte()
            data[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
    }
}
