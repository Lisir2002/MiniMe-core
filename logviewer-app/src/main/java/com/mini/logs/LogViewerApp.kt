package com.mini.logs

import android.app.Application

/**
 * MiniMe Logs 附属应用入口。
 *
 * 独立 applicationId = com.mini.logs，只读主应用写入的日志文件，不写日志。
 */
class LogViewerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: LogViewerApp
            private set
    }
}
