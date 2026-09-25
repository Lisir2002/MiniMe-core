package com.mini.me_core.feature.settings.presentation.component

/**
 * 设置页搜索引擎：拼音全拼/首字母 + 模糊匹配 + 同义词 + 编辑距离容错。
 *
 * 设计（对齐设计文档 F5.1）：
 * 1. 查询词按空格分词，每个 token 独立匹配
 * 2. 对 title(权重 3) / keywords(权重 2) / subtitle(权重 1) 分别打分并求和
 * 3. 拼音全拼匹配(0.8)、拼音首字母匹配(0.6)、直接子串匹配(1.0)
 * 4. 同义词扩展：查询词命中同义词表时，把同义词也纳入匹配
 * 5. 错别字容忍：编辑距离 ≤2 仍给低分匹配（0.3）
 * 6. 空查询返回 1.0（全部显示）
 */
internal object SearchUtils {

    /**
     * 拼音映射表（覆盖设置页用到的全部中文字符，去重）。
     * 手写表避免引入外部 pinyin 库依赖，覆盖本应用设置标题/副标题常用字即可。
     */
    private val PINYIN_MAP: Map<Char, String> = buildMap {
        // AI / 模型
        put('提', "ti"); put('供', "gong"); put('商', "shang"); put('模', "mo"); put('型', "xing")
        put('默', "mo"); put('认', "ren"); put('识', "shi"); put('图', "tu"); put('压', "ya")
        put('缩', "suo"); put('服', "fu"); put('务', "wu"); put('器', "qi"); put('工', "gong")
        put('具', "ju"); put('授', "shou"); put('权', "quan"); put('规', "gui"); put('则', "ze")
        put('配', "pei"); put('置', "zhi"); put('加', "jia"); put('载', "zai"); put('协', "xie")
        put('议', "yi"); put('连', "lian"); put('接', "jie"); put('状', "zhuang"); put('态', "tai")
        put('已', "yi"); put('未', "wei"); put('启', "qi"); put('用', "yong"); put('禁', "jin")
        put('心', "xin"); put('范', "fan"); put('力', "li"); put('限', "xian"); put('流', "liu")
        put('代', "dai"); put('络', "luo"); put('程', "cheng"); put('能', "neng")
        // 环境 / 终端
        put('终', "zhong"); put('端', "duan"); put('容', "rong"); put('镜', "jing"); put('像', "xiang")
        put('远', "yuan"); put('作', "zuo"); put('区', "qu")
        put('同', "tong"); put('步', "bu"); put('环', "huan"); put('境', "jing")
        put('命', "ming"); put('令', "ling"); put('主', "zhu"); put('机', "ji"); put('当', "dang")
        put('前', "qian"); put('内', "nei"); put('置', "zhi"); put('自', "zi"); put('定', "ding")
        put('义', "yi"); put('网', "wang")
        // 数据与安全
        put('备', "bei"); put('份', "fen"); put('还', "huan"); put('原', "yuan"); put('安', "an")
        put('全', "quan"); put('审', "shen"); put('计', "ji"); put('日', "ri"); put('志', "zhi")
        put('等', "deng"); put('密', "mi"); put('凭', "ping")
        put('据', "ju"); put('生', "sheng"); put('物', "wu"); put('别', "bie")
        put('导', "dao"); put('出', "chu"); put('入', "ru"); put('过', "guo"); put('滤', "lv")
        put('错', "cuo"); put('调', "diao"); put('试', "shi"); put('跟', "gen")
        put('踪', "zong"); put('事', "shi"); put('件', "jian"); put('证', "zheng"); put('书', "shu")
        // 系统与应用
        put('外', "wai"); put('观', "guan"); put('题', "ti"); put('语', "yu")
        put('言', "yan"); put('保', "bao"); put('活', "huo"); put('后', "hou"); put('台', "tai")
        put('更', "geng"); put('新', "xin"); put('许', "xu")
        put('可', "ke"); put('者', "zhe"); put('开', "kai"); put('发', "fa"); put('版', "ban")
        put('本', "ben"); put('号', "hao"); put('深', "shen"); put('色', "se"); put('浅', "qian")
        put('多', "duo"); put('杀', "sha"); put('死', "si"); put('进', "jin"); put('守', "shou")
        put('护', "hu"); put('通', "tong"); put('知', "zhi"); put('暂', "zan"); put('无', "wu")
        put('最', "zui"); put('近', "jin"); put('使', "shi"); put('修', "xiu"); put('改', "gai")
        put('删', "shan"); put('除', "chu"); put('名', "ming"); put('称', "cheng"); put('描', "miao")
        put('述', "shu"); put('创', "chuang"); put('建', "jian"); put('编', "bian"); put('辑', "ji")
        put('复', "fu"); put('粘', "zhan"); put('贴', "tie"); put('查', "cha")
        put('看', "kan"); put('显', "xian"); put('隐', "yin"); put('藏', "cang")
        // 通用
        put('设', "she"); put('管', "guan"); put('理', "li"); put('文', "wen"); put('件', "jian")
        put('数', "shu"); put('库', "ku"); put('页', "ye")
        put('搜', "sou"); put('索', "suo"); put('结', "jie"); put('果', "guo"); put('列', "lie")
        put('表', "biao"); put('信', "xin"); put('息', "xi"); put('对', "dui")
        put('目', "mu"); put('标', "biao"); put('组', "zu")
        put('运', "yun"); put('常', "chang"); put('异', "yi")
        put('退', "tui"); put('清', "qing"); put('空', "kong")
        put('重', "chong"); put('待', "dai"); put('办', "ban")
        put('完', "wan"); put('停', "ting"); put('恢', "hui")
        put('注', "zhu"); put('释', "shi"); put('说', "shuo"); put('明', "ming")
        put('确', "que"); put('定', "ding"); put('取', "qu"); put('消', "xiao"); put('提', "ti")
        put('交', "jiao"); put('存', "cun"); put('项', "xiang")
        put('下', "xia"); put('拉', "la"); put('勾', "gou")
        put('单', "dan"); put('击', "ji"); put('双', "shuang")
        put('长', "chang"); put('按', "an"); put('拖', "tuo"); put('拽', "zhuai"); put('缩', "suo")
        put('放', "fang"); put('滚', "gun"); put('动', "dong"); put('屏', "ping"); put('幕', "mu")
        put('窗', "chuang"); put('口', "kou"); put('话', "hua"); put('栏', "lan")
        put('钮', "niu"); put('链', "lian"); put('地', "di"); put('址', "zhi")
        put('路', "lu"); put('径', "jing"); put('本', "ben")
        put('代', "dai"); put('码', "ma"); put('脚', "jiao")
        put('控', "kong"); put('台', "tai"); put('面', "mian")
        put('板', "ban"); put('进', "jin"); put('度', "du"); put('条', "tiao")
        put('警', "jing"); put('功', "gong")
        put('失', "shi"); put('败', "bai"); put('正', "zheng")
        put('骤', "zhou"); put('允', "yun")
        put('拒', "ju"); put('绝', "jue"); put('永', "yong"); put('久', "jiu"); put('临', "lin")
        put('时', "shi"); put('每', "mei"); put('次', "ci"); put('总', "zong")
        put('否', "fou"); put('关', "guan"); put('停', "ting")
        put('止', "zhi"); put('局', "ju")
        put('试', "shi"); put('追', "zhui")
        put('覆', "fu"); put('盖', "gai"); put('溢', "yi")
        put('刷', "shua"); put('读', "du"); put('写', "xie"); put('入', "ru")
        put('移', "yi")
        put('剪', "jian"); put('切', "qie")
        put('预', "yu"); put('览', "lan"); put('打', "da"); put('印', "yin")
        put('布', "bu"); put('局', "ju"); put('排', "pai")
        put('齐', "qi"); put('距', "ju")
        put('边', "bian"); put('颜', "yan"); put('字', "zi")
        put('体', "ti"); put('大', "da"); put('小', "xiao"); put('粗', "cu"); put('细', "xi")
        put('背', "bei"); put('景', "jing"); put('前', "qian"); put('阴', "yin")
        put('影', "ying"); put('角', "jiao"); put('半', "ban"); put('径', "jing")
        put('框', "kuang"); put('分', "fen"); put('隔', "ge"); put('线', "xian")
        put('图', "tu"); put('接', "jie")
        put('超', "chao"); put('航', "hang"); put('包', "bao")
        put('菜', "cai"); put('单', "dan")
        put('滑', "hua"); put('块', "kuai"); put('光', "guang"); put('标', "biao")
        put('鼠', "shu"); put('键', "jian"); put('盘', "pan"); put('快', "kuai")
        put('捷', "jie"); put('组', "zu"); put('合', "he"); put('热', "re")
        put('手', "shou"); put('势', "shi"); put('触', "chu")
        put('点', "dian")
        put('旋', "xuan"); put('转', "zhuan")
        put('弃', "qi"); put('持', "chi")
        put('初', "chu"); put('始', "shi"); put('化', "hua")
        put('上', "shang"); put('传', "chuan"); put('载', "zai")
        put('构', "gou"); put('译', "yi"); put('包', "bao")
        put('布', "bu"); put('署', "shu"); put('维', "wei")
        put('监', "jian"); put('控', "kong"); put('告', "gao"); put('指', "zhi")
        put('采', "cai"); put('集', "ji")
        put('析', "xi"); put('统', "tong"); put('报', "bao")
        put('曲', "qu"); put('线', "xian")
        put('方', "fang"); put('饼', "bing")
        put('折', "zhe"); put('柱', "zhu")
        put('散', "san"); put('雷', "lei")
        put('达', "da"); put('力', "li")
        put('矩', "ju"); put('阵', "zhen")
        put('漏', "lou"); put('洞', "dong")
        put('扫', "sao"); put('描', "miao"); put('检', "jian"); put('测', "ce"); put('防', "fang")
        put('火', "huo"); put('墙', "qiang"); put('侵', "qin")
        put('解', "jie"); put('密', "mi")
        put('签', "qian"); put('名', "ming")
        put('访', "fang"); put('问', "wen")
        put('白', "bai"); put('黑', "hei")
    }

