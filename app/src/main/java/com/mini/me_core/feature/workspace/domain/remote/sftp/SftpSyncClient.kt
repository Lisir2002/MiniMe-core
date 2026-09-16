package com.mini.me_core.feature.workspace.domain.remote.sftp

import com.mini.me_core.feature.workspace.domain.remote.RemoteAuth
import com.mini.me_core.feature.workspace.domain.remote.RemoteFileInfo
import com.mini.me_core.feature.workspace.domain.remote.RemoteSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File

/**
 * SFTP 文件同步客户端。
 *
 * 安全约束：[hostKeyVerifier] 为必传参数，调用方必须显式传入真实的主机密钥校验器
 * （通常为 HostKeyManager.createVerifier() 的 TOFU 实现），禁止再回退到 PromiscuousVerifier
 * （接受任意主机密钥，存在中间人攻击风险）。
 */
class SftpSyncClient(
    private val hostKeyVerifier: HostKeyVerifier
) : RemoteSyncClient {

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    override suspend fun connect(host: String, port: Int, username: String, auth: RemoteAuth) = withContext(Dispatchers.IO) {
        sshClient = SSHClient().apply {
            // 强制走真实主机密钥校验（TOFU），不再静默接受任意主机。
            addHostKeyVerifier(hostKeyVerifier)
            connect(host, port)
            
            when (auth) {
                is RemoteAuth.Password -> authPassword(username, auth.password)
                is RemoteAuth.PrivateKey -> {
                    val keyProvider = if (auth.passphrase != null) {
                        loadKeys(auth.privateKeyPath, auth.passphrase)
                    } else {
                        loadKeys(auth.privateKeyPath)
                    }
                    authPublickey(username, keyProvider)
                }
            }
        }
        sftpClient = sshClient?.newSFTPClient()
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        sftpClient?.close()
        sshClient?.disconnect()
        sftpClient = null
        sshClient = null
    }

    override suspend fun listFiles(remotePath: String): List<RemoteFileInfo> = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        sftp.ls(remotePath).map {
            RemoteFileInfo(
                name = it.name,
                isDirectory = it.attributes.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY,
                size = it.attributes.size,
                lastModified = it.attributes.mtime * 1000L // mtime is in seconds
            )
        }
    }

    override suspend fun downloadFile(remotePath: String, localPath: String) = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()
        sftp.get(remotePath, localPath)
    }

    override suspend fun uploadFile(localPath: String, remotePath: String) = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        val localFile = File(localPath)
        if (localFile.exists()) {
            sftp.put(localPath, remotePath)
        }
    }

    override suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        sftp.mkdirs(remotePath)
    }

    override suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        val attrs = sftp.statExistence(remotePath)
        if (attrs != null) {
            if (attrs.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
                // 递归删除暂未实现
                sftp.rmdir(remotePath)
            } else {
                sftp.rm(remotePath)
            }
        }
    }

    override suspend fun isConnected(): Boolean = sshClient?.isConnected == true && sshClient?.isAuthenticated == true
}
