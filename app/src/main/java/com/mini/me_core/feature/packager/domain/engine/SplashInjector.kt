package com.mini.me_core.feature.packager.domain.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.packager.domain.model.SplashSettings
import com.mini.me_core.feature.packager.domain.model.normalizedColorOrNull
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 冷启动动画资源注入器。
 *
 * 目标：让打包出的 APK 冷启动第一帧即呈现用户自定义启动图与背景色，避免白屏，
 * 并与设备端 H5 启动页（assets/www/splash.html / splash.png）首帧像素级一致。
 *
 * 模板侧已固定 windowBackground 为 layer-list（兜底色 + 位图 @drawable/splash_img，
 * gravity=fill），因此打包器不改动二进制主题 / resources.arsc 结构，只做两件事：
 *  1. 按 1080×1920 全幅合成一张位图：先以 backgroundColor 铺满，再把用户启动图按
 *     centerCrop 居中绘制；无自定义图时为纯底色。
 *  2. 用这张合成位图**按同扩展名覆盖**编译 APK 里 splash_img 对应的位图文件
 *     （PNG→PNG；Android 按扩展名解析位图，写错扩展名会被当 XML 解析失败）。
 *
 * 资源定位：先解析 resources.arsc 全局字符串池，按资源名 splash_img 找到其
 * res/xxx.png 路径；找不到时兜底扫描 res/ 下文件名含 splash_img 的 PNG。
 *
 * 防御性：当前随仓库发布的 template-base.apk 是旧产物，可能尚不含 splash_img /
 * splash.html。此时定位失败仅 FileLogger 告警并优雅跳过（windowImageOverwritten=false），
 * 绝不抛出异常中断构建；assets/www 下的 splash.html / splash.png 照常写入，旧模板
 * 会忽略未知字段、不加载 splash.html，行为回退为现状（前向兼容）。
 */
@Suppress("UtilityClassWithPublicConstructor", "TooGenericExceptionCaught")
class SplashInjector {

    companion object {
        private const val TAG = "SplashInjector"

        /** 冷启动画布尺寸（全幅竖屏） */
        private const val CANVAS_W = 1080
        private const val CANVAS_H = 1920

        /** 模板侧冷启动窗地位图资源名 */
        private const val SPLASH_IMG_RES_NAME = "splash_img"

        // === arsc chunk 类型 ===
        private const val RES_TABLE_TYPE = 0x0002

        // === 字符串池 flag ===
        private const val UTF8_FLAG = 0x100

        /**
         * 注入结果。各标志独立成立，任一为 false 都不代表整体失败。
         *
         * @param windowImageOverwritten 是否已覆盖冷启动窗地位图（模板不含 splash_img 时为 false）
         * @param wwwSplashPngWritten 是否向 assets/www 写入了合成 splash.png
         * @param splashHtmlWritten 是否向 assets/www 写入了 splash.html
         */
        data class InjectResult(
            val windowImageOverwritten: Boolean,
            val wwwSplashPngWritten: Boolean,
            val splashHtmlWritten: Boolean,
            val note: String
        )

        /**
         * centerCrop 源裁剪矩形（纯 Kotlin 数据类，不依赖 android.graphics.Rect，
         * 便于在 JVM 单测中断言竖图/横图/方图/极端宽高比）。
         */
        data class CenterCrop(
            val srcLeft: Int,
            val srcTop: Int,
            val srcRight: Int,
            val srcBottom: Int
        ) {
            val width: Int get() = srcRight - srcLeft
            val height: Int get() = srcBottom - srcTop
        }

        /**
         * centerCrop 几何：给定源图与目标画布，返回源图上应被裁剪绘制的矩形。
         * 纯函数（不依赖 Bitmap / Rect）。
         *
         * 规则：以较大缩放比使源图“覆盖”目标画布（cover），多余部分居中裁掉。
         */
        @JvmStatic
        fun centerCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int): CenterCrop {
            val sw = srcW.coerceAtLeast(1)
            val sh = srcH.coerceAtLeast(1)
            val dw = dstW.coerceAtLeast(1)
            val dh = dstH.coerceAtLeast(1)
            val scale = maxOf(dw.toFloat() / sw, dh.toFloat() / sh)
            // 目标在源坐标下的可见尺寸
            val visibleW = (dw / scale).roundToInt().coerceAtMost(sw)
            val visibleH = (dh / scale).roundToInt().coerceAtMost(sh)
            val left = ((sw - visibleW) / 2f).roundToInt().coerceIn(0, sw - visibleW)
            val top = ((sh - visibleH) / 2f).roundToInt().coerceIn(0, sh - visibleH)
            return CenterCrop(left, top, left + visibleW, top + visibleH)
        }

