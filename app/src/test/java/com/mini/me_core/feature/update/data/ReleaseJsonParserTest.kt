package com.mini.me_core.feature.update.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReleaseJsonParser] 解析单测：覆盖 /releases/latest 与 /releases?per_page=30 的 JSON 形态，
 * 包括 assets 提取、APK 选择、draft/prerelease 标记、缺字段容错。
 */
class ReleaseJsonParserTest {

    private val sampleLatest = """
    {
      "tag_name": "v0.0.0.21",
      "name": "v0.0.0.21 正式版",
      "published_at": "2026-09-20T10:00:00Z",
      "body": "## 更新内容\n- 修复 A\n- 新增 B\n\n**注意事项**",
      "html_url": "https://github.com/Lisir2002/MiniMe-core/releases/tag/v0.0.0.21",
      "draft": false,
      "prerelease": false,
      "assets": [
        {
          "name": "MiniMe-core-0.0.0.21.apk",
          "browser_download_url": "https://github.com/.../app.apk",
          "size": 12345678,
          "content_type": "application/vnd.android.package-archive"
        },
        {
          "name": "source.zip",
          "browser_download_url": "https://github.com/.../source.zip",
          "size": 999,
          "content_type": "application/zip"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun parse_latest_extractsFields() {
        val r = ReleaseJsonParser.parseOne(sampleLatest)
        assertEquals("v0.0.0.21", r.tag)
        assertEquals("v0.0.0.21 正式版", r.name)
        assertEquals("0.0.0.21", r.versionName)
        assertFalse(r.isDraft)
        assertFalse(r.isPrerelease)
        assertNotNull(r.body)
        assertTrue(r.publishedAt > 0L)
    }

    @Test
    fun parse_latest_prefersApkAsset() {
        val r = ReleaseJsonParser.parseOne(sampleLatest)
        assertEquals(2, r.assets.size)
        assertTrue(r.hasApk)
        assertEquals("https://github.com/.../app.apk", r.downloadUrl)
        assertEquals(12345678L, r.fileSizeBytes)
    }

    @Test
    fun parse_summary_takesFirstNonHeadingLines() {
        val r = ReleaseJsonParser.parseOne(sampleLatest)
        val s = r.summary()
        // 应跳过 ## 标题行，取 - 修复 A / - 新增 B
        assertTrue(s.contains("修复 A"))
        assertFalse(s.startsWith("#"))
    }

    @Test
    fun parse_prereleaseAndDraftMarks() {
        val json = """
        {
          "tag_name": "v1.0.0-beta.1",
          "name": "",
          "draft": true,
          "prerelease": true,
          "assets": []
        }
        """.trimIndent()
        val r = ReleaseJsonParser.parseOne(json)
        assertTrue(r.isDraft)
        assertTrue(r.isPrerelease)
        // name 为空时回退 tag
        assertEquals("v1.0.0-beta.1", r.name)
        assertFalse(r.hasApk)
        assertNull(r.downloadUrl)
    }

    @Test
    fun parse_list_filtersAndMaps() {
        val list = """
        [
          {"tag_name": "v2.0.0", "name": "", "draft": false, "prerelease": false, "assets": []},
          {"tag_name": "v1.9.0", "name": "", "draft": false, "prerelease": false, "assets": []},
          {"broken": true}
        ]
        """.trimIndent()
        val releases = ReleaseJsonParser.parseList(list)
        // 第三条 broken 缺 tag_name，应被 mapNotNull 过滤
        assertEquals(2, releases.size)
        assertEquals("v2.0.0", releases[0].tag)
        assertEquals("v1.9.0", releases[1].tag)
    }

    @Test
    fun parse_missingHtmlUrl_fallsBack() {
        val json = """{"tag_name": "v0.1.0", "draft": false, "prerelease": false, "assets": []}"""
        val r = ReleaseJsonParser.parseOne(json)
        assertEquals(ReleaseJsonParser.FALLBACK_RELEASES_URL, r.htmlUrl)
    }
}
