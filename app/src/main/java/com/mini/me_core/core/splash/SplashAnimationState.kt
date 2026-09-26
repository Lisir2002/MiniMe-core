package com.mini.me_core.core.splash

/**
 * Splash animation stage timeline.
 *
 * NEW rhythm: aggregate 2.5s → hold/flash 0.5s → shatter 3s = 6.0s total.
 *
 * Timing scales by quality level:
 * HIGH:   6.0s total (0.7 explode / 1.8 converge / 0.5 hold / 3.0 shatter)
 * MEDIUM: 5.0s total (0.6 / 1.5 / 0.4 / 2.5)
 * LOW:    4.0s total (0.5 / 1.2 / 0.3 / 2.0)
 *
 * The Compose cross-fade (overlayAlpha → 0) handles the final dismissal,
 * so no separate FADE_OUT window is needed.
 */
enum class SplashStage {
    EXPLODE,
    CONVERGE,
    HOLD,
    SHATTER,
    FADE_OUT,
    COMPLETE;

    companion object {
        fun totalDurationMs(quality: SplashQualityLevel): Long = quality.durationMs

        fun explodeEndMs(quality: SplashQualityLevel): Long = when (quality) {
            SplashQualityLevel.HIGH -> 700L
            SplashQualityLevel.MEDIUM -> 600L
            SplashQualityLevel.LOW -> 500L
        }

        fun convergeEndMs(quality: SplashQualityLevel): Long = when (quality) {
            // Text fully formed around 2.3-2.5s → aggregation flash triggers here
            SplashQualityLevel.HIGH -> 2_500L
            SplashQualityLevel.MEDIUM -> 2_100L
            SplashQualityLevel.LOW -> 1_700L
        }

        fun holdEndMs(quality: SplashQualityLevel): Long = when (quality) {
            // 0.5s hold: aggregation pulse spreads 300ms then settles
            SplashQualityLevel.HIGH -> 3_000L
            SplashQualityLevel.MEDIUM -> 2_500L
            SplashQualityLevel.LOW -> 2_000L
        }

        fun shatterEndMs(quality: SplashQualityLevel): Long = when (quality) {
            // 3s shatter flight
            SplashQualityLevel.HIGH -> 6_000L
            SplashQualityLevel.MEDIUM -> 5_000L
            SplashQualityLevel.LOW -> 4_000L
        }

        fun fromElapsed(elapsedMs: Long, quality: SplashQualityLevel): SplashStage = when {
            elapsedMs < explodeEndMs(quality) -> EXPLODE
            elapsedMs < convergeEndMs(quality) -> CONVERGE
            elapsedMs < holdEndMs(quality) -> HOLD
            elapsedMs < shatterEndMs(quality) -> SHATTER
            elapsedMs < totalDurationMs(quality) -> FADE_OUT
            else -> COMPLETE
        }

        fun stageProgress(elapsedMs: Long, quality: SplashQualityLevel): Float {
            val eEnd = explodeEndMs(quality)
            val cEnd = convergeEndMs(quality)
            val hEnd = holdEndMs(quality)
            val sEnd = shatterEndMs(quality)
            val total = totalDurationMs(quality)
            return when (fromElapsed(elapsedMs, quality)) {
                EXPLODE -> elapsedMs.toFloat() / eEnd
                CONVERGE -> (elapsedMs - eEnd).toFloat() / (cEnd - eEnd)
                HOLD -> (elapsedMs - cEnd).toFloat() / (hEnd - cEnd)
                SHATTER -> (elapsedMs - hEnd).toFloat() / (sEnd - hEnd)
                FADE_OUT -> (elapsedMs - sEnd).toFloat() / (total - sEnd)
                COMPLETE -> 1f
            }
        }

        fun remainingSeconds(elapsedMs: Long, quality: SplashQualityLevel): Int {
            val secs = elapsedMs / 1000L
            return (totalDurationMs(quality) / 1000L - secs).toInt().coerceAtLeast(0)
        }
    }
}
