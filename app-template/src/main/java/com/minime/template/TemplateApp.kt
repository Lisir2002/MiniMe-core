package com.minime.template

import android.app.Application

/**
 * 模版应用 Application 类
 *
 * 负责全局初始化：崩溃日志捕获、配置预加载等
 */
class TemplateApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 初始化崩溃日志捕获（必须在最前面，确保能捕获到后续所有初始化阶段的崩溃）
        CrashHandler.init(this)
    }
}