        /**
         * 执行注入。
         *
         * @param extractedDir 模版 APK 解压根目录（含 res/、resources.arsc、assets/www/）
         * @param settings 启动页设置
         * @param customImageFile 用户自定义启动图文件（null 或不可解码则纯底色）
         * @param splashHtmlSource 主 App assets 中拷贝出的 splash.html 临时文件（null 则跳过写入）
         */
        @JvmStatic
        @Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod")
        fun inject(
            extractedDir: File,
            settings: SplashSettings,
            customImageFile: File?,
            splashHtmlSource: File?
        ): InjectResult {
            if (!extractedDir.isDirectory) {
                FileLogger.e(TAG, "参数非法：解压目录不存在 ${extractedDir.absolutePath}")
                return InjectResult(false, false, false, "解压目录不存在")
            }

            val hasCustomImage = customImageFile != null && customImageFile.isFile
            val bgColor = parseColor(settings.backgroundColor)

            // 1. 合成 1080×1920 全幅位图（全程 try，任何失败都不阻断构建）
            var composed: Bitmap? = null
            try {
                composed = composeSplashBitmap(bgColor, hasCustomImage, customImageFile)
            } catch (e: Exception) {
                FileLogger.e(TAG, "合成启动图失败，跳过位图注入: ${e.message}", e)
            }

            // 2. 覆盖冷启动窗地位图 splash_img（资源缺失则告警跳过）
            var windowOverwritten = false
            if (composed != null) {
                val target = locateSplashImageFile(extractedDir)
                if (target != null) {
                    try {
                        FileOutputStream(target).use { out ->
                            composed.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        windowOverwritten = true
                        FileLogger.i(TAG, "冷启动窗地位图已覆盖: ${target.path}")
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "写入 splash_img 失败: ${e.message}", e)
                    }
                } else {
                    FileLogger.w(TAG, "模版未包含 splash_img 位图，跳过冷启动窗地位图覆盖（旧模板前向兼容）")
                }
            }

            // 3. 向 assets/www 写入合成 splash.png（仅当有自定义图）
            var wwwPngWritten = false
            if (composed != null && hasCustomImage) {
                try {
                    val wwwDir = File(extractedDir, "assets/www")
                    wwwDir.mkdirs()
                    FileOutputStream(File(wwwDir, "splash.png")).use { out ->
                        composed.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    wwwPngWritten = true
                } catch (e: Exception) {
                    FileLogger.e(TAG, "写入 www/splash.png 失败: ${e.message}", e)
                }
            }

            composed?.recycle()

            // 4. 向 assets/www 写入 splash.html（旧模板也写，前向兼容）
            var htmlWritten = false
            if (splashHtmlSource != null && splashHtmlSource.isFile) {
                try {
                    val wwwDir = File(extractedDir, "assets/www")
                    wwwDir.mkdirs()
                    splashHtmlSource.copyTo(File(wwwDir, "splash.html"), overwrite = true)
                    htmlWritten = true
                } catch (e: Exception) {
                    FileLogger.e(TAG, "写入 www/splash.html 失败: ${e.message}", e)
                }
            } else {
                FileLogger.w(TAG, "splash.html 来源缺失，跳过写入")
            }

            val note = "windowImage=$windowOverwritten, wwwPng=$wwwPngWritten, html=$htmlWritten"
            FileLogger.i(TAG, "启动动画注入完成: $note")
            return InjectResult(windowOverwritten, wwwPngWritten, htmlWritten, note)
        }

        /** 位图是否可用于合成（非空、未回收、尺寸为正）。 */
        private fun isValidBitmap(bmp: Bitmap?): Boolean {
            return bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0
        }

        /** 合成全幅位图：bgColor 铺满 + 自定义图 centerCrop 居中。 */
        private fun composeSplashBitmap(bgColor: Int, hasImage: Boolean, srcFile: File?): Bitmap {
            val out = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(bgColor)

            if (hasImage && srcFile != null) {
                val src = BitmapFactory.decodeFile(srcFile.absolutePath)
                if (isValidBitmap(src)) {
                    val crop = centerCrop(src.width, src.height, CANVAS_W, CANVAS_H)
                    canvas.drawBitmap(
                        src,
                        Rect(crop.srcLeft, crop.srcTop, crop.srcRight, crop.srcBottom),
                        Rect(0, 0, CANVAS_W, CANVAS_H),
                        null
                    )
                    src.recycle()
                } else {
                    FileLogger.w(TAG, "自定义启动图解码失败，仅使用背景色: ${srcFile.absolutePath}")
                }
            }
            return out
        }

        /**
         * 定位编译 APK 内 splash_img 位图文件路径。
         * 先按资源名解析 resources.arsc 全局字符串池；找不到则扫描 res/ 兜底。
         */
        private fun locateSplashImageFile(extractedDir: File): File? {
            val arsc = File(extractedDir, "resources.arsc")
            if (arsc.isFile) {
                try {
                    val path = findSplashImagePathInArsc(arsc)
                    if (path != null) {
                        val candidate = File(extractedDir, path)
                        if (candidate.isFile && path.endsWith(".png", ignoreCase = true)) {
                            return candidate
                        }
                    }
                } catch (e: Exception) {
                    FileLogger.w(TAG, "解析 arsc 定位 splash_img 失败，转扫描兜底: ${e.message}")
                }
            }
            // 兜底扫描：res 下文件名含 splash_img 的 PNG
            val resDir = File(extractedDir, "res")
            return resDir.walkTopDown().firstOrNull { f -> isSplashImagePng(f) }
        }

        /** 文件名含 splash_img 且为 PNG 的位图候选。 */
        private fun isSplashImagePng(f: File): Boolean {
            if (!f.isFile) return false
            if (!f.name.contains(SPLASH_IMG_RES_NAME, ignoreCase = true)) return false
            return f.name.endsWith(".png", ignoreCase = true)
        }

        /** 在 resources.arsc 全局字符串池中查找形如 res/xxx.png 且含 splash_img 的路径。 */
        private fun findSplashImagePathInArsc(arsc: File): String? {
            val data = arsc.readBytes()
            if (data.size < 12 || getShortLE(data, 0) != RES_TABLE_TYPE) return null
            val globalStrings = ArrayList<String>()
            parseStringPool(data, 12, globalStrings)
            return globalStrings.firstOrNull { s ->
                s.startsWith("res/") &&
                    s.contains(SPLASH_IMG_RES_NAME, ignoreCase = true) &&
                    s.endsWith(".png", ignoreCase = true)
            }
        }

        /** 解析 "#RRGGBB" 为 Android 颜色 int；非法输入回退白色。 */
        private fun parseColor(raw: String): Int {
            val normalized = raw.normalizedColorOrNull() ?: "#FFFFFF"
            return try {
                Color.parseColor(normalized)
            } catch (e: IllegalArgumentException) {
                FileLogger.w(TAG, "背景色解析失败，回退白色: $raw", e)
                Color.WHITE
            }
        }

        /** 解析 RES_STRING_POOL chunk（兼容 UTF-8 / UTF-16），把字符串追加到 out。 */
        @Suppress("LoopWithTooManyJumpStatements")
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
                    var charLen = data[pos].toInt() and 0xFF; pos++
                    if (charLen and 0x80 != 0) {
                        charLen = ((charLen and 0x7F) shl 8) or (data[pos].toInt() and 0xFF); pos++
                    }
                    var byteLen = data[pos].toInt() and 0xFF; pos++
                    if (byteLen and 0x80 != 0) {
                        byteLen = ((byteLen and 0x7F) shl 8) or (data[pos].toInt() and 0xFF); pos++
                    }
                    s = String(data, pos, byteLen, Charsets.UTF_8)
                } else {
                    var len = getShortLE(data, pos); pos += 2
                    if (len and 0x8000 != 0) {
                        len = ((len and 0x7FFF) shl 16) or getShortLE(data, pos); pos += 2
                    }
                    s = String(data, pos, len * 2, Charsets.UTF_16LE)
                }
                out.add(s)
            }
        }

        private fun getShortLE(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun getIntLE(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