    /**
     * 同义词表：查询词 -> 扩展搜索词列表。
     * 例：搜「外观」也匹配「主题」「背景」；搜「深色」也匹配「暗黑」。
     * key 一律小写。
     */
    private val SYNONYM_MAP: Map<String, List<String>> = buildMap {
        put("外观", listOf("主题", "背景", "theme", "appearance"))
        put("appearance", listOf("外观", "主题", "背景"))
        put("主题", listOf("外观", "背景", "theme"))
        put("theme", listOf("主题", "外观", "背景"))
        put("背景", listOf("主题", "外观"))
        put("深色", listOf("主题", "暗黑", "dark"))
        put("dark", listOf("深色", "主题"))
        put("模式", listOf("mode"))
        put("mode", listOf("模式"))
        put("备份", listOf("导出", "backup", "backup", "restore"))
        put("backup", listOf("备份", "导出", "恢复"))
        put("导出", listOf("备份", "export"))
        put("export", listOf("导出", "备份"))
        put("日志", listOf("log", "debug", "trace"))
        put("log", listOf("日志", "debug"))
        put("安全", listOf("security", "隐私", "加密"))
        put("security", listOf("安全", "隐私"))
        put("终端", listOf("terminal", "shell", "容器"))
        put("terminal", listOf("终端", "shell", "容器"))
        put("容器", listOf("terminal", "终端", "container"))
        put("模型", listOf("model", "provider", "供应商"))
        put("model", listOf("模型", "供应商"))
        put("供应商", listOf("provider", "模型"))
        put("provider", listOf("供应商", "模型"))
    }

