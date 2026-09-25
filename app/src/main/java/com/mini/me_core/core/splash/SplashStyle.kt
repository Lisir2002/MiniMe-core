package com.mini.me_core.core.splash

import com.mini.me_core.R

/**
 * User-selectable splash animation style.
 * Persisted in ThemeSettingsRepository.
 */
enum class SplashStyle(val labelRes: Int) {
    PARTICLE(R.string.splash_style_particle),
    MINIMAL(R.string.splash_style_minimal),
    CLASSIC(R.string.splash_style_classic),
    OFF(R.string.splash_style_off);

    companion object {
        const val KEY = "splash_style"

        fun fromPersisted(value: String?): SplashStyle =
            entries.firstOrNull { it.name == value } ?: PARTICLE
    }
}
