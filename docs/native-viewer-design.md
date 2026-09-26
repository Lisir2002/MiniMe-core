# MiniMe-core Native 查看器/编辑器核心（libminimeviewer）完整实施方案

> 版本：v1.0
> 日期：2026-09-26
> 状态：待实施
> 适用项目：MiniMe-core（applicationId: com.mini.me_core）

---

## 一、项目背景与目标

### 1.1 背景

当前代码查看器方案为 WebView + highlight.js，存在以下问题：
- 大文件（>1MB）加载慢、内存高，WebView 初始化重
- highlight.js 基于正则匹配，语法高亮不准确，无法做代码折叠/符号分析
- 无法支撑高性能代码编辑（撤销重做、大文件编辑卡顿）
- 图片、PDF、Office 文档缺乏统一的应用内查看能力

### 1.2 目标

构建一个 C/C++ Native 共享库 `libminimeviewer.so`，统一承载：
1. 代码只读查看（高性能语法高亮、行号、折叠、搜索）
2. 代码编辑（piece table、撤销重做、自动缩进、括号补全）
3. 大文件流式加载与编码自动检测
4. Office 文档（docx/xlsx/pptx）轻量内容预览
5. 图片查看（native 解码备用通道）
6. PDF 查看（后续阶段，MuPDF）

### 1.3 设计原则

- **不重复造轮子**：语法解析用 tree-sitter，zip/XML/图片用成熟轻量 C/C++ 库
- **渲染与逻辑分离**：C++ 只负责数据计算，UI 渲染全部在 Compose 层
- **流式优先**：所有文件处理支持分块，避免 OOM
- **轻量**：native 总体积控制在 8MB 以内（arm64-v8a）
- **跨 ABI**：arm64-v8a、x86_64（与现有 abiFilters 一致）
- **命名规范**：所有新增代码统一 MiniMe 命名

---

## 二、整体架构

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────┐
│              Compose UI 层（Kotlin）                  │
│  CodeViewerScreen / CodeEditorScreen / ImageViewer   │
│  OfficePreviewScreen / PdfViewerScreen               │
│  （渲染、手势、顶栏、搜索、弹窗、交互）                  │
├─────────────────────────────────────────────────────┤
│              ViewModel / State 层（Kotlin）           │
│  CodeViewerViewModel / CodeEditorViewModel           │
│  OfficePreviewViewModel / ImageViewerViewModel       │
├─────────────────────────────────────────────────────┤
│              JNI 桥接层（Kotlin external + C JNI）    │
│  NativeViewerBridge.kt ↔ jni_bridge.cpp              │
├─────────────────────────────────────────────────────┤
│              Native 核心层（C/C++，libminimeviewer.so）│
│  ┌──────────┬──────────┬──────────┬────────────────┐ │
│  │tree-sitter│text_buffer│file_loader│office_ooxml   │ │
│  │语法解析   │编辑核心    │文件加载   │docx/xlsx/pptx │ │
│  ├──────────┴──────────┴──────────┴────────────────┤ │
│  │ image_loader (stb_image)  │  pdf (MuPDF，三期)    │ │
│  └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 2.2 数据流

**只读查看流程**：
```
文件路径 → file_loader（流式读取+编码检测）
         → tree_sitter_module（AST 解析+高亮 span 计算）
         → JNI 返回（行文本 + span 数组：起始/结束/颜色类别）
         → Compose Text/AnnotatedString 渲染
```

**编辑流程**：
```
文件 → file_loader → text_buffer（piece table 初始化）
用户输入 → JNI → text_buffer.insert/delete
         → tree-sitter 增量重解析（仅变更子树）
         → 返回变更行的 span
         → Compose 局部刷新
撤销/重做 → text_buffer.undo/redo → 同上
```

**Office 预览流程**：
```
docx/xlsx/pptx → office_ooxml（minizip 解压 + pugixml 解析 XML）
              → 提取结构化内容（段落/表格/sheet/slide）
              → JNI 返回结构化 JSON 或数据结构
              → Compose 原生组件重排预览
```

### 2.3 架构强化决策（性能 / 体验 / 功能 / 扩展性）

> 用户硬性要求：功能强大、扩展性高、体验流畅、性能无敌。以下为架构级强化决策，第一期地基必须落实，后续阶段逐步兑现。

#### 2.3.1 性能无敌

| 强化点 | 技术方案 | 落地阶段 |
|--------|---------|---------|
| **JNI 零拷贝** | 行文本通过 `ByteBuffer.allocateDirect` 传递，native 直接写、Kotlin 直接读，禁止 `NewStringUTF` 大字符串复制 | 第一期 |
| **mmap 大文件** | >50MB 文件用 `mmap` 内存映射，不占堆内存，零拷贝随机访问；<50MB 仍用堆缓冲 | 第一期 |
| **行索引紧凑数组 + 二分** | 行偏移量用 `uint32_t` 紧凑数组存储，O(log n) 定位；热点行 LRU 缓存 | 第一期 |
| **tree-sitter 增量解析** | 编辑后仅重解析变更子树（tree-sitter 原生 `ts_parser_parse` 带 old_tree），高亮 span 只重算变更行 | 第一期（接口预留）/ 第二期兑现 |
| **脏行渲染管线** | native 维护 `dirty_line_set`，Compose 只重绘脏行，不整屏重绘；行内容缓存 key=行号+版本号 | 第二期（自研布局时） |
| **后台线程解析** | tree-sitter 解析在 native 线程池执行，结果通过 JNI 回调异步推送，主线程永不阻塞 | 第一期 |
| **对象池 / 内存复用** | span 数组、行缓冲区用预分配对象池，避免频繁分配；native 侧无 GC | 第一期 |
| **SIMD 编码检测** | 编码检测用逐字节快速路径 + SIMD 可选优化（ARM NEON），大文件检测 < 50ms | 第一期（快速路径）/ 三期（NEON） |

