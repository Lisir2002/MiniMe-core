package com.mini.me_core.feature.packager.domain.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * ZIP 对齐器（zipalign）
 *
 * 确保 APK 中所有未压缩（STORED）的文件数据相对于文件起始位置的偏移量是 4 的倍数。
 * 通过在 Local File Header 的 extra field 中插入填充字节实现对齐。
 */
class ZipAligner {

    companion object {
        private const val ALIGNMENT = 4
        private const val LFH_MAGIC = 0x04034b50
        private const val CD_MAGIC = 0x02014b50
        private const val EOCD_MAGIC = 0x06054b50

        /**
         * 对 APK 进行 4 字节对齐
         */
        fun align(inputApk: File, outputApk: File) {
            outputApk.parentFile?.mkdirs()
            if (outputApk.exists()) outputApk.delete()

            // 读取所有条目
            val entries = mutableListOf<EntryData>()
            ZipInputStream(FileInputStream(inputApk)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val data = zis.readBytes()
                    entries.add(EntryData(entry!!.name, entry!!.method, data))
                    zis.closeEntry()
                }
            }

            FileOutputStream(outputApk).use { fos ->
                val lfhOffsets = mutableListOf<Int>()
                var currentOffset = 0

                // 写入 Local File Headers + 数据
                for (entryData in entries) {
                    val nameBytes = entryData.name.toByteArray(Charsets.UTF_8)
                    val crc = CRC32().apply { update(entryData.data) }.value
                    val size = entryData.data.size

                    // 计算基础 header 大小
                    val baseHeaderSize = 30 + nameBytes.size

                    // 计算需要的填充量（仅 STORED 条目需要对齐）
                    var padding = 0
                    if (entryData.method == ZipEntry.STORED) {
                        val dataStart = currentOffset + baseHeaderSize
                        if (dataStart % ALIGNMENT != 0) {
                            padding = ALIGNMENT - (dataStart % ALIGNMENT)
                        }
                    }

                    // 记录 LFH 偏移量
                    lfhOffsets.add(currentOffset)

                    // 写入 Local File Header
                    val header = ByteBuffer.allocate(baseHeaderSize + padding).order(ByteOrder.LITTLE_ENDIAN)
                    header.putInt(LFH_MAGIC)
                    header.putShort(20)                       // version needed
                    header.putShort(0)                        // flags
                    header.putShort(entryData.method.toShort())
                    header.putShort(0)                        // mod time (简化)
                    header.putShort(0)                        // mod date (简化)
                    header.putInt(crc.toInt())
                    header.putInt(size)                       // compressed size
                    header.putInt(size)                       // uncompressed size
                    header.putShort(nameBytes.size.toShort())
                    header.putShort(padding.toShort())        // extra field length
                    header.put(nameBytes)
                    if (padding > 0) header.put(ByteArray(padding))
                    fos.write(header.array())
                    currentOffset += header.array().size

                    // 写入数据
                    fos.write(entryData.data)
                    currentOffset += size
                }

                // 写入 Central Directory
                val cdStart = currentOffset
                for ((index, entryData) in entries.withIndex()) {
                    val nameBytes = entryData.name.toByteArray(Charsets.UTF_8)
                    val crc = CRC32().apply { update(entryData.data) }.value
                    val size = entryData.data.size

                    val cd = ByteBuffer.allocate(46 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN)
                    cd.putInt(CD_MAGIC)
                    cd.putShort(20)                        // version made by
                    cd.putShort(20)                        // version needed
                    cd.putShort(0)                         // flags
                    cd.putShort(entryData.method.toShort())
                    cd.putShort(0)                         // mod time
                    cd.putShort(0)                         // mod date
                    cd.putInt(crc.toInt())
                    cd.putInt(size)                        // compressed size
                    cd.putInt(size)                        // uncompressed size
                    cd.putShort(nameBytes.size.toShort())
                    cd.putShort(0)                         // extra length
                    cd.putShort(0)                         // comment length
                    cd.putShort(0)                         // disk start
                    cd.putShort(0)                         // internal attrs
                    cd.putInt(0)                           // external attrs
                    cd.putInt(lfhOffsets[index])           // relative offset
                    cd.put(nameBytes)
                    fos.write(cd.array())
                    currentOffset += cd.array().size
                }

                // 写入 End of Central Directory
                val cdSize = currentOffset - cdStart
                val eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
                eocd.putInt(EOCD_MAGIC)
                eocd.putShort(0)                         // disk number
                eocd.putShort(0)                         // cd disk
                eocd.putShort(entries.size.toShort())    // entries on disk
                eocd.putShort(entries.size.toShort())    // total entries
                eocd.putInt(cdSize)
                eocd.putInt(cdStart)
                eocd.putShort(0)                         // comment length
                fos.write(eocd.array())
            }
        }

        private data class EntryData(
            val name: String,
            val method: Int,
            val data: ByteArray
        )
    }
}
