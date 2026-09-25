package com.mini.me_core.core.splash

/**
 * Splash animation stage timeline (total 6000ms).
 *
 * 0.0-1.5s  EXPLODE     mixed particles burst outward from center with trails
 * 1.5-3.5s  CONVERGE    all particles fly to form the "MiniMe-core" text shape
 * 3.5-4.5s  HOLD        text fully formed, breathing scale + shimmer sweep
 * 4.5-5.5s  SHATTER     text particles explode + screen glass shatters in 3D
 * 5.5-6.0s  FADE_OUT    shards fade, main screen fades in underneath
 */
enum class SplashStage {
    EXPLODE,
    CONVERGE,
    HOLD,
    SHATTER,
    FADE_OUT,
    COMPLETE;

    companion object {
        const val TOTAL_DURATION_MS = 6_000L
        const val EXPLODE_END_MS = 1_500L
        const val CONVERGE_END_MS = 3_500L
        const val HOLD_END_MS = 4_500L
        const val SHATTER_END_MS = 5_500L

        fun fromElapsed(elapsedMs: Long): SplashStage = when {
            elapsedMs < EXPLODE_END_MS -> EXPLODE
            elapsedMs < CONVERGE_END_MS -> CONVERGE
            elapsedMs < HOLD_END_MS -> HOLD
            elapsedMs < SHATTER_END_MS -> SHATTER
            elapsedMs < TOTAL_DURATION_MS -> FADE_OUT
            else -> COMPLETE
        }

        /** Progress within the current stage, 0..1 */
        fun stageProgress(elapsedMs: Long): Float = when (fromElapsed(elapsedMs)) {
            EXPLODE -> elapsedMs.toFloat() / EXPLODE_END_MS
            CONVERGE -> (elapsedMs - EXPLODE_END_MS).toFloat() / (CONVERGE_END_MS - EXPLODE_END_MS)
            HOLD -> (elapsedMs - CONVERGE_END_MS).toFloat() / (HOLD_END_MS - CONVERGE_END_MS)
            SHATTER -> (elapsedMs - HOLD_END_MS).toFloat() / (SHATTER_END_MS - HOLD_END_MS)
            FADE_OUT -> (elapsedMs - SHATTER_END_MS).toFloat() / (TOTAL_DURATION_MS - SHATTER_END_MS)
            COMPLETE -> 1f
        }

        /** Remaining seconds for the skip button countdown, 6..0 */
        fun remainingSeconds(elapsedMs: Long): Int {
            val secs = elapsedMs / 1000L
            return (TOTAL_DURATION_MS / 1000L - secs).toInt().coerceAtLeast(0)
        }
    }
}
