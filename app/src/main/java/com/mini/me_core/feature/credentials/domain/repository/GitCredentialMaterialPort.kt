package com.mini.me_core.feature.credentials.domain.repository

/**
 * Git 凭据「按需物化」端口（feature 侧定义，容器/helper 侧由 D 组接线）。
 *
 * 现状：[GitCredentialsFileSync] 把解密后的 token 以 `https://user:token@host` 明文写入
 * `git-credentials` 供 `credential.helper=store` 持久复用。该文件在 app 私有目录已收紧 0600，
 * 但 https token 仍以明文静态落盘。
 *
 * 目标（D 组容器侧接线）：把 `.gitconfig` 的 `credential.helper` 从
 * `store --file=/root/.minime/git-credentials` 改为按需 helper（即已存在的
 * `git-credential-minime` IPC 链），使 token 不再静态落盘——git 真正需要时，由 helper
 * 经 cred-req/cred-resp IPC 从加密库临时取到受控位置，用完即删。
 *
 * B 组已在 feature/credentials 内做好：
 *  - 凭据在 SQLDelight 内加密存储（[CredentialRepository] 走 CredentialEncryptor）；
 *  - 落盘文件 / 临时响应文件一律 0600；
 *  - token 不进日志、不在 URL 回显。
 *
 * D 组需在容器侧（LinuxContainerEngine / helper 脚本 / .gitconfig 安装）接线的接口：
 * ```
 * interface GitCredentialMaterialPort {
 *     /** git 真正使用凭据时调用：把 host 对应凭据解密到受控临时文件，返回该文件；用完由调用方删除。 */
 *     suspend fun materializeForUse(host: String): MaterializedGitCredential?
 *
 *     /** 用完后清理临时物化文件（含内存中的明文 token 引用）。 */
 *     fun release(materialized: MaterializedGitCredential)
 * }
 * data class MaterializedGitCredential(
 *     val host: String,
 *     val username: String,
 *     val token: String,   // 明文仅在内存，用后释放
 *     val tempFile: File?,  // 若物化到文件，为受控临时文件路径
 * )
 * ```
 */
interface GitCredentialMaterialPort