**性能基线（硬性指标）**：
- 1MB 代码文件打开 < 200ms
- 50MB 文件滚动稳定 60fps，无掉帧
- 编辑器输入延迟 < 8ms（从按键到屏幕刷新）
- 10MB xlsx 预览解析 < 2s
- 50MB 文件 native 内存 < 80MB（mmap 不计入堆）

#### 2.3.2 体验流畅

| 强化点 | 技术方案 | 落地阶段 |
|--------|---------|---------|
| **自研文本布局** | 第二期彻底移除 Compose TextField 双端同步，native text_buffer 为唯一数据源，自研 Canvas 文本布局（行测量、折行、光标、选择） | 第二期 |
| **视口 + 预渲染** | 可见行 ± 50 行预加载预解析，滚动方向预判预取，无白屏 | 第一期 |
| **搜索流式返回** | 大文件搜索异步执行，匹配结果逐条推送，进度条实时更新，可取消 | 第一期 |
| **折叠/展开动画** | 行高变化用 `animateFloatAsState` 插值，平滑过渡不突兀 | 第一期 |
| **光标/选择流畅** | 自研布局后光标绘制、文本选择、拖拽手柄全部 native 计算，60fps | 第二期 |
| **主题切换零延迟** | 高亮颜色全部从 colorScheme 派生，主题切换即时生效无重解析 | 第一期 |

#### 2.3.3 功能强大

| 强化点 | 说明 | 落地阶段 |
|--------|------|---------|
| **多光标 + 列选择** | text_buffer 支持多选区（selections 数组），同时编辑、同时撤销 | 第二期 |
| **代码诊断预留** | 预留 LSP 诊断接口（错误/警告下划线、灯泡提示、快速修复），native 存储诊断数据，UI 渲染 | 第二期（接口）/ 三期（LSP 接入） |
| **代码导航** | tree-sitter 符号表 + 跳转定义/查找引用，大纲面板点击跳转 | 第一期（大纲）/ 二期（跳转） |
| **差异对比（diff）** | 内置 Myers diff 算法（native 实现），支持文件对比、未保存变更高亮 | 第三期 |
| **十六进制查看** | 二进制文件自动检测并切换 hex 视图（地址 + hex + ASCII 三栏），可编辑 | 第三期 |
| **命令面板** | 所有操作抽象为 Command（打开/保存/搜索/跳转/折叠/编码切换…），支持快捷键 + 模糊搜索唤起 | 第二期 |
| **宏录制/回放** | 编辑操作序列可录制为宏，回放执行（基于 Command 系统） | 第三期 |
| **编码切换** | 打开后可手动切换编码重新解码（GBK/UTF-8/UTF-16），解决误判 | 第一期 |

#### 2.3.4 扩展性高

| 强化点 | 技术方案 | 落地阶段 |
|--------|---------|---------|
| **文件类型注册表** | `FileTypeRegistry`：扩展名/MIME → 查看器类型（code/office/image/pdf/hex），新增类型只需注册一行 | 第一期 |
| **grammar 动态加载** | tree-sitter grammar 编译为独立静态库链接，语言检测后按需初始化 parser；预留运行时加载 .so 接口 | 第一期（静态链接）/ 三期（动态） |
| **主题系统可配置** | `CodeTheme` 数据类：语义类别 → 颜色/字体样式，支持从 JSON 导入主题，内置多套 | 第一期 |
| **事件钩子** | `beforeSave` / `afterEdit` / `onOpen` / `onClose` 钩子注册表，预留扩展点 | 第二期（接口） |
| **LSP 适配层** | native 预留 LSP 消息通道（JSON-RPC over stdio），后续可接入语言服务器 | 第三期 |
| **查看器/编辑器分离** | CodeViewer（只读）与 CodeEditor（可编辑）共享 native 核心，UI 层分离，只读场景不加载编辑逻辑 | 第一期 |

#### 2.3.5 零拷贝 JNI 接口规范（第一期必须落实）

```
// 行文本传递：native 写入 DirectByteBuffer，Kotlin 读取后释放
// 避免 NewStringUTF 在大文件时的 O(n) 拷贝和 GC 压力
JNIEXPORT jobject JNICALL nativeReadLinesDirect(
    JNIEnv*, jclass, jlong handle,
    jlong startLine, jlong endLine,
    jobject outBuffer  // DirectByteBuffer，native 填充
);
// 返回值：实际填充的行数；Kotlin 侧从 buffer 按行解析
```

---

## 三、模块详细设计

### 3.1 file_loader（文件加载模块）

**语言**：C++17
**源文件**：`file_loader.hpp / file_loader.cpp`
**预估代码量**：约 400 行

**职责**：
1. 流式读取文件（分块，默认 1MB/块）
2. 编码自动检测：
   - UTF-8（含 BOM）
   - UTF-16 LE/BE（含 BOM）
   - GBK/GB2312（基于统计启发式）
   - Latin-1（兜底）
3. 换行符检测与统一（LF / CRLF / CR）
4. 大文件行索引（构建行偏移量表，支持随机跳转）
5. 文件元信息（大小、修改时间、是否只读）

**关键数据结构**：
```cpp
struct FileInfo {
    std::string path;
    int64_t size;
    Encoding encoding;
    LineEnding lineEnding;
    bool readOnly;
    int64_t lineCount;
};

class FileLoader {
    // 打开文件，构建行索引（大文件仅索引，不全量加载）
    bool open(const std::string& path);
    // 按行范围读取（用于视口外懒加载）
    std::vector<std::string> readLines(int64_t start, int64_t end);
    // 全文读取（小文件）
    std::string readAll();
    FileInfo info() const;
};
```