    /** 获取单个汉字的拼音（小写），非汉字返回原字符小写 */
    private fun Char.toPinyin(): String = PINYIN_MAP[this]?.lowercase() ?: this.lowercase().toString()

    /** 将字符串转为拼音（空格分隔每个字） */
    fun toPinyin(text: String): String = text.map { it.toPinyin() }.joinToString(" ")

    /** 获取拼音首字母 */
    private fun Char.toPinyinInitial(): Char? = PINYIN_MAP[this]?.firstOrNull()?.lowercaseChar()

    /** 将字符串转为拼音首字母串 */
    fun toPinyinInitials(text: String): String = text.mapNotNull { it.toPinyinInitial() }.joinToString("")

    /** 是否为纯 ASCII 字符串 */
    private fun String.isAscii(): Boolean = all { it.code < 128 }

    /**
     * 计算两字符串的编辑距离（Levenshtein）。
     * 仅用于短文本（设置标题/拼音词），O(n*m) 可接受。
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,        // 删除
                    dp[j - 1] + 1,    // 插入
                    prev + cost       // 替换
                )
                prev = temp
            }
        }
        return dp[b.length]
    }

    /**
     * 对单个搜索 token 匹配一个文本字段，返回匹配分数。
     * - 直接子串匹配: weight * 1.0
     * - 拼音全拼匹配: weight * 0.8
     * - 拼音首字母匹配: weight * 0.6
     * - 编辑距离 ≤2 容错: weight * 0.3
     */
    private fun matchToken(token: String, fieldText: String, weight: Int): Double {
        if (token.isEmpty() || fieldText.isEmpty()) return 0.0
        val lowerToken = token.lowercase()
        val lowerField = fieldText.lowercase()

        // 1. 直接子串匹配（最高优先级）
        if (lowerField.contains(lowerToken)) return weight * 1.0

        // 2. 拼音匹配（仅当 token 是 ASCII 且 field 包含中文时）
        if (lowerToken.isAscii()) {
            val pinyinField = toPinyin(lowerField)
            if (pinyinField.contains(lowerToken)) return weight * 0.8

            val initialsField = toPinyinInitials(lowerField)
            if (initialsField.isNotEmpty() && initialsField.contains(lowerToken)) return weight * 0.6
        }

        // 3. 错别字容忍：编辑距离 ≤2（仅对长度 ≥2 的 token，避免过短词误匹配）
        if (lowerToken.length >= 2) {
            // 3a. token 是拼音，对 field 的拼音逐词做编辑距离
            if (lowerToken.isAscii()) {
                val pinyinWords = toPinyin(lowerField).split(' ')
                for (w in pinyinWords) {
                    if (w.length >= 2 && levenshtein(lowerToken, w) <= 2) return weight * 0.3
                }
            } else {
                // 3b. token 是中文，直接对 field 做整体编辑距离
                if (levenshtein(lowerToken, lowerField) <= 2) return weight * 0.3
            }
        }

        return 0.0
    }

