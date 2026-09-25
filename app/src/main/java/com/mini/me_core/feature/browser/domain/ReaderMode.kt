package com.mini.me_core.feature.browser.domain

import org.json.JSONArray
import org.json.JSONObject

/** 阅读模式正文块（F4.4）。 */
data class ReaderBlock(
    val type: String,   // "p" 段落 / "img" 图片
    val text: String = "",
    val src: String = ""
)

/** 阅读模式提取出的文章。 */
data class ReaderArticle(
    val title: String = "",
    val blocks: List<ReaderBlock> = emptyList(),
    val prevUrl: String = "",
    val nextUrl: String = ""
)

/**
 * F4.4 阅读模式：通过注入 JS 提取文章正文（去除导航/广告/侧栏）。
 *
 * 纯静态工具：提供检测与正文提取脚本，由 [BrowserController] 在 WebView 中执行并回传 JSON。
 */
object ReaderMode {

    /**
     * 检测当前页面是否为文章类（有大段正文）。返回布尔字符串 "true"/"false"。
     * 启发式：article/main 容器内 <p> 文本总量超过阈值。
     */
    const val JS_DETECT = """
        (function() {
          var best = 0;
          document.querySelectorAll('article, main').forEach(function(el){
            var t = (el.innerText||'').length;
            if (t > best) best = t;
          });
          if (best < 800) {
            var body = (document.body && document.body.innerText || '').length;
            best = body;
          }
          return best > 1200 ? 'true' : 'false';
        })();
    """

    /** 提取正文：返回 JSON 字符串 {title, blocks:[{type,text,src}], prevUrl, nextUrl}。 */
    const val JS_EXTRACT = """
        (function() {
          var best = null, bestScore = 0;
          document.querySelectorAll('article, main').forEach(function(el){
            var score = (el.innerText||'').length + el.querySelectorAll('p').length * 50;
            if (score > bestScore) { bestScore = score; best = el; }
          });
          if (!best) best = document.body;
          if (best) {
            best.querySelectorAll('nav,aside,footer,header,script,style,iframe,form,button,input,select,textarea,svg,canvas').forEach(function(n){ n.remove(); });
            best.querySelectorAll('[class]').forEach(function(n){
              var c = (n.className && n.className.toString()) || '';
              if (/ad|ads|advert|banner|sidebar|comment|related|recommend|share|popup/i.test(c)) n.remove();
            });
          }
          var blocks = [];
          if (best) {
            best.querySelectorAll('p').forEach(function(p){
              var t = (p.innerText||'').trim();
              if (t.length > 30) blocks.push({type:'p', text: t});
            });
            if (blocks.length === 0) {
              (best.innerText||'').split(/\n+/).forEach(function(line){
                if (line.trim().length > 30) blocks.push({type:'p', text: line.trim()});
              });
            }
            best.querySelectorAll('img').forEach(function(img){
              if (img.src && img.src.indexOf('data:') !== 0) blocks.push({type:'img', src: img.src});
            });
          }
          var prevUrl = '', nextUrl = '';
          document.querySelectorAll('link[rel="prev"], a[rel="prev"]').forEach(function(n){ prevUrl = n.href || n.getAttribute('href') || ''; });
          document.querySelectorAll('link[rel="next"], a[rel="next"]').forEach(function(n){ nextUrl = n.href || n.getAttribute('href') || ''; });
          return JSON.stringify({
            title: document.title || '',
            blocks: blocks.slice(0, 300),
            prevUrl: prevUrl,
            nextUrl: nextUrl
          });
        })();
    """

    /** 解析 [evaluateJavascript] 回传的 JSON 字符串为 [ReaderArticle]。 */
    fun parse(raw: String?): ReaderArticle {
        if (raw.isNullOrBlank()) return ReaderArticle()
        return runCatching {
            var s = raw.trim()
            if (s.startsWith("\"") && s.endsWith("\"")) s = org.json.JSONObject.quote(s).let { raw }
            // evaluateJavascript 可能返回带引号的 JSON 字符串
            val unquoted = if (s.startsWith("\"")) {
                org.json.JSONTokener(s).nextValue().toString()
            } else s
            val obj = JSONObject(unquoted)
            val arr: JSONArray = obj.optJSONArray("blocks") ?: JSONArray()
            val list = (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                ReaderBlock(
                    type = b.optString("type", "p"),
                    text = b.optString("text", ""),
                    src = b.optString("src", "")
                )
            }
            ReaderArticle(
                title = obj.optString("title", ""),
                blocks = list,
                prevUrl = obj.optString("prevUrl", ""),
                nextUrl = obj.optString("nextUrl", "")
            )
        }.getOrDefault(ReaderArticle())
    }
}
