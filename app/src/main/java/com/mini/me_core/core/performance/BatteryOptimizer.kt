package com.mini.me_core.core.performance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.mini.me_core.datalayer.store.KVStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F6.7 电池优化：监听系统省电模式/电池状态，结合用户开关决定是否进入「省电模式」。
 *
 * 省电模式下的行为（由各消费方读取 [batterySaverActive]）：
 *  - 降低启动动画质量/关闭非必要动画；
 *  - 减少后台轮询与同步频率。
 *
 * 用户开关持久化在 KVStore（namespace="performance"，key="battery_saver"）。
 */
@Singleton
class BatteryOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kv: KVStore,
) {
    companion object {
        private const val NS = "performance"
        private const val KEY_BATTERY_SAVER = "battery_saver"
    }

    private val powerManager = context.getSystemService(PowerManager::class.java)

    private val _batterySaverActive = MutableStateFlow(false)
    /** 当前是否处于省电模式（用户开关开启 且 系统省电/低电量）。 */
    val batterySaverActive: StateFlow<Boolean> = _batterySaverActive.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            refresh()
        }
    }

    /** 注册监听。在 Application.onCreate 调用一次。 */
    fun start() {
        runCatching {
            val filter = IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            context.registerReceiver(receiver, filter)
            refresh()
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    /** 用户是否手动开启电池优化开关。 */
    fun isUserEnabled(): Boolean = kv.getBool(NS, KEY_BATTERY_SAVER) ?: false

    fun setUserEnabled(enabled: Boolean) {
        kv.putBool(NS, KEY_BATTERY_SAVER, enabled)
        refresh()
    }

    private fun refresh() {
        val userEnabled = isUserEnabled()
        val powerSave = powerManager?.isPowerSaveMode == true
        val batteryLow = isBatteryLow()
        _batterySaverActive.value = userEnabled && (powerSave || batteryLow)
    }

    private fun isBatteryLow(): Boolean {
        return runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level < 0 || scale <= 0) false else (level * 100 / scale) <= 15
        }.getOrDefault(false)
    }
}
