package com.mini.me_core.feature.workspace.domain.remote.ftp

import com.mini.me_core.feature.workspace.domain.remote.RemoteAuth
import com.mini.me_core.feature.workspace.domain.remote.RemoteFileInfo
import com.mini.me_core.feature.workspace.domain.remote.RemoteSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 明文 FTP 风险策略（纯逻辑，便于单测）。
 *
 *  - 默认拒绝静默的明文登录（fail-closed）：未获用户显式风险确认前不发起明文登录；
 *  - 当存在 FTPS/SFTP 等安全通道可用时，绝不退化为明文；
 *  - 凭据绝不写日志。
 */
internal object FtpCleartextPolicy {
    /**
     * @param userConfirmedCleartext 用户是否已在本次连接前显式确认承担明文风险；
     * @param secureChannelAvailable 是否存在 FTPS/SFTP 等安全通道。
     * @return true=允许明文连接；false=拒绝（调用方应改用安全通道或请求用户确认）。
     */
    fun allowCleartext(userConfirmedCleartext: Boolean, secureChannelAvailable: Boolean): Boolean {
        if (secureChannelAvailable) return false
        return userConfirmedCleartext
    }
}

/** 明文 FTP 未经风险确认即被拒绝时抛出。 */
class CleartextFtpNotConfirmedException(message: String) : SecurityException(message)

class FtpSyncClient(
    /**
     * 用户是否已对本次明文 FTP 连接作出显式风险确认。默认 false（拒绝静默明文登录）。
     * 现有调用方不传该参数即得到最安全默认；确认 UI 的接线由调用层负责。
     */
    private val cleartextConfirmedByUser: Boolean = false,
) : RemoteSyncClient {

    private val ftpClient = FTPClient()
    private var isConnected = false

    override suspend fun connect(host: String, port: Int, username: String, auth: RemoteAuth) = withContext(Dispatchers.IO) {
        if (auth !is RemoteAuth.Password) {
            throw IllegalArgumentException("FTP only supports Password authentication")
        }

        // 明文风险闸口：默认拒绝静默明文登录；有安全通道时一律优先安全通道。
        // 此处本客户端只实现明文 FTP，故 secureChannelAvailable=false，仅靠用户确认放行。
        if (!FtpCleartextPolicy.allowCleartext(
                userConfirmedCleartext = cleartextConfirmedByUser,
                secureChannelAvailable = false
            )
        ) {
            throw CleartextFtpNotConfirmedException(
                "明文 FTP 未获风险确认：FTP 账号口令与文件均明文传输。" +
                    "请显式确认承担明文风险，或优先使用 SFTP/FTPS 等加密通道。"
            )
        }

        ftpClient.connect(host, port)
        val reply = ftpClient.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftpClient.disconnect()
            throw IllegalStateException("FTP server refused connection.")
        }

        if (!ftpClient.login(username, auth.password)) {
            ftpClient.disconnect()
            throw IllegalStateException("FTP login failed")
        }

        ftpClient.enterLocalPassiveMode()
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
        isConnected = true
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        if (ftpClient.isConnected) {
            ftpClient.logout()
            ftpClient.disconnect()
        }
        isConnected = false
    }

    override suspend fun listFiles(remotePath: String): List<RemoteFileInfo> = withContext(Dispatchers.IO) {
        val files = ftpClient.listFiles(remotePath) ?: emptyArray()
        files.map {
            RemoteFileInfo(
                name = it.name,
                isDirectory = it.isDirectory,
                size = it.size,
                lastModified = it.timestamp.timeInMillis
            )
        }
    }

    override suspend fun downloadFile(remotePath: String, localPath: String) = withContext(Dispatchers.IO) {
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()
        FileOutputStream(localFile).use { fos ->
            val success = ftpClient.retrieveFile(remotePath, fos)
            if (!success) {
                throw IllegalStateException("Failed to download file from FTP: $remotePath")
            }
        }
    }

    override suspend fun uploadFile(localPath: String, remotePath: String) = withContext(Dispatchers.IO) {
        val localFile = File(localPath)
        if (!localFile.exists()) return@withContext

        // 确保远程目录存在
        val remoteDir = remotePath.substringBeforeLast("/")
        if (remoteDir.isNotEmpty() && remoteDir != remotePath) {
             // 递归创建目录较复杂，暂简化处理
             ftpClient.makeDirectory(remoteDir)
        }

        FileInputStream(localFile).use { fis ->
            val success = ftpClient.storeFile(remotePath, fis)
            if (!success) {
                throw IllegalStateException("Failed to upload file to FTP: $remotePath")
            }
        }
    }

    override suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        ftpClient.makeDirectory(remotePath)
        Unit
    }

    override suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        // 先尝试当做文件删除，如果失败则当做目录删除
        if (!ftpClient.deleteFile(remotePath)) {
            ftpClient.removeDirectory(remotePath)
        }
    }

    override suspend fun isConnected(): Boolean = isConnected && ftpClient.isConnected
}
