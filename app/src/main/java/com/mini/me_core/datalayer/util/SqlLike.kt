package com.mini.me_core.datalayer.util

/**
 * SQL `LIKE` 通配符转义（审计 M7）。
 *
 * 配合 `LIKE ... ESCAPE '\'` 使用：把用户输入中的三个 LIKE 特殊字符转义，
 * 避免它们被当成通配符导致结果膨胀（或全表扫描）——
 *  - `\` → `\\`
 *  - `%` → `\%`（任意长度通配）
 *  - `_` → `\_`（单字符通配）
 *
 * 调用方负责在外层拼 `%kw%` 的前后缀；本函数只转义「用户内容」部分。
 */
fun escapeSqlLike(input: String): String =
    input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
