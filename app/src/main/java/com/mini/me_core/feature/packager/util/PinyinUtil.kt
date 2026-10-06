package com.mini.me_core.feature.packager.util

/**
 * 拼音转换工具
 *
 * 用于从中文应用名自动生成包名。
 * 采用轻量级实现，不依赖外部拼音库，覆盖常用汉字。
 * 对于无法识别的字符，直接保留原字符（转为小写）。
 */
object PinyinUtil {

    /**
     * 将字符串转换为拼音（小写，空格转下划线，去除特殊字符）
     *
     * @param input 输入字符串（中文/英文/数字混合）
     * @return 拼音字符串，如 "我的博客" → "wodeboke"
     */
    fun toPinyin(input: String): String {
        if (input.isBlank()) return ""

        val result = StringBuilder()
        for (char in input) {
            when {
                // 中文字符：查拼音表
                char.code in 0x4E00..0x9FFF -> {
                    val pinyin = PINYIN_MAP[char]
                    if (pinyin != null) {
                        result.append(pinyin)
                    }
                    // 无法识别的汉字跳过
                }
                // 英文字母：转小写
                char.isLetter() -> result.append(char.lowercaseChar())
                // 数字：保留
                char.isDigit() -> result.append(char)
                // 空格或连字符：转下划线
                char == ' ' || char == '-' || char == '_' -> result.append('_')
                // 其他特殊字符：跳过
            }
        }

        // 清理：连续下划线合并，首尾下划线去除
        return result.toString()
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    /**
     * 从应用名生成包名
     *
     * @param appName 应用名称
     * @return 包名，如 "我的博客" → "com.minime.app.wodeboke"
     */
    fun generatePackageName(appName: String): String {
        val pinyin = toPinyin(appName)
        val safeName = if (pinyin.isBlank()) {
            // 如果拼音为空（全是特殊字符），使用时间戳后6位
            "app${System.currentTimeMillis() % 1000000}"
        } else {
            // 确保以字母开头
            if (pinyin[0].isLetter()) pinyin else "app_$pinyin"
        }
        return Project_PACKAGE_PREFIX + safeName
    }

    private const val Project_PACKAGE_PREFIX = "com.minime.app."

    /**
     * 常用汉字拼音表（覆盖常用3500汉字中的高频字）
     * 格式：汉字 → 拼音（无声调，小写）
     *
     * 注意：这是一个精简表，覆盖日常应用名中最常用的汉字。
     * 对于表中不存在的汉字，toPinyin 会跳过该字符。
     */
    private val PINYIN_MAP: Map<Char, String> = buildMap {
        // 常用字按拼音首字母分组
        val pinyinData = listOf(
            // A
            "啊a", "阿a", "爱ai", "安an", "昂ang", "奥ao",
            // B
            "八ba", "巴ba", "把ba", "爸ba", "白bai", "百bai", "拜bai",
            "班ban", "半ban", "办ban", "帮bang", "包bao", "宝bao", "保bao",
            "报bao", "北bei", "备bei", "背bei", "被bei", "本ben", "比bi",
            "笔bi", "必bi", "毕bi", "闭bi", "边bian", "编bian", "便bian",
            "变bian", "遍bian", "标biao", "表biao", "别bie", "宾bin", "冰bing",
            "兵bing", "丙bing", "病bing", "拨bo", "波bo", "博bo", "补bu",
            "不bu", "布bu", "步bu", "部bu",
            // C
            "擦ca", "猜cai", "才cai", "财cai", "采cai", "彩cai", "菜cai",
            "参can", "餐can", "残can", "蚕can", "惨can", "灿can", "仓cang",
            "苍cang", "藏cang", "操cao", "草cao", "册ce", "侧ce", "测ce",
            "层ceng", "叉cha", "插cha", "查cha", "茶cha", "察cha", "差cha",
            "拆chai", "柴chai", "产chan", "缠chan", "蝉chan", "馋chan", "产chan",
            "长chang", "肠chang", "尝chang", "常chang", "场chang", "厂chang",
            "畅chang", "唱chang", "倡chang", "超chao", "朝chao", "潮chao",
            "吵chao", "炒chao", "车che", "扯che", "彻che", "沉chen", "陈chen",
            "晨chen", "臣chen", "尘chen", "衬chen", "称cheng", "城cheng",
            "程cheng", "成cheng", "承cheng", "乘cheng", "诚cheng", "惩cheng",
            "澄cheng", "吃chi", "池chi", "迟chi", "持chi", "匙chi", "尺chi",
            "齿chi", "耻chi", "翅chi", "充chong", "冲chong", "虫chong", "崇chong",
            "抽chou", "仇chou", "愁chou", "筹chou", "酬chou", "丑chou", "臭chou",
            "出chu", "初chu", "除chu", "厨chu", "锄chu", "础chu", "储chu",
            "楚chu", "触chu", "处chu", "川chuan", "穿chuan", "传chuan", "船chuan",
            "喘chuan", "串chuan", "窗chuang", "床chuang", "创chuang", "吹chui",
            "垂chui", "锤chui", "春chun", "纯chun", "唇chun", "醇chun", "词ci",
            "慈ci", "瓷ci", "辞ci", "磁ci", "雌ci", "此ci", "次ci", "刺ci",
            "从cong", "丛cong", "凑cou", "粗cu", "促cu", "催cui", "脆cui",
            "翠cui", "村cun", "存cun", "寸cun", "搓cuo", "错cuo",
            // D
            "搭da", "达da", "答da", "打da", "大da", "呆dai", "带dai", "代dai",
            "待dai", "袋dai", "逮dai", "戴dai", "单dan", "担dan", "胆dan",
            "旦dan", "但dan", "诞dan", "弹dan", "淡dan", "蛋dan", "当dang",
            "挡dang", "党dang", "荡dang", "刀dao", "导dao", "岛dao", "倒dao",
            "蹈dao", "到dao", "悼dao", "道dao", "盗dao", "得de", "德de",
            "的de", "灯deng", "登deng", "等deng", "凳deng", "低di", "堤di",
            "滴di", "迪di", "敌di", "笛di", "底di", "抵di", "地di", "弟di",
            "帝di", "递di", "第di", "颠dian", "典dian", "点dian", "电dian",
            "店dian", "垫dian", "殿dian", "雕diao", "吊diao", "调diao", "掉diao",
            "爹die", "跌die", "叠die", "蝶die", "丁ding", "顶ding", "鼎ding",
            "定ding", "订ding", "丢diu", "东dong", "冬dong", "懂dong", "动dong",
            "冻dong", "洞dong", "都dou", "斗dou", "抖dou", "陡dou", "豆dou",
            "逗dou", "毒du", "读du", "独du", "堵du", "赌du", "杜du", "肚du",
            "度du", "渡du", "端duan", "短duan", "段duan", "断duan", "缎duan",
            "堆dui", "队dui", "对dui", "吨dun", "敦dun", "蹲dun", "盾dun",
            "顿dun", "多duo", "夺duo", "朵duo", "躲duo", "堕duo", "惰duo",
            // E
            "鹅e", "额e", "讹e", "恶e", "饿e", "恩en", "儿er", "而er", "尔er",
            "耳er", "饵er", "二er", "贰er",
            // F
            "发fa", "乏fa", "罚fa", "阀fa", "法fa", "帆fan", "番fan", "翻fan",
            "凡fan", "烦fan", "繁fan", "反fan", "返fan", "犯fan", "泛fan",
            "饭fan", "范fan", "贩fan", "方fang", "坊fang", "芳fang", "防fang",
            "妨fang", "房fang", "肪fang", "仿fang", "访fang", "纺fang", "放fang",
            "飞fei", "妃fei", "非fei", "啡fei", "肥fei", "匪fei", "诽fei",
            "废fei", "沸fei", "肺fei", "费fei", "分fen", "芬fen", "纷fen",
            "坟fen", "焚fen", "粉fen", "份fen", "奋fen", "愤fen", "粪fen",
            "风feng", "丰feng", "封feng", "疯feng", "峰feng", "锋feng", "蜂feng",
            "逢feng", "缝feng", "讽feng", "凤feng", "奉feng", "佛fo", "否fou",
            "夫fu", "肤fu", "孵fu", "扶fu", "拂fu", "服fu", "浮fu", "符fu",
            "幅fu", "福fu", "抚fu", "辅fu", "俯fu", "釜fu", "腐fu", "父fu",
            "付fu", "妇fu", "负fu", "附fu", "复fu", "赴fu", "覆fu", "富fu",
            "腹fu", "赋fu", "缚fu",
            // G
            "该gai", "改gai", "盖gai", "概gai", "干gan", "甘gan", "杆gan", "肝gan",
            "赶gan", "敢gan", "感gan", "刚gang", "岗gang", "港gang", "高gao",
            "糕gao", "搞gao", "稿gao", "告gao", "哥ge", "歌ge", "阁ge", "革ge",
            "格ge", "葛ge", "隔ge", "个ge", "各ge", "给gei", "根gen", "跟gen",
            "亘gen", "更geng", "耕geng", "工gong", "弓gong", "公gong", "功gong",
            "攻gong", "宫gong", "恭gong", "供gong", "躬gong", "巩gong", "共gong",
            "贡gong", "勾gou", "沟gou", "钩gou", "狗gou", "垢gou", "构gou",
            "购gou", "够gou", "估gu", "姑gu", "孤gu", "辜gu", "古gu", "谷gu",
            "股gu", "骨gu", "鼓gu", "固gu", "故gu", "顾gu", "瓜gua", "刮gua",
            "挂gua", "拐guai", "怪guai", "关guan", "观guan", "官guan", "冠guan",
            "馆guan", "管guan", "贯guan", "惯guan", "灌guan", "光guang", "广guang",
            "归gui", "龟gui", "规gui", "硅gui", "轨gui", "鬼gui", "柜gui", "贵gui",
            "桂gui", "跪gui", "滚gun", "棍gun", "锅guo", "国guo", "果guo",
            "裹guo", "过guo",
            // H
            "哈ha", "孩hai", "海hai", "害hai", "酣han", "寒han", "韩han", "罕han",
            "喊han", "汉han", "汗han", "旱han", "悍han", "焊han", "翰han", "杭hang",
            "航hang", "壕hao", "好hao", "号hao", "浩hao", "耗hao", "呵he", "喝he",
            "合he", "何he", "和he", "河he", "核he", "荷he", "盒he", "贺he", "褐he",
            "鹤he", "黑hei", "痕hen", "很hen", "狠hen", "恨hen", "哼heng", "恒heng",
            "横heng", "衡heng", "轰hong", "哄hong", "红hong", "宏hong", "洪hong",
            "虹hong", "鸿hong", "喉hou", "猴hou", "吼hou", "后hou", "厚hou", "候hou",
            "呼hu", "乎hu", "忽hu", "狐hu", "胡hu", "壶hu", "湖hu", "蝴hu", "糊hu",
            "虎hu", "互hu", "户hu", "护hu", "花hua", "华hua", "哗hua", "猾hua",
            "滑hua", "画hua", "话hua", "怀huai", "淮huai", "坏huai", "欢huan",
            "环huan", "还huan", "缓huan", "幻huan", "换huan", "唤huan", "焕huan",
            "涣huan", "荒huang", "慌huang", "皇huang", "黄huang", "煌huang", "惶huang",
            "晃huang", "谎huang", "灰hui", "挥hui", "辉hui", "徽hui", "回hui",
            "毁hui", "悔hui", "汇hui", "会hui", "绘hui", "贿hui", "秽hui", "昏hun",
            "婚hun", "魂hun", "浑hun", "混hun", "豁huo", "活huo", "火huo", "伙huo",
            "或huo", "货huo", "获huo", "祸huo", "惑huo",
            // J
            "击ji", "饥ji", "迹ji", "积ji", "基ji", "绩ji", "缉ji", "激ji", "及ji",
            "吉ji", "级ji", "即ji", "极ji", "急ji", "疾ji", "棘ji", "集ji", "籍ji",
            "几ji", "己ji", "挤ji", "脊ji", "计ji", "记ji", "技ji", "际ji", "季ji",
            "剂ji", "既ji", "济ji", "继ji", "寄ji", "寂ji", "加jia", "佳jia", "家jia",
            "嘉jia", "夹jia", "佳jia", "甲jia", "假jia", "价jia", "架jia", "驾jia",
            "嫁jia", "歼jian", "坚jian", "间jian", "肩jian", "艰jian", "兼jian",
            "监jian", "煎jian", "拣jian", "茧jian", "捡jian", "简jian", "俭jian",
            "剪jian", "减jian", "荐jian", "鉴jian", "践jian", "贱jian", "见jian",
            "键jian", "箭jian", "件jian", "建jian", "健jian", "剑jian", "渐jian",
            "溅jian", "江jiang", "将jiang", "姜jiang", "僵jiang", "疆jiang", "讲jiang",
            "奖jiang", "桨jiang", "蒋jiang", "匠jiang", "降jiang", "蕉jiao", "交jiao",
            "郊jiao", "浇jiao", "骄jiao", "娇jiao", "嚼jiao", "角jiao", "脚jiao",
            "狡jiao", "绞jiao", "饺jiao", "缴jiao", "叫jiao", "轿jiao", "较jiao",
            "教jiao", "阶jie", "皆jie", "接jie", "秸jie", "街jie", "节jie", "劫jie",
            "杰jie", "捷jie", "睫jie", "截jie", "竭jie", "姐jie", "解jie", "介jie",
            "戒jie", "届jie", "界jie", "借jie", "巾jin", "斤jin", "金jin", "今jin",
            "津jin", "筋jin", "襟jin", "紧jin", "锦jin", "谨jin", "进jin", "近jin",
            "浸jin", "尽jin", "劲jin", "晋jin", "禁jin", "京jing", "经jing", "茎jing",
            "睛jing", "精jing", "晶jing", "鲸jing", "井jing", "警jing", "景jing",
            "颈jing", "静jing", "境jing", "镜jing", "径jing", "痉jing", "靖jing",
            "究jiu", "纠jiu", "揪jiu", "九jiu", "久jiu", "酒jiu", "旧jiu", "臼jiu",
            "舅jiu", "救jiu", "就jiu", "疚jiu", "拘ju", "居ju", "驹ju", "菊ju",
            "局ju", "咀ju", "矩ju", "举ju", "沮ju", "句ju", "巨ju", "具ju", "俱ju",
            "剧ju", "惧ju", "距ju", "锯ju", "聚ju", "捐juan", "娟juan", "卷juan",
            "倦juan", "眷juan", "决jue", "诀jue", "绝jue", "觉jue", "掘jue", "崛jue",
            "爵jue", "嚼jue", "军jun", "均jun", "菌jun", "钧jun", "君jun", "俊jun",
            "峻jun", "竣jun",
            // K
            "卡ka", "开kai", "凯kai", "慨kai", "刊kan", "勘kan", "看kan", "康kang",
            "慷kang", "糠kang", "扛kang", "抗kang", "炕kang", "考kao", "拷kao", "烤kao",
            "靠kao", "坷ke", "苛ke", "柯ke", "棵ke", "颗ke", "科ke", "壳ke", "咳ke",
            "可ke", "渴ke", "克ke", "刻ke", "客ke", "课ke", "肯ken", "啃ken", "坑keng",
            "空kong", "孔kong", "恐kong", "控kong", "口kou", "扣kou", "寇kou", "枯ku",
            "哭ku", "窟ku", "苦ku", "酷ku", "裤ku", "夸kua", "垮kua", "跨kua", "块kuai",
            "快kuai", "宽kuan", "款kuan", "筐kuang", "狂kuang", "框kuang", "矿kuang",
            "旷kuang", "况kuang", "亏kui", "盔kui", "葵kui", "奎kui", "魁kui", "傀kui",
            "馈kui", "愧kui", "溃kui", "坤kun", "昆kun", "捆kun", "困kun", "扩kuo",
            "括kuo", "阔kuo",
            // L
            "垃la", "拉la", "啦la", "蜡la", "腊la", "辣la", "来lai", "莱lai", "赖lai",
            "兰lan", "拦lan", "栏lan", "篮lan", "阑lan", "蓝lan", "览lan", "懒lan",
            "缆lan", "烂lan", "滥lan", "郎lang", "狼lang", "廊lang", "朗lang", "浪lang",
            "捞lao", "劳lao", "牢lao", "老lao", "佬lao", "姥lao", "涝lao", "烙lao",
            "落lao", "乐le", "勒le", "雷lei", "镭lei", "蕾lei", "磊lei", "累lei",
            "类lei", "泪lei", "冷leng", "愣leng", "厘li", "梨li", "犁li", "黎li",
            "篱li", "狸li", "离li", "理li", "李li", "里li", "鲤li", "礼li", "荔li",
            "吏li", "丽li", "利li", "励li", "例li", "俐li", "痢li", "立li", "粒li",
            "沥li", "力li", "历li", "厉li", "丽li", "连lian", "帘lian", "莲lian",
            "联lian", "廉lian", "怜lian", "涟lian", "脸lian", "敛lian", "链lian",
            "恋lian", "炼lian", "练lian", "粮liang", "凉liang", "梁liang", "粱liang",
            "良liang", "两liang", "亮liang", "谅liang", "辆liang", "量liang", "撩liao",
            "聊liao", "僚liao", "疗liao", "燎liao", "寥liao", "辽liao", "了liao",
            "料liao", "撂liao", "列lie", "裂lie", "烈lie", "猎lie", "劣lie", "林lin",
            "临lin", "邻lin", "鳞lin", "淋lin", "琳lin", "凛lin", "赁lin", "吝lin",
            "拎lin", "玲ling", "菱ling", "零ling", "龄ling", "铃ling", "伶ling",
            "灵ling", "陵ling", "凌ling", "领ling", "岭ling", "令ling", "溜liu",
            "刘liu", "流liu", "留liu", "榴liu", "硫liu", "琉liu", "瘤liu", "柳liu",
            "六liu", "龙long", "聋long", "咙long", "笼long", "隆long", "垄long",
            "拢long", "弄long", "楼lou", "搂lou", "篓lou", "漏lou", "陋lou", "芦lu",
            "卢lu", "颅lu", "炉lu", "鲁lu", "陆lu", "录lu", "鹿lu", "碌lu", "路lu",
            "驴lv", "旅lv", "履lv", "铝lv", "侣lv", "滤lv", "律lv", "率lv", "绿lv",
            "峦luan", "孪luan", "滦luan", "卵luan", "乱luan", "掠lue", "略lue", "轮lun",
            "伦lun", "沦lun", "纶lun", "论lun", "萝luo", "螺luo", "罗luo", "逻luo",
            "锣luo", "箩luo", "骡luo", "洛luo", "落luo", "骆luo", "络luo",
            // M
            "妈ma", "麻ma", "马ma", "码ma", "蚂ma", "骂ma", "吗ma", "埋mai", "买mai",
            "麦mai", "卖mai", "迈mai", "脉mai", "蛮man", "馒man", "瞒man", "满man",
            "曼man", "慢man", "漫man", "忙mang", "芒mang", "盲mang", "茫mang", "猫mao",
            "茅mao", "锚mao", "毛mao", "矛mao", "铆mao", "卯mao", "茂mao", "冒mao",
            "帽mao", "貌mao", "贸mao", "么me", "没mei", "眉mei", "媒mei", "煤mei",
            "玫mei", "梅mei", "酶mei", "霉mei", "每mei", "美mei", "昧mei", "媚mei",
            "妹mei", "门men", "闷men", "们men", "萌meng", "蒙meng", "盟meng", "猛meng",
            "梦meng", "孟meng", "眯mi", "迷mi", "谜mi", "弥mi", "米mi", "泌mi", "蜜mi",
            "密mi", "幂mi", "棉mian", "绵mian", "眠mian", "免mian", "勉mian", "娩mian",
            "面mian", "苗miao", "描miao", "秒miao", "渺miao", "妙miao", "庙miao", "灭mie",
            "民min", "抿min", "皿min", "敏min", "悯min", "闽min", "明ming", "鸣ming",
            "铭ming", "名ming", "命ming", "摸mo", "模mo", "膜mo", "磨mo", "摩mo", "魔mo",
            "抹mo", "末mo", "莫mo", "墨mo", "默mo", "沫mo", "漠mo", "寞mo", "陌mo",
            "谋mou", "某mou", "母mu", "亩mu", "牡mu", "姆mu", "拇mu", "木mu", "目mu",
            "牧mu", "穆mu", "慕mu", "暮mu", "幕mu", "募mu",
            // N
            "拿na", "哪na", "那na", "纳na", "娜na", "乃nai", "奶nai", "耐nai", "奈nai",
            "男nan", "南nan", "难nan", "囊nang", "挠nao", "脑nao", "恼nao", "闹nao",
            "呢ne", "馁nei", "内nei", "嫩nen", "能neng", "妮ni", "尼ni", "泥ni", "倪ni",
            "你ni", "逆ni", "匿ni", "腻ni", "年nian", "念nian", "娘niang", "鸟niao",
            "尿niao", "捏nie", "聂nie", "镍nie", "涅nie", "您nin", "柠ning", "狞ning",
            "凝ning", "宁ning", "拧ning", "牛niu", "扭niu", "钮niu", "纽niu", "农nong",
            "浓nong", "弄nong", "奴nu", "努nu", "怒nu", "女nv", "暖nuan", "虐nue",
            "挪nuo", "诺nuo", "懦nuo",
            // O
            "哦o", "欧ou", "鸥ou", "殴ou", "藕ou", "呕ou", "偶ou", "沤ou",
            // P
            "趴pa", "爬pa", "帕pa", "怕pa", "拍pai", "排pai", "牌pai", "徘pai", "湃pai",
            "派pai", "攀pan", "潘pan", "盘pan", "磐pan", "盼pan", "畔pan", "判pan", "叛pan",
            "乓pang", "庞pang", "旁pang", "彷pang", "胖pang", "抛pao", "咆pao", "刨pao",
            "炮pao", "袍pao", "跑pao", "泡pao", "呸pei", "胚pei", "培pei", "裴pei",
            "赔pei", "陪pei", "配pei", "佩pei", "沛pei", "喷pen", "盆pen", "抨peng",
            "烹peng", "澎peng", "彭peng", "蓬peng", "棚peng", "硼peng", "篷peng", "膨peng",
            "捧peng", "碰peng", "批pi", "披pi", "劈pi", "霹pi", "皮pi", "疲pi", "啤pi",
            "脾pi", "匹pi", "痞pi", "僻pi", "屁pi", "譬pi", "篇pian", "偏pian", "片pian",
            "骗pian", "飘piao", "漂piao", "瓢piao", "票piao", "瞥pie", "拼pin", "贫pin",
            "品pin", "聘pin", "乒ping", "坪ping", "苹ping", "萍ping", "平ping", "凭ping",
            "瓶ping", "评ping", "屏ping", "坡po", "泼po", "颇po", "婆po", "破po", "魄po",
            "剖pou", "扑pu", "铺pu", "仆pu", "莆pu", "菩pu", "蒲pu", "埔pu", "朴pu",
            "浦pu", "普pu", "谱pu", "曝pu",
            // Q
            "七qi", "妻qi", "栖qi", "戚qi", "期qi", "欺qi", "漆qi", "齐qi", "祈qi",
            "祁qi", "骑qi", "棋qi", "旗qi", "歧qi", "祈qi", "奇qi", "崎qi", "鳍qi",
            "乞qi", "企qi", "启qi", "起qi", "气qi", "迄qi", "弃qi", "汽qi", "泣qi",
            "契qi", "砌qi", "器qi", "掐qia", "恰qia", "洽qia", "千qian", "迁qian",
            "牵qian", "铅qian", "谦qian", "签qian", "前qian", "钱qian", "潜qian",
            "遣qian", "浅qian", "谴qian", "欠qian", "歉qian", "枪qiang", "腔qiang",
            "羌qiang", "强qiang", "墙qiang", "蔷qiang", "抢qiang", "橇qiao", "敲qiao",
            "悄qiao", "桥qiao", "瞧qiao", "侨qiao", "巧qiao", "翘qiao", "峭qiao",
            "窍qiao", "切qie", "茄qie", "且qie", "怯qie", "窃qie", "亲qin", "侵qin",
            "秦qin", "琴qin", "勤qin", "禽qin", "寝qin", "沁qin", "青qing", "轻qing",
            "氢qing", "倾qing", "卿qing", "清qing", "晴qing", "擎qing", "氰qing",
            "情qing", "顷qing", "请qing", "庆qing", "穷qiong", "琼qiong", "秋qiu",
            "丘qiu", "邱qiu", "囚qiu", "求qiu", "球qiu", "区qu", "曲qu", "驱qu", "躯qu",
            "屈qu", "趋qu", "渠qu", "取qu", "娶qu", "趣qu", "去qu", "圈quan", "全quan",
            "权quan", "泉quan", "拳quan", "痊quan", "诠quan", "犬quan", "券quan", "缺que",
            "瘸que", "却que", "确que", "雀que", "裙qun", "群qun",
            // R
            "然ran", "燃ran", "染ran", "嚷rang", "壤rang", "攘rang", "让rang", "饶rao",
            "扰rao", "绕rao", "惹re", "热re", "人ren", "仁ren", "忍ren", "刃ren", "认ren",
            "任ren", "妊ren", "纫ren", "扔reng", "仍reng", "日ri", "戎rong", "荣rong",
            "容rong", "融rong", "熔rong", "溶rong", "蓉rong", "榕rong", "柔rou", "肉rou",
            "如ru", "儒ru", "孺ru", "茹ru", "蠕ru", "汝ru", "乳ru", "辱ru", "入ru",
            "褥ru", "软ruan", "蕊rui", "瑞rui", "锐rui", "闰run", "润run", "若ruo",
            "弱ruo",
            // S
            "撒sa", "洒sa", "萨sa", "腮sai", "塞sai", "赛sai", "三san", "叁san", "伞san",
            "散san", "桑sang", "嗓sang", "丧sang", "搔sao", "骚sao", "扫sao", "嫂sao",
            "色se", "涩se", "瑟se", "森sen", "僧seng", "莎sha", "沙sha", "纱sha", "刹sha",
            "砂sha", "杀sha", "刹sha", "傻sha", "煞sha", "筛shai", "晒shai", "山shan",
            "删shan", "衫shan", "闪shan", "陕shan", "擅shan", "膳shan", "善shan", "汕shan",
            "扇shan", "缮shan", "墒shang", "伤shang", "商shang", "赏shang", "晌shang",
            "上shang", "尚shang", "裳shang", "梢shao", "稍shao", "烧shao", "芍shao",
            "勺shao", "少shao", "绍shao", "哨shao", "奢she", "赊she", "蛇she", "舌she",
            "舍she", "射she", "慑she", "摄she", "申shen", "伸shen", "身shen", "深shen",
            "娠shen", "绅shen", "神shen", "沈shen", "审shen", "婶shen", "甚shen", "肾shen",
            "慎shen", "渗shen", "声sheng", "生sheng", "牲sheng", "升sheng", "绳sheng",
            "省sheng", "圣sheng", "胜sheng", "盛sheng", "剩sheng", "尸shi", "失shi",
            "师shi", "狮shi", "施shi", "湿shi", "诗shi", "虱shi", "石shi", "拾shi",
            "时shi", "蚀shi", "实shi", "识shi", "史shi", "矢shi", "使shi", "屎shi",
            "驶shi", "始shi", "士shi", "氏shi", "世shi", "市shi", "示shi", "式shi",
            "事shi", "侍shi", "饰shi", "试shi", "视shi", "试shi", "室shi", "是shi",
            "适shi", "逝shi", "释shi", "嗜shi", "噬shi", "收shou", "手shou", "首shou",
            "守shou", "寿shou", "受shou", "售shou", "授shou", "瘦shou", "兽shou", "蔬shu",
            "枢shu", "叔shu", "殊shu", "抒shu", "舒shu", "输shu", "书shu", "赎shu",
            "熟shu", "暑shu", "署shu", "蜀shu", "黍shu", "鼠shu", "属shu", "术shu",
            "束shu", "述shu", "树shu", "竖shu", "恕shu", "刷shua", "耍shua", "摔shuai",
            "甩shuai", "帅shuai", "栓shuan", "拴shuan", "双shuang", "霜shuang", "爽shuang",
            "谁shui", "水shui", "睡shui", "税shui", "吮shun", "顺shun", "舜shun", "说shuo",
            "硕shuo", "朔shuo", "斯si", "撕si", "嘶si", "思si", "私si", "司si", "丝si",
            "死si", "肆si", "寺si", "似si", "饲si", "巳si", "松song", "耸song", "怂song",
            "颂song", "送song", "宋song", "讼song", "搜sou", "艘sou", "苏su", "酥su",
            "俗su", "素su", "速su", "粟su", "塑su", "宿su", "诉su", "肃su", "酸suan",
            "蒜suan", "算suan", "虽sui", "隋sui", "随sui", "绥sui", "髓sui", "碎sui",
            "岁sui", "穗sui", "遂sui", "隧sui", "孙sun", "损sun", "笋sun", "蓑suo",
            "梭suo", "唆suo", "缩suo", "琐suo", "索suo", "锁suo",
            // T
            "他ta", "她ta", "它ta", "塌ta", "塔ta", "獭ta", "挞ta", "踏ta", "胎tai",
            "台tai", "抬tai", "太tai", "态tai", "泰tai", "贪tan", "摊tan", "滩tan",
            "坛tan", "檀tan", "痰tan", "谭tan", "谈tan", "坦tan", "毯tan", "袒tan",
            "碳tan", "探tan", "叹tan", "炭tan", "汤tang", "塘tang", "搪tang", "堂tang",
            "棠tang", "膛tang", "唐tang", "糖tang", "倘tang", "躺tang", "淌tang", "烫tang",
            "趟tang", "掏tao", "涛tao", "滔tao", "绦tao", "桃tao", "逃tao", "陶tao",
            "淘tao", "讨tao", "套tao", "特te", "疼teng", "腾teng", "藤teng", "梯ti",
            "踢ti", "剔ti", "锑ti", "提ti", "题ti", "啼ti", "蹄ti", "体ti", "替ti",
            "嚏ti", "惕ti", "涕ti", "剃ti", "天tian", "添tian", "填tian", "田tian",
            "甜tian", "恬tian", "舔tian", "腆tian", "挑tiao", "条tiao", "迢tiao", "调tiao",
            "跳tiao", "帖tie", "贴tie", "铁tie", "厅ting", "听ting", "烃ting", "汀ting",
            "廷ting", "停ting", "亭ting", "庭ting", "挺ting", "艇ting", "通tong", "捅tong",
            "筒tong", "统tong", "痛tong", "偷tou", "头tou", "投tou", "透tou", "凸tu",
            "秃tu", "突tu", "图tu", "徒tu", "途tu", "涂tu", "屠tu", "土tu", "吐tu",
            "兔tu", "湍tuan", "团tuan", "推tui", "颓tui", "腿tui", "蜕tui", "退tui",
            "吞tun", "屯tun", "臀tun", "拖tuo", "托tuo", "脱tuo", "鸵tuo", "陀tuo",
            "驮tuo", "驼tuo", "椭tuo", "妥tuo", "拓tuo", "唾tuo",
            // W
            "挖wa", "哇wa", "蛙wa", "瓦wa", "袜wa", "歪wai", "外wai", "豌wan", "弯wan",
            "湾wan", "玩wan", "顽wan", "丸wan", "烷wan", "完wan", "碗wan", "挽wan",
            "晚wan", "皖wan", "惋wan", "婉wan", "万wan", "腕wan", "汪wang", "王wang",
            "亡wang", "枉wang", "网wang", "往wang", "旺wang", "望wang", "忘wang", "妄wang",
            "威wei", "巍wei", "微wei", "危wei", "韦wei", "违wei", "围wei", "唯wei",
            "维wei", "苇wei", "委wei", "伟wei", "伪wei", "尾wei", "纬wei", "未wei",
            "蔚wei", "味wei", "畏wei", "胃wei", "喂wei", "慰wei", "魏wei", "位wei",
            "渭wei", "谓wei", "温wen", "瘟wen", "文wen", "纹wen", "闻wen", "蚊wen",
            "吻wen", "稳wen", "紊wen", "问wen", "翁weng", "嗡weng", "瓮weng", "挝wo",
            "蜗wo", "窝wo", "我wo", "沃wo", "卧wo", "握wo", "污wu", "呜wu", "钨wu",
            "乌wu", "屋wu", "无wu", "芜wu", "梧wu", "吴wu", "毋wu", "五wu", "午wu",
            "伍wu", "武wu", "舞wu", "侮wu", "捂wu", "坞wu", "戊wu", "雾wu", "晤wu",
            "物wu", "勿wu", "务wu", "悟wu", "误wu",
            // X
            "夕xi", "汐xi", "西xi", "吸xi", "希xi", "昔xi", "析xi", "矽xi", "晰xi",
            "嘻xi", "膝xi", "嬉xi", "熙xi", "息xi", "熄xi", "烯xi", "溪xi", "汐xi",
            "悉xi", "惜xi", "犀xi", "檄xi", "袭xi", "席xi", "习xi", "喜xi", "铣xi",
            "洗xi", "系xi", "隙xi", "戏xi", "细xi", "瞎xia", "虾xia", "匣xia", "霞xia",
            "辖xia", "峡xia", "侠xia", "狭xia", "下xia", "夏xia", "吓xia", "厦xia",
            "先xian", "仙xian", "鲜xian", "纤xian", "咸xian", "贤xian", "衔xian",
            "闲xian", "嫌xian", "显xian", "险xian", "现xian", "献xian", "县xian",
            "腺xian", "馅xian", "羡xian", "宪xian", "线xian", "相xiang", "厢xiang",
            "镶xiang", "香xiang", "箱xiang", "襄xiang", "湘xiang", "乡xiang", "翔xiang",
            "祥xiang", "详xiang", "想xiang", "响xiang", "享xiang", "项xiang", "象xiang",
            "像xiang", "向xiang", "萧xiao", "硝xiao", "霄xiao", "削xiao", "哮xiao",
            "嚣xiao", "销xiao", "消xiao", "宵xiao", "淆xiao", "小xiao", "晓xiao",
            "孝xiao", "校xiao", "笑xiao", "效xiao", "楔xie", "些xie", "歇xie", "蝎xie",
            "鞋xie", "协xie", "挟xie", "携xie", "邪xie", "斜xie", "胁xie", "谐xie",
            "写xie", "血xie", "泻xie", "卸xie", "屑xie", "械xie", "谢xie", "榭xie",
            "懈xie", "蟹xie", "心xin", "欣xin", "辛xin", "新xin", "薪xin", "馨xin",
            "鑫xin", "信xin", "衅xin", "星xing", "腥xing", "猩xing", "惺xing", "兴xing",
            "刑xing", "型xing", "形xing", "邢xing", "行xing", "醒xing", "幸xing",
            "杏xing", "性xing", "姓xing", "凶xiong", "胸xiong", "匈xiong", "汹xiong",
            "雄xiong", "熊xiong", "休xiu", "修xiu", "羞xiu", "朽xiu", "秀xiu", "绣xiu",
            "袖xiu", "锈xiu", "嗅xiu", "墟xu", "戌xu", "需xu", "虚xu", "嘘xu", "须xu",
            "徐xu", "许xu", "蓄xu", "酗xu", "叙xu", "绪xu", "续xu", "絮xu", "婿xu",
            "絮xu", "轩xuan", "喧xuan", "宣xuan", "悬xuan", "旋xuan", "玄xuan", "选xuan",
            "癣xuan", "眩xuan", "绚xuan", "靴xue", "薛xue", "学xue", "穴xue", "雪xue",
            "血xue", "勋xun", "熏xun", "循xun", "旬xun", "询xun", "寻xun", "驯xun",
            "巡xun", "殉xun", "汛xun", "训xun", "讯xun", "逊xun", "迅xun",
            // Y
            "呀ya", "押ya", "鸦ya", "鸭ya", "丫ya", "芽ya", "牙ya", "蚜ya", "崖ya",
            "涯ya", "衙ya", "雅ya", "哑ya", "亚ya", "讶ya", "焉yan", "咽yan", "阉yan",
            "烟yan", "淹yan", "盐yan", "严yan", "言yan", "颜yan", "阎yan", "炎yan",
            "沿yan", "奄yan", "掩yan", "眼yan", "衍yan", "演yan", "艳yan", "堰yan",
            "燕yan", "厌yan", "砚yan", "雁yan", "唁yan", "谚yan", "验yan", "殃yang",
            "央yang", "鸯yang", "秧yang", "杨yang", "扬yang", "佯yang", "疡yang",
            "羊yang", "阳yang", "氧yang", "仰yang", "痒yang", "养yang", "样yang",
            "漾yang", "邀yao", "腰yao", "妖yao", "瑶yao", "摇yao", "尧yao", "遥yao",
            "窑yao", "谣yao", "姚yao", "咬yao", "舀yao", "药yao", "要yao", "耀yao",
            "椰ye", "噎ye", "耶ye", "爷ye", "野ye", "冶ye", "也ye", "页ye", "业ye",
            "叶ye", "曳ye", "夜ye", "液ye", "谒ye", "一yi", "壹yi", "医yi", "揖yi",
            "铱yi", "依yi", "伊yi", "衣yi", "颐yi", "夷yi", "遗yi", "移yi", "仪yi",
            "胰yi", "疑yi", "沂yi", "宜yi", "姨yi", "彝yi", "椅yi", "蚁yi", "倚yi",
            "已yi", "以yi", "矣yi", "艺yi", "抑yi", "易yi", "邑yi", "屹yi", "亿yi",
            "役yi", "臆yi", "逸yi", "肄yi", "疫yi", "亦yi", "裔yi", "意yi", "毅yi",
            "忆yi", "义yi", "益yi", "溢yi", "诣yi", "议yi", "谊yi", "译yi", "异yi",
            "翼yi", "茵yin", "因yin", "殷yin", "音yin", "阴yin", "姻yin", "银yin",
            "淫yin", "寅yin", "饮yin", "尹yin", "引yin", "隐yin", "印yin", "英ying",
            "樱ying", "婴ying", "鹰ying", "应ying", "缨ying", "莹ying", "营ying",
            "荧ying", "蝇ying", "迎ying", "赢ying", "盈ying", "影ying", "颖ying",
            "硬ying", "映ying", "哟yo", "拥yong", "庸yong", "臃yong", "痈yong", "邕yong",
            "雍yong", "踊yong", "蛹yong", "咏yong", "泳yong", "涌yong", "永yong",
            "恿yong", "勇yong", "用yong", "幽you", "优you", "悠you", "忧you", "尤you",
            "由you", "邮you", "犹you", "油you", "游you", "酉you", "有you", "友you",
            "右you", "佑you", "釉you", "诱you", "又you", "幼you", "迂yu", "淤yu",
            "于yu", "予yu", "余yu", "鱼yu", "愉yu", "渔yu", "隅yu", "愚yu", "榆yu",
            "虞yu", "愚yu", "舆yu", "宇yu", "羽yu", "雨yu", "语yu", "玉yu", "域yu",
            "芋yu", "育yu", "吁yu", "遇yu", "峪yu", "御yu", "狱yu", "浴yu", "欲yu",
            "喻yu", "寓yu", "裕yu", "豫yu", "预yu", "誉yu", "渊yuan", "冤yuan",
            "元yuan", "垣yuan", "袁yuan", "原yuan", "援yuan", "缘yuan", "源yuan",
            "猿yuan", "远yuan", "苑yuan", "愿yuan", "怨yuan", "院yuan", "曰yue", "约yue",
            "月yue", "悦yue", "阅yue", "跃yue", "越yue", "粤yue", "云yun", "匀yun",
            "陨yun", "允yun", "运yun", "蕴yun", "酝yun", "晕yun", "韵yun",
            // Z
            "匝za", "砸za", "杂za", "栽zai", "哉zai", "灾zai", "宰zai", "载zai",
            "再zai", "在zai", "攒zan", "暂zan", "赞zan", "赃zang", "脏zang", "葬zang",
            "遭zao", "糟zao", "凿zao", "藻zao", "枣zao", "早zao", "澡zao", "蚤zao",
            "躁zao", "噪zao", "造zao", "皂zao", "灶zao", "燥zao", "责ze", "择ze",
            "则ze", "泽ze", "贼zei", "怎zen", "增zeng", "憎zeng", "赠zeng", "扎zha",
            "喳zha", "渣zha", "札zha", "轧zha", "闸zha", "眨zha", "榨zha", "咋zha",
            "炸zha", "诈zha", "斋zhai", "摘zhai", "宅zhai", "窄zhai", "债zhai", "寨zhai",
            "瞻zhan", "毡zhan", "粘zhan", "沾zhan", "盏zhan", "斩zhan", "辗zhan",
            "崭zhan", "展zhan", "占zhan", "战zhan", "站zhan", "湛zhan", "绽zhan",
            "樟zhang", "章zhang", "彰zhang", "漳zhang", "张zhang", "掌zhang", "涨zhang",
            "杖zhang", "丈zhang", "帐zhang", "账zhang", "仗zhang", "胀zhang", "障zhang",
            "招zhao", "昭zhao", "找zhao", "沼zhao", "赵zhao", "照zhao", "罩zhao",
            "兆zhao", "肇zhao", "遮zhe", "折zhe", "哲zhe", "蛰zhe", "辙zhe", "者zhe",
            "锗zhe", "赭zhe", "这zhe", "浙zhe", "蔗zhe", "贞zhen", "针zhen", "侦zhen",
            "枕zhen", "疹zhen", "诊zhen", "震zhen", "振zhen", "镇zhen", "阵zhen",
            "蒸zheng", "挣zheng", "睁zheng", "征zheng", "狰zheng", "争zheng", "怔zheng",
            "整zheng", "正zheng", "证zheng", "郑zheng", "政zheng", "枝zhi", "支zhi",
            "脂zhi", "汁zhi", "芝zhi", "蜘zhi", "知zhi", "织zhi", "职zhi", "直zhi",
            "植zhi", "殖zhi", "执zhi", "值zhi", "侄zhi", "址zhi", "指zhi", "止zhi",
            "只zhi", "旨zhi", "趾zhi", "纸zhi", "志zhi", "挚zhi", "掷zhi", "至zhi",
            "致zhi", "置zhi", "帜zhi", "峙zhi", "制zhi", "智zhi", "秩zhi", "稚zhi",
            "质zhi", "炙zhi", "中zhong", "忠zhong", "钟zhong", "衷zhong", "终zhong",
            "种zhong", "重zhong", "仲zhong", "众zhong", "舟zhou", "周zhou", "州zhou",
            "洲zhou", "粥zhou", "轴zhou", "肘zhou", "帚zhou", "咒zhou", "皱zhou",
            "宙zhou", "昼zhou", "骤zhou", "珠zhu", "株zhu", "蛛zhu", "朱zhu", "猪zhu",
            "诸zhu", "诛zhu", "逐zhu", "竹zhu", "烛zhu", "煮zhu", "嘱zhu", "主zhu",
            "著zhu", "柱zhu", "助zhu", "蛀zhu", "贮zhu", "铸zhu", "筑zhu", "住zhu",
            "注zhu", "祝zhu", "驻zhu", "抓zhua", "爪zhua", "拽zhuai", "专zhuan",
            "砖zhuan", "转zhuan", "赚zhuan", "篆zhuan", "撰zhuan", "妆zhuang", "庄zhuang",
            "装zhuang", "桩zhuang", "壮zhuang", "状zhuang", "撞zhuang", "幢zhuang",
            "追zhui", "锥zhui", "坠zhui", "缀zhui", "谆zhun", "准zhun", "拙zhuo",
            "捉zhuo", "卓zhuo", "桌zhuo", "茁zhuo", "酌zhuo", "啄zhuo", "着zhuo",
            "灼zhuo", "浊zhuo", "兹zi", "咨zi", "资zi", "姿zi", "滋zi", "淄zi",
            "孜zi", "紫zi", "仔zi", "籽zi", "滓zi", "子zi", "自zi", "渍zi", "字zi",
            "宗zong", "综zong", "踪zong", "棕zong", "鬃zong", "总zong", "纵zong",
            "走zou", "奏zou", "揍zou", "租zu", "足zu", "卒zu", "族zu", "诅zu",
            "阻zu", "组zu", "钻zuan", "纂zuan", "嘴zui", "最zui", "罪zui", "醉zui",
            "尊zun", "遵zun", "昨zuo", "左zuo", "佐zuo", "做zuo", "作zuo", "坐zuo",
            "座zuo"
        )

        for (item in pinyinData) {
            if (item.length >= 2) {
                val char = item[0]
                val pinyin = item.substring(1)
                put(char, pinyin)
            }
        }
    }
}
