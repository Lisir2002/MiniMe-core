package com.mini.me_core.feature.git.domain

/**
 * git ref（引用）与 commit hash 的格式校验工具，防止把含 shell 元字符或越界语义的字符串直接拼进
 * `git show <ref>:<path>`、`git checkout <ref>` 等命令。
 *
 * 即便命令参数已经过 [com.mini.me_core.feature.git.domain.GitRepository.shellQuote] 单引号转义、
 * 不可能注入 shell，这里仍做一道白名单校验：只放行 git rev 语法里**合法且无歧义**的字符，
 * 拒绝空格、分号、引号、`$`、反引号、换行等任何可能被当成参数分隔或命令拼接的字符。
 *
 * 允许的字符：字母、数字，以及 `/` `_` `.` `-` `~` `^` `@` `{` `}`。
 * 据此可容纳常见的合法写法：分支/标签名（`main`、`origin/feature`、`v1.0.0`）、
 * 相对引用（`HEAD~1`、`HEAD^2`）、reflog 语法（`master@{1}`、`stash@{0}`）与完整/短哈希。
 */
object GitRefValidator {

    /** 合法 ref 字符白名单。`.` 在字符组内是字面量；`-` 放在组尾避免被解释成范围。 */
    private val REF_REGEX = Regex("^[A-Za-z0-9/_~^@{}.\\-]+$")

    /** 完整 commit 对象名：40 位十六进制（SHA-1）。 */
    private val HASH_REGEX = Regex("^[0-9a-fA-F]{40}$")

    /**
     * 判断 [ref] 是否为合法 git ref 字符串。空串或含白名单外字符一律返回 false。
     *
     * 注意：本方法只做「字符级」白名单校验，不解析 ref 是否真实存在——存在性由 git 自身
     * 据退出码报错，这里只挡格式与注入问题。
     */
    fun isValidRef(ref: String): Boolean = ref.isNotEmpty() && REF_REGEX.matches(ref)

    /**
     * 校验 [ref] 合法，非法时抛 [IllegalArgumentException]；合法则原样返回，便于链式传参。
     *
     * @throws IllegalArgumentException 当 [ref] 为空或含白名单外字符时抛出。
     */
    fun requireValidRef(ref: String): String {
        if (!isValidRef(ref)) {
            throw IllegalArgumentException("非法 git ref: $ref")
        }
        return ref
    }

    /**
     * 判断 [hash] 是否为合法的完整 commit 哈希（40 位十六进制）。用于 diff-tree 等直接吃
     * 对象名的命令，挡住被篡改/拼接过的 hex 串。短哈希（7 位）不在此放行——调用方确需短哈希时
     * 应走 [isValidRef]。
     */
    fun isValidHash(hash: String): Boolean = HASH_REGEX.matches(hash)

    /**
     * [isValidHash] 的抛异常版本：非法时抛 [IllegalArgumentException]，合法则原样返回。
     */
    fun requireValidHash(hash: String): String {
        if (!isValidHash(hash)) {
            throw IllegalArgumentException("非法 commit hash（需 40 位十六进制）: $hash")
        }
        return hash
    }
}