**大文件策略**：
- < 5MB：全量加载到内存
- 5MB-100MB：行索引 + 视口内容加载（LRU 缓存，默认缓存 5000 行）
- > 100MB：提示"大文件模式"，只读，禁用全量搜索（改为分块搜索）

### 3.2 tree_sitter_module（语法解析模块）

**语言**：C（tree-sitter 核心）+ C++17（封装）
**依赖**：tree-sitter（v0.24+，MIT 协议）+ 各语言 grammar
**源文件**：`ts_parser.hpp / ts_parser.cpp`
**预估代码量**：封装约 500 行（grammar 为预编译第三方代码）

**支持语言（第一期 12 个核心语言）**：
| 语言 | grammar 库 |
|------|-----------|
| Kotlin | tree-sitter-kotlin |
| Java | tree-sitter-java |
| Python | tree-sitter-python |
| JavaScript/JSX | tree-sitter-javascript / typescript（含 tsx） |
| TypeScript | tree-sitter-typescript |
| C/C++ | tree-sitter-c / tree-sitter-cpp |
| Go | tree-sitter-go |
| Rust | tree-sitter-rust |
| JSON | tree-sitter-json |
| XML/HTML | tree-sitter-xml / tree-sitter-html |
| YAML | tree-sitter-yaml |
| Bash/Shell | tree-sitter-bash |
| Markdown | tree-sitter-markdown |
| SQL | tree-sitter-sql |

（第二期按需扩展：Ruby、PHP、Swift、Dart、Dockerfile、Git 等）

**职责**：
1. 全量解析（打开文件时）
2. 增量解析（编辑后仅重解析变更范围，tree-sitter 原生支持）
3. 高亮 span 计算：遍历 AST，将节点类型映射为颜色类别
4. 代码折叠：识别函数/类/块结构，提供折叠点
5. 符号大纲（可选）：提取函数/类定义，用于大纲导航
6. 括号匹配：查询语法节点中的成对括号

**高亮颜色类别（与主题解耦）**：
native 层只输出语义类别 ID，颜色由 Kotlin 层根据主题映射：
```
KEYWORD, TYPE, FUNCTION, STRING, NUMBER, COMMENT,
OPERATOR, PROPERTY, VARIABLE, CONSTANT, TAG, ATTRIBUTE,
PARAMETER, ANNOTATION, NAMESPACE, PUNCTUATION, TEXT, NONE
```

**关键接口**：
```cpp
struct HighlightSpan {
    uint32_t startByte;
    uint32_t endByte;
    uint8_t category;  // HighlightCategory enum
};

struct FoldRegion {
    uint32_t startLine;
    uint32_t endLine;
    uint8_t kind;  // function/class/block
};

class TsParser {
    bool setLanguage(const std::string& scope);
    // 全量解析，返回高亮 spans 和折叠区域
    ParseResult parse(const std::string& source);
    // 增量解析（编辑后）
    ParseResult reparse(const std::string& source,
                        uint32_t editStart, uint32_t editOldEnd,
                        uint32_t editNewEnd);
    std::vector<FoldRegion> folds() const;
    SymbolOutline outline() const;
};
```

### 3.3 text_buffer（文本编辑核心）

**语言**：C++17
**源文件**：`text_buffer.hpp / text_buffer.cpp`
**预估代码量**：约 1200 行
**数据结构**：Piece Table（带撤销栈）

**为什么用 Piece Table**：
- O(1) 插入/删除（大部分情况）
- 天然支持撤销（保留所有 buffer）
- VS Code、Lapce 等编辑器采用类似结构
- 比 rope 实现简单，性能足够

**职责**：
1. 文本插入、删除、替换
2. 多行编辑（多光标，第二期）
3. 撤销/重做（无步数上限，受内存限制，默认保留 1000 步）
4. 行/列坐标与字节偏移互转
5. 自动缩进（继承上一行缩进）
6. 括号/引号自动补全
7. 换行处理（保持文件原有换行符）
8. 脏状态跟踪（是否已修改）
9. 保存（写回文件，原子写入：先写 .tmp 再 rename）

**关键接口**：
```cpp
struct EditOp {
    enum Type { INSERT, DELETE } type;
    uint32_t offset;
    std::string text;      // INSERT: 插入内容；DELETE: 被删内容
    uint32_t length;
};

class TextBuffer {
    bool load(const std::string& content, LineEnding le);
    // 编辑
    void insert(uint32_t offset, const std::string& text);
    void erase(uint32_t start, uint32_t end);
    void replace(uint32_t start, uint32_t end, const std::string& text);
    // 撤销重做
    bool undo();
    bool redo();
    bool canUndo() const;
    bool canRedo() const;
    // 查询
    std::string getText() const;
    std::string getLine(uint32_t line) const;
    uint32_t lineCount() const;
    uint32_t offsetToLine(uint32_t offset) const;
    uint32_t lineToOffset(uint32_t line) const;
    // 保存
    bool save(const std::string& path);
    bool isDirty() const;
    // 自动缩进/补全辅助
    uint32_t getAutoIndent(uint32_t line) const;
    std::string getMatchingPair(char c) const;
};
```

**撤销栈设计**：
- 编辑操作合并：连续输入（停顿 < 500ms）合并为一个 undo 步骤
- 光标移动不入栈
- 保存后不清空撤销栈（但标记 clean checkpoint）

### 3.4 office_ooxml（Office 轻量预览模块）

