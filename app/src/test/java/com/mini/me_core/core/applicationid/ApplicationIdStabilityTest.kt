package com.mini.me_core.core.applicationid

import com.mini.me_core.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 数据持久化守卫：release 变体的 applicationId 必须恒为 `com.mini.me_core`。
 *
 * 背景：包名（applicationId）变更在 Android 眼里是完全不同的 App，安装新包名是一次"全新安装"，
 * 私有数据目录为空，用户历史对话全部"消失"。本测试把 release applicationId 锁死，
 * 防止未来误改包名再次造成数据丢失。
 *
 * 注意：debug 变体带 `.debug` 后缀（com.mini.me_core.debug），因此本测试只对 release 变体成立，
 * 必须通过 `:app:testReleaseUnitTest`（CI 门禁同款）运行。
 */
class ApplicationIdStabilityTest {

    @Test
    fun `release_applicationId_恒为_com_mini_me_core`() {
        assertEquals("com.mini.me_core", BuildConfig.APPLICATION_ID)
    }
}
