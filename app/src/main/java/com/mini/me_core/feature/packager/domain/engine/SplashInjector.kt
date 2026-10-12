package com.mini.me_core.feature.packager.domain.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.packager.domain.model.SplashMode
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
@Suppress("UtilityClassWithPublicConstructor", "TooGenericExceptionCaught", "TooManyFunctions")
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
        private const val RES_TABLE_PACKAGE_TYPE = 0x0200
        private const val RES_TABLE_TYPE_TYPE = 0x0201

        // === 字符串池 flag ===
        private const val UTF8_FLAG = 0x100

        /** android:windowBackground 属性资源 ID（Res_value.data 指向该 drawable） */
        private const val ANDROID_WINDOW_BACKGROUND = 0x01010054

        /** Res_value 数据类型：资源引用 */
        private const val TYPE_REFERENCE = 0x01

        /** Res_value 数据类型：ARGB8 颜色 */
        private const val TYPE_INT_COLOR_ARGB8 = 0x1c

        /** style / drawable / color 类型名与目标主题名、颜色资源名 */
        private const val STYLE_TYPE_NAME = "style"
        private const val DRAWABLE_TYPE_NAME = "drawable"
        private const val COLOR_TYPE_NAME = "color"
        private const val THEME_APP_TEMPLATE = "Theme.AppTemplate"
        private const val SPLASH_BG_COLOR_NAME = "splash_background"

        /** ResTable_entry 的 FLAG_COMPLEX：其后为 bag（style）而非单值 */
        private const val ENTRY_FLAG_COMPLEX = 0x0001

        /** offset 数组中表示"该配置下无此 entry"的标记（0xffffffff 的有符号值） */
        private const val RES_NO_ENTRY_OFFSET = -1

        /**
         * 注入结果。各标志独立成立，任一为 false 都不代表整体失败。
         *
         * @param windowImageOverwritten 是否已覆盖冷启动窗地位图（模板不含 splash_img 时为 false）
         * @param wwwSplashPngWritten 是否向 assets/www 写入了合成 splash.png
         * @param splashHtmlWritten 是否向 assets/www 写入了 splash.html
         */
        data class InjectResult(
            val windowImageOverwritten: Boolean,
            val windowBackgroundPatched: Boolean,
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
            splashHtmlSource: File?,
            userSplashHtmlFile: File? = null
        ): InjectResult {
            if (!extractedDir.isDirectory) {
                FileLogger.e(TAG, "参数非法：解压目录不存在 ${extractedDir.absolutePath}")
                return InjectResult(false, false, false, false, "解压目录不存在")
            }

            val hasCustomImage = customImageFile != null && customImageFile.isFile
            val bgColor = parseColor(settings.backgroundColor)
            val mode = settings.mode

            // 1. 合成 1080×1920 全幅位图（仅 IMAGE 模式需要；全程 try，任何失败都不阻断构建）
            var composed: Bitmap? = null
            if (mode == SplashMode.IMAGE) {
                try {
                    composed = composeSplashBitmap(bgColor, hasCustomImage, customImageFile)
                } catch (e: Exception) {
                    FileLogger.e(TAG, "合成启动图失败，跳过位图注入: ${e.message}", e)
                }
            }

            // 2. 覆盖冷启动窗地位图 splash_img（资源缺失则告警跳过；仅 IMAGE 模式）
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

            // 2.5 把原生主题 windowBackground 指向目标资源（原地修改 arsc，不改文件大小）。
            // IMAGE：指向 splash_img 位图（需位图已覆盖）；COLOR/HTML：指向 splash_background 颜色资源并改写为用户色。
            // 任何异常都不阻断构建。
            var bgPatched = false
            val canPatch = mode != SplashMode.IMAGE || windowOverwritten
            if (canPatch) {
                try {
                    bgPatched = applyNativeWindowBackground(extractedDir, mode, bgColor)
                } catch (e: Exception) {
                    FileLogger.e(TAG, "windowBackground 主题注入失败（不阻断）: ${e.message}", e)
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
            val wwwDir = File(extractedDir, "assets/www").apply { mkdirs() }
            val htmlTarget = File(wwwDir, "splash.html")
            try {
                if (mode == SplashMode.HTML && userSplashHtmlFile != null && userSplashHtmlFile.isFile) {
                    // HTML 模式：用用户自定义 splash.html，并在 <head> 注入 minimeSplash bridge
                    val injected = injectSplashBridge(
                        userSplashHtmlFile.readText(Charsets.UTF_8),
                        settings.htmlTimeoutMs,
                        settings.backgroundColor
                    )
                    htmlTarget.writeText(injected, Charsets.UTF_8)
                    htmlWritten = true
                    FileLogger.i(TAG, "HTML 模式启动页已注入: ${userSplashHtmlFile.path}")
                } else if (splashHtmlSource != null && splashHtmlSource.isFile) {
                    splashHtmlSource.copyTo(htmlTarget, overwrite = true)
                    htmlWritten = true
                } else {
                    FileLogger.w(TAG, "splash.html 来源缺失，跳过写入")
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "写入 www/splash.html 失败: ${e.message}", e)
            }

            val note = "windowImage=$windowOverwritten, bgPatched=$bgPatched, wwwPng=$wwwPngWritten, html=$htmlWritten"
            FileLogger.i(TAG, "启动动画注入完成: $note")
            return InjectResult(windowOverwritten, bgPatched, wwwPngWritten, htmlWritten, note)
        }

        /** 位图是否可用于合成（非空、未回收、尺寸为正）。 */
        private fun isValidBitmap(bmp: Bitmap?): Boolean {
            return bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0
        }

        /**
         * HTML 模式：在用户 splash.html 的 `<head>` 内注入 minimeSplash bridge 脚本。
         *
         * 提供 window.minimeSplash.finish()（跳主页）与 config（背景色/超时）；并内置超时兜底：
         * 用户页在 timeoutMs 内未调用 finish 时自动跳转。脚本幂等，重复注入安全。
         */
        private fun injectSplashBridge(html: String, timeoutMs: Int, bgColor: String): String {
            val script = """
<script>
(function(){
  var cfg = { backgroundColor: ${jsString(bgColor)}, timeoutMs: $timeoutMs };
  var done = false;
  function finish(){ if(done) return; done = true;
    try { window.minimeBridge && window.minimeBridge.closeSplash && window.minimeBridge.closeSplash(); } catch(e){}
    try { if(window.NativeBridge) window.NativeBridge.postMessage(JSON.stringify({type:'splashFinish'})); } catch(e){}
  }
  window.minimeSplash = { finish: finish, config: cfg };
  setTimeout(finish, $timeoutMs);
})();
</script>
""".trimIndent()
            val headIdx = html.indexOf("<head>", ignoreCase = true)
            if (headIdx >= 0) {
                val insertAt = headIdx + "<head>".length
                return html.substring(0, insertAt) + "\n" + script + html.substring(insertAt)
            }
            return script + "\n" + html
        }

        private fun jsString(s: String): String =
            "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

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

        /**
         * 把 Theme.AppTemplate 的 android:windowBackground 指向 splash_img 位图资源。
         *
         * 原地修改 resources.arsc 中 style bag 内 windowBackground 映射的 Res_value.data
         * （4 字节），不改变 arsc 文件大小。解析失败或目标不存在时返回 false，不抛异常。
         */
        /**
         * 按启动模式把原生 windowBackground 指向正确资源，并按需要改写颜色值。
         *
         * - IMAGE：windowBackground → splash_img（位图），由调用方先写入 splash_img.png
         * - COLOR / HTML：windowBackground → splash_background（颜色资源），并把该颜色值
         *   改写为用户背景色；HTML 模式下该颜色同时作为原生兜底底色
         */
        private fun applyNativeWindowBackground(
            extractedDir: File,
            mode: SplashMode,
            bgColorArgb: Int
        ): Boolean {
            val arsc = File(extractedDir, "resources.arsc")
            if (!arsc.isFile) {
                FileLogger.w(TAG, "resources.arsc 不存在，跳过 windowBackground 注入")
                return false
            }
            val data = arsc.readBytes()
            if (data.size < 12 || getShortLE(data, 0) != RES_TABLE_TYPE) {
                FileLogger.w(TAG, "resources.arsc table header 非法，跳过 windowBackground 注入")
                return false
            }
            var targetResId: Int? = null
            when (mode) {
                SplashMode.IMAGE -> targetResId = findResIdByKey(data, DRAWABLE_TYPE_NAME, SPLASH_IMG_RES_NAME)
                SplashMode.COLOR, SplashMode.HTML -> {
                    // 先把 splash_background 颜色值改写为用户背景色，再让 windowBackground 指向它
                    patchColorValue(data, SPLASH_BG_COLOR_NAME, bgColorArgb)
                    targetResId = findResIdByKey(data, COLOR_TYPE_NAME, SPLASH_BG_COLOR_NAME)
                }
            }
            if (targetResId == null) {
                FileLogger.w(TAG, "arsc 中未解析到目标资源 ID(${mode.name})，跳过 windowBackground 注入")
                return false
            }
            val patched = patchWindowBackground(data, targetResId)
            if (patched) {
                arsc.writeBytes(data)
                FileLogger.i(
                    TAG,
                    "windowBackground 已指向 0x${Integer.toHexString(targetResId)} (mode=${mode.name})"
                )
            } else {
                FileLogger.w(TAG, "未在 Theme.AppTemplate 找到 windowBackground 映射，跳过")
            }
            return patched
        }

        /**
         * 通用：在 arsc 中按 type 名 + entry key 名解析资源 ID（0x7fTTEEEE）。纯函数，便于单测。
         */
        @JvmStatic
        fun findResIdByKey(data: ByteArray, typeName: String, keyName: String): Int? {
            val ctx = ArscContext.parse(data) ?: return null
            val typeIdx = ctx.indexOfType(typeName) ?: return null
            ctx.forEachTypeEntry(typeIdx) { entryIdx, keyIdx, isComplex ->
                if (!isComplex && ctx.keyStrings.getOrNull(keyIdx) == keyName) {
                    return (ctx.packageId shl 24) or (typeIdx shl 16) or entryIdx
                }
            }
            return null
        }

        /**
         * 原地改写 color 类型下指定 key 的颜色值（Res_value.data），并把 dataType 置为
         * TYPE_INT_COLOR_ARGB8(0x1c)。纯函数（直接修改入参 ByteArray）。返回是否命中改写。
         */
        @JvmStatic
        fun patchColorValue(data: ByteArray, keyName: String, newArgb: Int): Boolean {
            val ctx = ArscContext.parse(data) ?: return false
            val colorTypeIdx = ctx.indexOfType(COLOR_TYPE_NAME) ?: return false
            var found = false
            ctx.forEachTypeEntry(colorTypeIdx) { _, keyIdx, isComplex ->
                if (found || isComplex) return@forEachTypeEntry
                if (ctx.keyStrings.getOrNull(keyIdx) != keyName) return@forEachTypeEntry
                // Res_value 位于 entry header(8) 之后：size(2)@+0, res0(1)@+2, dataType(1)@+3, data(4)@+4
                val ep = ctx.currentEntryOffset
                data[ep + 11] = TYPE_INT_COLOR_ARGB8.toByte()
                putIntLE(data, ep + 12, newArgb)
                found = true
            }
            return found
        }

        /**
         * 在 arsc 中解析 splash_img 位图资源 ID（0x7fTTEEEE）。
         *
         * 定位 type=drawable 的 type chunk，遍历其非空 entry，找到 key 字符串为
         * splash_img 的条目，返回 (pkgId<<24)|(typeIdx<<16)|entryIdx。纯函数，便于单测。
         */
        @JvmStatic
        fun findSplashImgResId(data: ByteArray): Int? =
            findResIdByKey(data, DRAWABLE_TYPE_NAME, SPLASH_IMG_RES_NAME)

        /**
         * 原地把 Theme.AppTemplate 的 windowBackground 映射值改为 splashImgResId。
         *
         * 找到 type=style、key=Theme.AppTemplate 的 bag entry，遍历其 ResTable_map，
         * 命中 key=0x01010054 时把 Res_value 的 dataType 置为引用、data 置为 splashImgResId。
         * 纯函数（直接修改入参 ByteArray），便于单测。返回是否命中并改写。
         */
        @JvmStatic
        fun patchWindowBackground(data: ByteArray, splashImgResId: Int): Boolean {
            val ctx = ArscContext.parse(data) ?: return false
            val styleTypeIdx = ctx.indexOfType(STYLE_TYPE_NAME) ?: return false
            var found = false
            ctx.forEachTypeEntry(styleTypeIdx) { entryIdx, keyIdx, isComplex ->
                if (found || !isComplex) return@forEachTypeEntry
                if (ctx.keyStrings.getOrNull(keyIdx) != THEME_APP_TEMPLATE) return@forEachTypeEntry
                // bag: entry header(8) + parentRef(4) + count(4)，map 数组紧随其后
                val bagCount = getIntLE(data, ctx.currentEntryOffset + 12)
                var mapPos = ctx.currentEntryOffset + 16
                repeat(bagCount) {
                    val mapKey = getIntLE(data, mapPos)
                    if (mapKey == ANDROID_WINDOW_BACKGROUND) {
                        // Res_value: size(2)@+4, res0(1)@+6, dataType(1)@+7, data(4)@+8
                        data[mapPos + 7] = TYPE_REFERENCE.toByte()
                        putIntLE(data, mapPos + 8, splashImgResId)
                        found = true
                    }
                    mapPos += 12
                }
            }
            return found
        }

        /**
         * arsc 解析上下文：复用 table header 之后的全局字符串池与 package chunk，
         * 提供 type/key 字符串索引与 type chunk entry 遍历。纯解析、不持有 Android 依赖。
         */
        private class ArscContext private constructor(
            val packageId: Int,
            val typeStrings: List<String>,
            val keyStrings: List<String>,
            private val data: ByteArray,
            private val chunks: List<ChunkRef>
        ) {
            /** 当前 forEachTypeEntry 命中的 entry 在 data 中的绝对偏移。 */
            var currentEntryOffset: Int = 0
                private set

            private class ChunkRef(val offset: Int, val typeIdx: Int, val entryCount: Int, val entriesStart: Int)

            fun indexOfType(name: String): Int? = typeStrings.indexOf(name).takeIf { it >= 0 }?.plus(1)

            /** 遍历指定 typeIdx 的所有非空 entry，回调 (entryIdx, keyStringIndex, isComplex)。 */
            @Suppress("LoopWithTooManyJumpStatements")
            inline fun forEachTypeEntry(typeIdx: Int, cb: (entryIdx: Int, keyIdx: Int, isComplex: Boolean) -> Unit) {
                for (c in chunks) {
                    if (c.typeIdx != typeIdx) continue
                    val headerSize = getShortLE(data, c.offset + 2)
                    val offsetArrayBase = c.offset + headerSize
                    for (i in 0 until c.entryCount) {
                        val entryOffsetRel = getIntLE(data, offsetArrayBase + i * 4)
                        if (entryOffsetRel == 0 || entryOffsetRel == RES_NO_ENTRY_OFFSET) continue
                        val ep = c.offset + c.entriesStart + entryOffsetRel
                        if (ep + 8 > data.size) continue
                        val flags = getShortLE(data, ep + 2)
                        val keyIdx = getIntLE(data, ep + 4)
                        currentEntryOffset = ep
                        cb(i, keyIdx, (flags and ENTRY_FLAG_COMPLEX) != 0)
                    }
                }
            }

            companion object {
                fun parse(data: ByteArray): ArscContext? {
                    if (data.size < 12 || getShortLE(data, 0) != RES_TABLE_TYPE) return null
                    val globalPoolSize = getIntLE(data, 12 + 4)
                    val pkgOff = 12 + globalPoolSize
                    if (pkgOff + 288 > data.size || getShortLE(data, pkgOff) != RES_TABLE_PACKAGE_TYPE) return null
                    val packageId = getIntLE(data, pkgOff + 8) and 0xFF
                    val typeStringsOff = getIntLE(data, pkgOff + 12 + 256)
                    val keyStringsOff = getIntLE(data, pkgOff + 12 + 256 + 8)
                    val typeStrings = ArrayList<String>()
                    parseStringPool(data, pkgOff + typeStringsOff, typeStrings)
                    val keyStrings = ArrayList<String>()
                    parseStringPool(data, pkgOff + keyStringsOff, keyStrings)

                    val pkgHeaderSize = getShortLE(data, pkgOff + 2)
                    val pkgSize = getIntLE(data, pkgOff + 4)
                    var cur = pkgOff + pkgHeaderSize
                    val end = pkgOff + pkgSize
                    val chunks = ArrayList<ChunkRef>()
                    while (cur + 8 <= end) {
                        val ct = getShortLE(data, cur)
                        val csz = getIntLE(data, cur + 4)
                        if (csz < 8 || cur + csz > data.size) break
                        if (ct == RES_TABLE_TYPE_TYPE) {
                            val tid = data[cur + 8].toInt() and 0xFF
                            chunks.add(
                                ChunkRef(cur, tid, getIntLE(data, cur + 12), getIntLE(data, cur + 16))
                            )
                        }
                        cur += csz
                    }
                    return ArscContext(packageId, typeStrings, keyStrings, data, chunks)
                }
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

        private fun putIntLE(data: ByteArray, offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
            data[offset + 2] = ((value shr 16) and 0xFF).toByte()
            data[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
    }
}
