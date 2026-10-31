package dev.emyth.app.data.themeshare

import dev.emyth.app.utils.JsonInstant
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 主题分享码编解码：ThemeShareData -> JSON -> gzip -> base64(url安全)。
 * 纯本地，不依赖服务器。
 */
object ThemeShareCodec {

    fun encode(data: ThemeShareData): String {
        val json = JsonInstant.encodeToString(ThemeShareData.serializer(), data)
        val gzip = gzipCompress(json.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(gzip)
    }

    fun decode(code: String): ThemeShareData {
        val gzip = Base64.getUrlDecoder().decode(code.trim())
        val json = gzipDecompress(gzip).toString(Charsets.UTF_8)
        return JsonInstant.decodeFromString(ThemeShareData.serializer(), json)
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    /** 本地文件 -> base64（图片内嵌进分享码用） */
    fun fileToBase64(path: String): String? = runCatching {
        Base64.getUrlEncoder().withoutPadding().encodeToString(java.io.File(path).readBytes())
    }.getOrNull()

    /** base64 -> 本地文件，返回是否成功 */
    fun base64ToFile(base64: String, path: String): Boolean = runCatching {
        java.io.File(path).writeBytes(Base64.getUrlDecoder().decode(base64))
    }.isSuccess
}