**语言**：C++17
**依赖**：
- minizip-ng（zlib 许可，zip 解压）
- pugixml（MIT，轻量 XML 解析）
**源文件**：
- `office/docx_extractor.hpp/cpp`
- `office/xlsx_extractor.hpp/cpp`
- `office/pptx_extractor.hpp/cpp`
- `office/office_types.hpp`
**预估代码量**：约 1800 行

**定位**：内容预览，不做像素级排版；旧格式（.doc/.xls/.ppt）不支持，提示外部打开。

#### 3.4.1 通用数据模型

```cpp
// 富文本片段
struct TextRun {
    std::string text;
    bool bold = false;
    bool italic = false;
    bool underline = false;
    bool strike = false;
    uint8_t headingLevel = 0;  // 0=正文, 1-6=标题
    std::string color;         // hex，可空
    std::string hyperlink;     // 可空
};

// 段落
struct Paragraph {
    std::vector<TextRun> runs;
    enum Alignment { LEFT, CENTER, RIGHT, JUSTIFY } alignment = LEFT;
    uint8_t listLevel = 0;       // 0=非列表
    enum ListType { NONE, BULLET, NUMBER } listType = NONE;
    int32_t indent = 0;
};

// 图片（从 zip 提取的二进制）
struct EmbeddedImage {
    std::string mediaPath;   // zip 内路径
    std::vector<uint8_t> data;
    std::string mimeType;
    int32_t widthPx = 0;
    int32_t heightPx = 0;
};
```

#### 3.4.2 DOCX 提取器

解析 `word/document.xml`，提取：
- 段落（标题层级、对齐、列表、缩进）
- 文本样式（粗体、斜体、下划线、删除线、颜色、超链接）
- 表格（转换为行列数据模型）
- 图片（从 `word/media/` 提取）
- 分页符（标记分页位置）

输出：
```cpp
struct DocxContent {
    std::vector<Block> blocks;  // Paragraph / Table / Image / PageBreak
    CoreProperties props;       // 标题/作者/创建时间
};
```

#### 3.4.3 XLSX 提取器

解析：
- `xl/workbook.xml`（sheet 列表）
- `xl/worksheets/sheetN.xml`（单元格数据）
- `xl/sharedStrings.xml`（共享字符串）
- 单元格类型（字符串/数字/布尔/公式结果）
- 合并单元格信息
- 列宽/行高（用于简单布局）

输出：
```cpp
struct Cell {
    uint32_t row, col;
    std::string value;
    enum Type { STRING, NUMBER, BOOLEAN, FORMULA, EMPTY } type;
    bool bold;
    std::string color;
};

struct Sheet {
    std::string name;
    std::vector<Cell> cells;
    uint32_t maxRow, maxCol;
    std::vector<std::pair<uint32_t,uint32_t>> mergedRanges;
};

struct XlsxContent {
    std::vector<Sheet> sheets;
    CoreProperties props;
};
```

Kotlin 层用 LazyColumn + 行列网格渲染，支持 sheet 切换。

#### 3.4.4 PPTX 提取器

解析：
- `ppt/slides/slideN.xml`（每页内容）
- 文本框（段落 + 样式）
- 图片（`ppt/media/`）
- 备注（可选）
- 幻灯片顺序

输出：
```cpp
struct Slide {
    std::vector<Paragraph> paragraphs;
    std::vector<EmbeddedImage> images;
    std::string notes;
};

struct PptxContent {
    std::vector<Slide> slides;
    CoreProperties props;
};
```

Kotlin 层用 HorizontalPager 左右翻页浏览。

#### 3.4.5 兜底策略

- 旧格式（.doc/.xls/.ppt）：native 返回 `UNSUPPORTED_FORMAT`，UI 显示"旧格式暂不支持预览"，提供"用其他应用打开"（FileProvider + Intent ACTION_VIEW）
- 加密/损坏文档：返回解析错误，提示外部打开
- 超大 Office（>50MB）：警告后仍尝试解析，超时（10秒）则中止

### 3.5 image_loader（图片模块）

**依赖**：stb_image（C 单头文件，public domain）
**源文件**：`image_loader.hpp/cpp`
**预估代码量**：约 200 行

**定位**：
- 常规网络图片仍用 Coil（缓存、动图支持更好）
- native 模块用于：容器内特殊格式（PNG/JPEG/WebP/BMP/TGA/HDR/PSD 基础层）、超大图片分块解码、获取图片元信息（尺寸/位深/EXIF 方向）

**图片查看器 UI（Kotlin 实现）**：
- 双指缩放（0.5x-8x）、双击放大/还原
- 单指平移、拖动退出（缩放=1 时）
- 保存到相册、分享
- 图片信息面板（尺寸、格式、大小、路径）
- 左右滑动切换同目录图片（容器浏览场景）

### 3.6 pdf_module（PDF 模块，第三期）

**依赖**：MuPDF（AGPL，需评估协议；或改用 PDFium 的 BSD-3 条款）
**说明**：
- PDFium（Chromium PDF 引擎，BSD-3-Clause，协议友好）
- 功能：页面渲染为 bitmap、文本提取、目录、页面缩略图、缩放
- 体积约 8-15MB per ABI
- 第三期实施，第二期先保留接口

**协议决策**：本项目 GPL-3.0，与 PDFium（BSD-3）兼容；MuPDF AGPL 与 GPL-3 兼容但限制更强。**推荐 PDFium**。

---

## 四、JNI 桥接层设计

### 4.1 C++ 侧（jni_bridge.cpp）

**原则**：
- JNI 只做参数转换和结果封送，不写业务逻辑
- 大结果通过回调/分块返回，避免单次 JNI 超大数组
- 全局句柄缓存（JavaVM、ClassLoader）
- 异常通过返回码 + 错误消息接口获取，不跨 JNI 抛 C++ 异常