    /**
     * 对 MenuItem 进行搜索打分。
     * 查询词按空格分词，每个 token 分别匹配 title(3) / keywords(2) / subtitle(1)，
     * 累加所有 token 得分后返回。命中同义词表时把同义词也纳入匹配。
     */
    fun score(menuItem: MenuItem, query: String): Double {
        val queryTrimmed = query.trim()
        if (queryTrimmed.isEmpty()) return 1.0 // 空查询→全部显示

        val tokens = queryTrimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return 0.0

        // 展开同义词：每个 token 扩展为 [原词] + 同义词
        val expandedTokens = tokens.flatMap { token ->
            val syns = SYNONYM_MAP[token.lowercase()] ?: emptyList()
            listOf(token.lowercase()) + syns
        }.distinct()

        val keywordsText = menuItem.keywords.joinToString(" ")

        return expandedTokens.sumOf { token ->
            var score = 0.0
            score += matchToken(token, menuItem.title, 3)
            score += matchToken(token, keywordsText, 2)
            score += matchToken(token, menuItem.subtitle, 1)
            score
        }
    }

    /**
     * 从 title + subtitle 中自动提取关键词（用于补充硬编码 keywords）。
     */
    fun extractKeywords(title: String, subtitle: String): List<String> {
        val combined = "$title $subtitle"
        val chineseTokens = Regex("[\\u4e00-\\u9fff]{2,}")
            .findAll(combined)
            .map { it.value }
            .toList()
        return chineseTokens.distinct()
    }
}
