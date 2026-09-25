package com.mini.me_core.core.applicationid

import com.mini.me_core.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据持久化守卫：applicationId 必须恒为 `com.mini.me_core`（release）或其 debug 后缀变体。
 *
 * 背景：包名（applicationId）变更在 Android 眼里是完全不同的 App，安装新包名是一次"全新安装"，
 * 私有数据目录为空，用户历史对话全部"消失"。本测试把 applicationId 锁死，
 * 防止未来误改包名再次造成数据丢失。
 *
 * debug 变体带 `.debug` 后缀（com.mini.me_core.debug），与 release 数据隔离；
 * release 变体恒为 com.mini.me_core。两种变体下本测试均应通过。
 */
class ApplicationIdStabilityTest {

    @Test
    fun `applicationId_恒为_com_mini_me_core_或其debug后缀变体`() {
        val id = BuildConfig.APPLICATION_ID
        // release: com.mini.me_core；debug: com.mini.me_core.debug
        assertTrue(
            "applicationId=$id 不在白名单内（期望 com.mini.me_core 或 com.mini.me_core.debug）",
            id == "com.mini.me_core" || id == "com.mini.me_core.debug"
        )
    }
}
