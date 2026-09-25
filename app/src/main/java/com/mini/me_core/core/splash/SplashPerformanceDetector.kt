package com.mini.me_core.core.splash

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Device performance tier for adaptive splash animation quality.
 *
 * HIGH:  ≥8 cores, ≥6GB RAM — full effects, 1200 particles, 40 shards, 6s
 * MEDIUM: 4-7 cores, 4-6GB RAM — simplified, 800 particles, 25 shards, 5s
 * LOW:   <4 cores, <4GB RAM — minimal, 400 particles, 12 shards, no 3D, 4s
 */
enum class SplashQualityLevel(
    val particleCount: Int,
    val shardCount: Int,
    val durationMs: Long,
    val enable3D: Boolean,
    val enableCracks: Boolean,
) {
    HIGH(1200, 40, 6000L, true, true),
    MEDIUM(800, 25, 5000L, true, true),
    LOW(400, 12, 4000L, false, false);

    companion object {
        @Volatile
        private var cached: SplashQualityLevel? = null

        fun detect(context: Context): SplashQualityLevel {
            cached?.let { return it }
            val result = detectInternal(context)
            cached = result
            return result
        }

        private fun detectInternal(context: Context): SplashQualityLevel {
            val cores = Runtime.getRuntime().availableProcessors()

            // Total RAM in bytes
            val memoryInfo = ActivityManager.MemoryInfo()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getMemoryInfo(memoryInfo)
            val totalRamBytes = memoryInfo.totalMem
            val totalRamGb = totalRamBytes / (1024.0 * 1024.0 * 1024.0)

            // Screen resolution
            val metrics = context.resources.displayMetrics
            val densityDpi = metrics.densityDpi
            val pixels = metrics.widthPixels * metrics.heightPixels

            // Memory class
            val memoryClass = am.memoryClass

            return when {
                cores >= 8 && totalRamGb >= 6.0 -> HIGH
                cores < 4 || totalRamGb < 4.0 -> LOW
                else -> MEDIUM
            }
        }
    }
}