**统一错误码**：
```
SUCCESS = 0
ERR_FILE_NOT_FOUND
ERR_PERMISSION_DENIED
ERR_UNSUPPORTED_ENCODING
ERR_PARSE_FAILED
ERR_UNSUPPORTED_FORMAT
ERR_OUT_OF_MEMORY
ERR_INVALID_HANDLE
ERR_SAVE_FAILED
```

**句柄管理**：
- native 对象通过 `jlong handle`（指针）传递给 Kotlin
- 提供 `nativeClose(handle)` 显式释放
- Kotlin 侧用 Closeable/use 保证释放

### 4.2 JNI 函数清单

```cpp
// ── 通用 ──
JNIEXPORT jstring JNICALL nativeGetVersion(JNIEnv*, jclass);
JNIEXPORT jstring JNICALL nativeGetLastError(JNIEnv*, jclass);

// ── 文件加载 ──
JNIEXPORT jlong JNICALL nativeOpenFile(JNIEnv*, jclass, jstring path);
JNIEXPORT jobject JNICALL nativeGetFileInfo(JNIEnv*, jclass, jlong handle);
JNIEXPORT jlong JNICALL nativeGetLineCount(JNIEnv*, jclass, jlong handle);
JNIEXPORT jobjectArray JNICALL nativeReadLines(JNIEnv*, jclass, jlong handle,
                                               jlong start, jlong end);
JNIEXPORT void JNICALL nativeCloseFile(JNIEnv*, jclass, jlong handle);

// ── 代码查看（只读，轻量句柄）──
JNIEXPORT jlong JNICALL nativeOpenCodeViewer(JNIEnv*, jclass,
                                             jstring path, jstring languageHint);
JNIEXPORT jlongArray JNICALL nativeGetHighlightSpans(JNIEnv*, jclass,
                                                     jlong handle, jlong lineStart,
                                                     jlong lineEnd);
JNIEXPORT jobjectArray JNICALL nativeGetFoldRegions(JNIEnv*, jclass, jlong handle);
JNIEXPORT jobjectArray JNICALL nativeGetOutline(JNIEnv*, jclass, jlong handle);

// ── 代码编辑 ──
JNIEXPORT jlong JNICALL nativeOpenCodeEditor(JNIEnv*, jclass, jstring path);
JNIEXPORT jboolean JNICALL nativeEditorInsert(JNIEnv*, jclass, jlong handle,
                                              jlong offset, jstring text);
JNIEXPORT jboolean JNICALL nativeEditorErase(JNIEnv*, jclass, jlong handle,
                                             jlong start, jlong end);
JNIEXPORT jboolean JNICALL nativeEditorUndo(JNIEnv*, jclass, jlong handle);
JNIEXPORT jboolean JNICALL nativeEditorRedo(JNIEnv*, jclass, jlong handle);
JNIEXPORT jboolean JNICALL nativeEditorSave(JNIEnv*, jclass, jlong handle,
                                            jstring path);
JNIEXPORT jboolean JNICALL nativeEditorIsDirty(JNIEnv*, jclass, jlong handle);
JNIEXPORT jlong JNICALL nativeEditorLineCount(JNIEnv*, jclass, jlong handle);
JNIEXPORT jstring JNICALL nativeEditorGetText(JNIEnv*, jclass, jlong handle);
JNIEXPORT jlong JNICALL nativeEditorLineToOffset(JNIEnv*, jclass, jlong handle,
                                                 jlong line);

// ── Office 预览 ──
JNIEXPORT jint JNICALL nativeDetectOfficeType(JNIEnv*, jclass, jstring path);
JNIEXPORT jlong JNICALL nativeParseDocx(JNIEnv*, jclass, jstring path);
JNIEXPORT jlong JNICALL nativeParseXlsx(JNIEnv*, jclass, jstring path);
JNIEXPORT jlong JNICALL nativeParsePptx(JNIEnv*, jclass, jstring path);
// 结构化结果序列化为 JSON 字符串返回（Kotlin 用 kotlinx.serialization 解析）
JNIEXPORT jstring JNICALL nativeOfficeToJson(JNIEnv*, jclass, jlong handle);
JNIEXPORT jbyteArray JNICALL nativeGetMediaData(JNIEnv*, jclass, jlong handle,
                                                jstring mediaPath);
JNIEXPORT void JNICALL nativeCloseOffice(JNIEnv*, jclass, jlong handle);

// ── 图片 ──
JNIEXPORT jintArray JNICALL nativeGetImageInfo(JNIEnv*, jclass, jstring path);
JNIEXPORT jbyteArray JNICALL nativeDecodeImage(JNIEnv*, jclass, jstring path,
                                               jint targetWidth);
```

### 4.3 Kotlin 侧（NativeViewerBridge.kt）

```kotlin
internal object NativeViewerBridge {
    init { System.loadLibrary("minimeviewer") }

    external fun nativeGetVersion(): String
    external fun nativeOpenFile(path: String): Long
    external fun nativeGetFileInfo(handle: Long): FileInfoDto?
    external fun nativeReadLines(handle: Long, start: Long, end: Long): Array<String>?
    external fun nativeCloseFile(handle: Long)
    // ... 与 JNI 一一对应
}
```

**上层封装（面向 ViewModel 的友好 API）**：
```kotlin
class NativeCodeViewer(path: String, languageHint: String?) : Closeable {
    private var handle: Long = ...
    fun readViewport(startLine: Long, endLine: Long): ViewportData
    fun folds(): List<FoldRegion>
    fun outline(): List<SymbolNode>
    override fun close()
}

class NativeCodeEditor(path: String) : Closeable {
    fun insert(offset: Int, text: String): EditResult
    fun erase(start: Int, end: Int): EditResult
    fun undo(): Boolean
    fun redo(): Boolean
    fun save(path: String): Boolean
    // ...
}
```

