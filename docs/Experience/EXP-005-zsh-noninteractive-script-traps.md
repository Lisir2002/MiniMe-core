# EXP-005：zsh 非交互脚本三陷阱——无单词分裂、`status` 只读、`curl -d @`

> 一句话：在 zsh 下写非交互批处理/轮询脚本时，三个与 bash 直觉不符、且报错信息毫不贴心的陷阱。

| 元数据 | 值 |
|---|---|
| 状态 | 活跃 |
| 严重度 | 次要 |
| 分类 | 工具链 |
| 创建日期 | 2026-10-20 |
| 最后验证 | 2026-10-20（三个坑当日各踩中一次，改写后恢复） |
| 创建人 | AI 协同会话（维护者确认） |
| 关联 | 本次发版过程的轮询/推送脚本 |

## 1. 问题现象

| 陷阱 | 写法 | 实际行为 |
|---|---|---|
| 无单词分裂 | `for f in $FILES`（FILES="a b c"） | zsh 默认不做单词分裂，整个字符串当成一个元素，循环只跑一次且路径错 |
| `status` 只读 | `status=$(curl ...)` | zsh 内置 `status` 等价于 `$?`，赋值直接报 read-only 错误，脚本中断 |
| `curl -d @` | `-d '{"body":@dependabot rebase}'` | `@` 被 curl 当文件引用，且 JSON 串缺引号 → 400 Bad Request，API 调用静默失败 |

## 2. 根因分析

- zsh 与 bash 的默认行为差异（SH_WORD_SPLIT 默认关）；这些差异在**非交互**执行时才暴露，交互式手打命令往往碰不到。
- `status` 是 zsh 为兼容 csh 提供的内置只读变量。
- curl 的 `-d @file` 语法会吞掉 `@` 后的内容当文件名，JSON 又缺双引号，两个错叠一起只报 400，不看请求体根本想不到根因。

## 3. 解决方案

**首选：非交互脚本显式用 bash 解释器**，从根上避开差异：

```bash
#!/bin/bash
#  shebang 声明 + 保存为 .sh 执行，不要依赖当前 shell 是 bash
```

**必须在 zsh 下跑时**，三个对应改法：

```bash
# ① 需要单词分裂时显式开启，或干脆写展开式
for f in ${=FILES}; do ...; done      # zsh：${=VAR} 显式分裂
for f in a b c; do ...; done          # 或直接逐项列出，最稳

# ② 避开 status / 其它 csh 兼容只读名（status, path, commands 等）
st=$(curl -s -o /dev/null -w "%{http_code}" "$url")   # 改名 st

# ③ curl 提交 JSON：用 --data-raw + 正确的引号 + 显式 Content-Type
curl -s --data-raw '{"body":"@dependabot rebase"}' \
     -H "Content-Type: application/json" \
     -H "Authorization: bearer $GH_TOKEN" \
     "https://api.github.com/repos/$O/$R/issues/$N/comments"
```

## 4. 验证方法

- 脚本先在 `bash -n <script>` 过语法；跑完检查 `echo "exit=$?"` 与预期一致。
- curl 类调用加 `-w "%{http_code}"` 或 `-sS`，把 HTTP 码打出来，别让 400 静默。

## 5. 预防与规则沉淀

- AI 协同会话生成的非交互脚本一律加 `#!/bin/bash` shebang；本机默认 shell 是 zsh 不是借口。
- 变量命名避开 csh 兼容只读名；`for` 循环优先写展开式。

## 6. 更新记录（追加式，只增不改）

| 日期 | 更新内容 | 触发事件 |
|---|---|---|
| 2026-10-20 | 初版 | 发版轮询脚本连踩三坑：`$FILES` 不分裂、`status` 只读、`-d @` 400 |
