package com.mini.me_core.core.splash

/**
 * Splash animation stage timeline.
 *
 * Timing scales by quality level:
 * HIGH:  6.0s total (1.5 explode / 2.0 converge / 1.0 hold / 1.0 shatter / 0.5 fade)
 * MEDIUM: 5.0s total (1.25 / 1.75 / 0.75 / 0.75 / 0.5)
 * LOW:    4.0s total (1.0 / 1.5 / 0.5 / 0.75 / 0.25)
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
            SplashQualityLevel.HIGH -> 1_500L
            SplashQualityLevel.MEDIUM -> 1_250L
            SplashQualityLevel.LOW -> 1_000L
        }

        fun convergeEndMs(quality: SplashQualityLevel): Long = when (quality) {
            SplashQualityLevel.HIGH -> 3_500L
            SplashQualityLevel.MEDIUM -> 3_000L
            SplashQualityLevel.LOW -> 2_500L
        }

        fun holdEndMs(quality: SplashQualityLevel): Long = when (quality) {
            SplashQualityLevel.HIGH -> 4_500L
            SplashQualityLevel.MEDIUM -> 3_750L
            SplashQualityLevel.LOW -> 3_000L
        }

        fun shatterEndMs(quality: SplashQualityLevel): Long = when (quality) {
            SplashQualityLevel.HIGH -> 5_500L
            SplashQualityLevel.MEDIUM -> 4_500L
            SplashQualityLevel.LOW -> 3_750L
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