---

## 五、Kotlin/Compose 层设计

### 5.1 目录结构

```
core/viewer/
├── native/
│   ├── NativeViewerBridge.kt          # JNI external 声明
│   ├── NativeCodeViewer.kt            # 只读查看 native 封装
│   ├── NativeCodeEditor.kt            # 编辑器 native 封装
│   ├── NativeOfficeParser.kt          # Office 解析封装
│   └── dto/                           # DTO（FileInfoDto/ViewportData 等）
├── code/
│   ├── CodeViewerScreen.kt            # 只读查看页面
│   ├── CodeEditorScreen.kt            # 编辑页面
│   ├── CodeViewerViewModel.kt
│   ├── CodeEditorViewModel.kt
│   ├── component/
│   │   ├── CodeLineText.kt            # 单行渲染（AnnotatedString）
│   │   ├── LineNumberGutter.kt        # 行号栏
│   │   ├── CodeSearchBar.kt           # 搜索栏
│   │   ├── FoldToggle.kt              # 折叠按钮
│   │   └── SymbolOutlineSheet.kt      # 大纲抽屉
│   └── CodeThemeMapper.kt             # 语义类别 → 主题颜色映射
├── image/
│   ├── ImageViewerScreen.kt
│   ├── ImageViewerViewModel.kt
│   └── ZoomableImage.kt               # 缩放/平移组件
├── office/
│   ├── DocxPreviewScreen.kt
│   ├── XlsxPreviewScreen.kt
│   ├── PptxPreviewScreen.kt
│   ├── OfficePreviewViewModel.kt
│   └── component/
│       ├── RichTextBlock.kt           # 富文本段落渲染
│       ├── TableBlock.kt
│       ├── SheetGridView.kt
│       └── SlidePager.kt
└── pdf/
    └── （第三期）
```

### 5.2 代码查看器渲染方案

**视口化渲染**：
- 使用 `LazyColumn`（或自研文本列表，因 LazyColumn 对超长文本行有局限）
- 仅渲染可见行 ± 缓冲区（默认上下各 30 行）
- 行内容通过 native 按需读取
- 高亮 span 按视口从 native 获取，本地缓存（LruCache，key=行号）

**横向长行处理**：
- 外层 `horizontalScroll`（每行独立宽度计算）或
- 软换行开关（默认关闭，可开启按屏幕宽度 wrap）
- 修复之前"日志内容超出屏幕被截断"的问题

**CodeThemeMapper**：
```kotlin
object CodeThemeMapper {
    fun colorFor(category: HighlightCategory, colorScheme: ColorScheme): Color =
        when (category) {
            KEYWORD -> colorScheme.primary
            STRING -> /* 自定义 codeString 色，从主题派生 */
            COMMENT -> colorScheme.outline
            FUNCTION -> colorScheme.tertiary
            ...
        }
}
```
高亮颜色全部从 MaterialTheme colorScheme 派生，自动适配明暗主题，不硬编码。

### 5.3 编辑器交互

- 文本输入：`BasicTextField2`（或自定义文本布局直接对接 native buffer）
- 第一期方案：Compose TextField 承载编辑态，native text_buffer 作为同步后端（每次变更同步），保证撤销/大文件能力
- 第二期：自研编辑文本布局，直接以 native 为唯一数据源（避免双端状态同步问题）
- 自动缩进：换行时从 native 取缩进
- 括号补全：输入 `(` 自动补 `)`，成对删除
- 未保存提示：返回时若 dirty，弹窗确认
- 只读文件：隐藏键盘，顶部显示"只读"标识

### 5.4 Office 预览 UI

- 顶部显示文档类型图标 + 文件名
- DOCX：LazyColumn 渲染块（段落/表格/图片），样式贴近文档但使用项目 AppCard/Text 组件
- XLSX：sheet 切换 tab（复用统一 tab 样式）+ 可横向纵向滚动的单元格网格，冻结首行首列（可选）
- PPTX：HorizontalPager 翻页 + 底部页码指示器
- 右上角溢出菜单："用其他应用打开"、"另存为"、文档属性
- 不支持的格式：居中说明 + Filled 按钮"用其他应用打开"

---

## 六、CMake 构建体系

### 6.1 目录结构

```
app/src/main/cpp/
├── CMakeLists.txt                     # 顶层
├── jni_bridge.cpp
├── core/
│   ├── file_loader.hpp/cpp
│   └── text_buffer.hpp/cpp
├── treesitter/
│   ├── ts_parser.hpp/cpp
│   └── thirdparty/
│       ├── tree-sitter/               # 源码（git submodule 或 vendored）
│       ├── tree-sitter-kotlin/
│       ├── tree-sitter-java/
│       └── ...（各 grammar）
├── office/
│   ├── docx_extractor.hpp/cpp
│   ├── xlsx_extractor.hpp/cpp
│   ├── pptx_extractor.hpp/cpp
│   ├── office_types.hpp
│   └── thirdparty/
│       ├── minizip-ng/
│       └── pugixml/
├── image/
│   ├── image_loader.hpp/cpp
│   └── thirdparty/stb_image.h
└── pdf/（第三期）
```

### 6.2 第三方库管理策略

- **优先 vendored 源码**：将 tree-sitter、pugixml 等源码直接放入 `thirdparty/`（这些库体积小、稳定），避免网络依赖导致 CI 不稳定
- 不使用 git submodule（CI 需 `--recursive`，容易遗漏）
- 每个 thirdparty 库保留 LICENSE 文件，在"关于 → 开源致谢"中列出
- tree-sitter grammar 按需引入，每个 grammar 仅一个 parser.c + 少量文件

### 6.3 CMake 关键配置

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(minimeviewer LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)

# 编译优化：-Oz（体积优先），去除调试符号（release）
# 每个 grammar 单独静态库，避免全量 unity build 内存爆炸

# tree-sitter 核心
add_subdirectory(treesitter/thirdparty/tree-sitter)
# 各 grammar（每个一个静态库）
add_library(ts_kotlin STATIC treesitter/thirdparty/tree-sitter-kotlin/src/parser.c)
# ...

# pugixml / minizip-ng
add_subdirectory(office/thirdparty/pugixml)

# 主 so
add_library(minimeviewer SHARED
    jni_bridge.cpp
    core/file_loader.cpp
    core/text_buffer.cpp
    treesitter/ts_parser.cpp
    office/docx_extractor.cpp
    office/xlsx_extractor.cpp
    office/pptx_extractor.cpp
    image/image_loader.cpp
)
target_link_libraries(minimeviewer
    tree-sitter ts_kotlin ts_java ts_python ...
    pugixml minizip log android)
```

### 6.4 Gradle 集成

在 `app/build.gradle.kts` android {} 中新增：
```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
defaultConfig {
    externalNativeBuild {
        cmake {
            arguments += listOf(
                "-DANDROID_STL=c++_shared",
                "-DCMAKE_BUILD_TYPE=Release"
            )
            cFlags += listOf("-Oz", "-fvisibility=hidden")
            cppFlags += listOf("-Oz", "-fvisibility=hidden", "-fno-exceptions"（可选）)
        }
    }
}
```

**注意**：
- 使用 `c++_shared` STL（多个静态库共享，体积小），需确保 APK 打包 libc++_shared.so（Gradle 自动处理）
- release 变体 `-Oz` 优化体积，debug 保持 -O0
- 编译缓存：CI 中对 `.cxx` 和 native 构建产物配置 Gradle cache key（含 CMakeLists 和 thirdparty 哈希）

---

## 七、分阶段实施计划

### 第一阶段：基建 + 只读代码查看（核心地基）

| 序号 | 任务 | 产出 | 预估 |
|------|------|------|------|
| 1.1 | CMake 构建体系搭建 + Gradle 集成 | 空 so 成功编译打包 | 0.5 天 |
| 1.2 | JNI 桥接骨架 + 句柄管理 + 错误机制 | jni_bridge.cpp | 0.5 天 |
| 1.3 | file_loader（读取/编码检测/行索引） | 编译+单元测试 | 1 天 |
| 1.4 | tree-sitter vendored + 编译验证（先 3 个 grammar：kotlin/java/python） | 静态库链路打通 | 1 天 |
| 1.5 | ts_parser 封装（全量解析+span 计算） | 高亮数据正确 | 1 天 |
| 1.6 | CodeViewerScreen + ViewModel（视口加载、行号、横向滚动） | 可用页面 | 1.5 天 |
| 1.7 | CodeThemeMapper + 明暗主题适配 | 主题联动 | 0.5 天 |
| 1.8 | 补齐其余 grammar（共 14 个） | 全语言支持 | 1 天 |
| 1.9 | 搜索、折叠、大纲、行跳转 | 完整查看功能 | 1.5 天 |
| 1.10 | native 单元测试 + 集成验证 + 替换旧 WebView 方案 | 质量收口 | 1 天 |

**第一阶段出口标准**：
- 50MB 代码文件流畅打开、滚动、搜索
- 14 种语言高亮准确，明暗主题正确
- 旧 WebView 代码查看路径全部替换
- compileDebugKotlin 0 error，native 单测通过

### 第二阶段：代码编辑器 + Office 预览 + 图片查看

| 序号 | 任务 | 预估 |
|------|------|------|
| 2.1 | text_buffer（piece table + 撤销重做） | 1.5 天 |
| 2.2 | tree-sitter 增量解析对接 | 1 天 |
| 2.3 | CodeEditorScreen（编辑/保存/dirty 提示/自动缩进/括号补全） | 2 天 |
| 2.4 | minizip-ng + pugixml 集成编译 | 0.5 天 |
| 2.5 | docx_extractor + 预览页面 | 1.5 天 |
| 2.6 | xlsx_extractor + 表格预览（多 sheet） | 1.5 天 |
| 2.7 | pptx_extractor + 翻页预览 | 1 天 |
| 2.8 | 外部应用打开兜底（FileProvider/Intent） | 0.5 天 |
| 2.9 | image_loader + ImageViewerScreen（缩放/平移/保存/分享） | 1.5 天 |
| 2.10 | 各模块测试 + 性能验证 + 收口 | 1.5 天 |

**第二阶段出口标准**：
- 编辑器支持 5MB 文件流畅编辑、撤销重做、正确保存
- 三类 Office 文档可应用内预览，旧格式正确兜底
- 图片查看器交互完整
- 无数据丢失、无编码损坏

### 第三阶段：PDF + 编辑器高级能力 + 打磨

| 序号 | 任务 | 预估 |
|------|------|------|
| 3.1 | PDFium 集成（协议确认 + 编译） | 2 天 |
| 3.2 | PdfViewerScreen（渲染/缩放/目录/文本选择） | 1.5 天 |
| 3.3 | 多光标编辑、查找替换高级模式 | 1.5 天 |
| 3.4 | 自研编辑文本布局（native 为唯一数据源，去除双端同步） | 3 天 |
| 3.5 | 编辑器：代码补全接口预留、minimap（可选） | 1.5 天 |
| 3.6 | 全量性能优化、内存剖析、低端机适配 | 1.5 天 |
| 3.7 | 文档、开源致谢更新、发版 | 1 天 |

### 实施节奏说明

- 每阶段完成后编译验证 + 可独立发版（第一阶段即可发一版验证查看效果）
- 严格串行（遵循用户偏好），不并行 native 编译重任务
- native 编译耗时长，CI 需配置 ccache 或 Gradle 缓存避免每次全量编译 grammar
- 遇到技术瓶颈（如 grammar 编译问题、PDFium 移植）直接联网检索成熟解法

---

## 八、体积评估（arm64-v8a，release strip + -Oz + LTO + gc-sections）

> 已确认优化：release 仅打 arm64-v8a（debug 保留 x86_64 供模拟器）；LTO + gc-sections + ICF；PDFium 直接内置主包。

| 模块 | 预估 so 增量（arm64） |
|------|----------------------|
| JNI bridge + file_loader + text_buffer | ~0.6 MB |
| tree-sitter 核心 | ~0.25 MB |
| 16 个 grammar（LTO 优化后） | ~1.5-2.0 MB |
| pugixml + minizip-ng | ~0.4 MB |
| Office 三个提取器 | ~0.5 MB |
| stb_image | ~0.08 MB |
| **第一+二期合计** | **约 3.3-4.0 MB** |
| PDFium（第三期，直接内置） | ~6-10 MB |
| 自研布局 + 多光标 + diff + hex | ~0.5 MB |
| **三期总计** | **约 10-14.5 MB** |

说明：
- 以上为 strip 后、-Oz + LTO + gc-sections 优化的单 ABI（arm64-v8a）估算
- release 仅 arm64-v8a，APK 内 so 体积为单 ABI 值；debug 保留 arm64+x86_64 双 ABI
- 移除 WebView 查看方案可抵消部分体积（highlight.js/资源）
- PDFium 直接内置主 APK，不做按需下载；LTO 后预计 6-10MB（arm64）
- libc++_shared.so 由 SQLCipher 已引入，不重复计算

---

## 九、风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| tree-sitter grammar 编译问题（个别 grammar 非标准 CMake） | 阻塞 | 逐个验证；用官方推荐编译参数；失败的 grammar 延后 |
| NDK 编译显著拖慢 CI | 发版慢 | ccache + Gradle native 缓存；thirdparty 变更极少可长期缓存 |
| Compose TextField 与 native buffer 双端状态不同步 | 编辑数据错误 | 第一期单向同步（TextField 为编辑源 → native 为后端），第二期自研布局彻底统一 |
| OOXML 复杂文档解析不全 | 预览缺内容 | 保证文本不丢失（宁可样式简化）；提供外部打开兜底 |
| 编码误判导致乱码 | 数据显示错误 | 多算法投票 + 置信度；低置信度让用户手动选择编码 |
| PDFium 编译体积/复杂度超预期 | 三期延期 | 三期独立，不影响前两期；可退回系统 PDF 查看器 |
| native 内存泄漏 | 长时间使用 OOM | 句柄强制 Closeable；ASan 调试构建检测泄漏 |
| 保存中断导致文件损坏 | 用户数据丢失 | 原子写入（tmp + rename）；保存前备份原文件（.bak） |

---

## 十、验证方案

### 10.1 Native 单元测试

- file_loader：构造各编码/换行符样本文件，验证读取与检测
- text_buffer：随机编辑操作序列，验证文本一致性、撤销重做正确性（fuzz 测试）
- tree-sitter：各语言样本代码，验证关键 span 类别
- office：准备标准 docx/xlsx/pptx 样本，验证提取内容完整
- 使用 Android 仪器测试或主机端 gtest（file_loader/text_buffer 为平台无关代码，可直接主机 gtest，反馈更快）

### 10.2 性能基线

| 场景 | 目标 |
|------|------|
| 1MB 代码文件打开 | < 300ms |
| 50MB 文件滚动 | 稳定 60fps，无卡顿 |
| 编辑器输入延迟 | < 16ms |
| 10MB xlsx 预览解析 | < 3s |
| native 内存（50MB 文件） | < 100MB |

### 10.3 编译验证（交子 agent）

- `:app:compileDebugKotlin` 0 error/warning
- native 全 ABI 编译通过（arm64-v8a + x86_64）
- release 构建仍由 GitHub Actions 云端执行（遵守发版硬规则，本地不做 release build）
- APK 中确认 so 存在且 ABI 正确

### 10.4 发版集成

- 每阶段作为独立版本发布，发版说明严格遵守《发版说明格式规范》
- native 能力相关变更按"新功能/改进"分档，描述用户可感知价值
- 开源致谢补充 tree-sitter、pugixml、minizip-ng、stb_image、PDFium 等协议信息

---

## 十一、任务落地方式

- 本方案确认后，由子 agent（串行队列）按阶段执行
- 第一阶段从"CMake 基建 + 技术验证"开始，先证明编译链路可行再铺开
- MainAgent 与用户保持讨论，阶段性汇报进度
- 每阶段出口标准达成并验证后，再进入下一阶段
- 全部完成或每阶段完成后按流程云端构建、打 tag、发版

---

## 附：关键技术选型协议清单

| 库 | 协议 | 用途 | 协议与 GPL-3 兼容性 |
|----|------|------|---------------------|
| tree-sitter | MIT | 语法解析 | 兼容 |
| pugixml | MIT | XML 解析 | 兼容 |
| minizip-ng | Zlib | zip 解压 | 兼容 |
| stb_image | Public Domain / MIT | 图片解码 | 兼容 |
| PDFium | BSD-3-Clause | PDF 渲染 | 兼容 |
| zlib | Zlib | 压缩基础 | 兼容 |

全部依赖与项目 GPL-3.0 协议兼容。
