package com.qiyu.safe.security

import android.net.Uri
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP（RFC 6238）实现，SHA1 / 6 位 / 30 秒，
 * 与微软验证器（Microsoft Authenticator）及 Google Authenticator 通用。
 */
object Totp {
    const val PERIOD = 30
    const val DIGITS = 6
    private const val MODULUS = 1_000_000

    /** 当前（或指定时间）的 6 位动态码。 */
    fun code(secret: String, atMs: Long = System.currentTimeMillis()): String {
        val key = Security.base32Decode(secret)
        val counter = atMs / 1000L / PERIOD
        val buffer = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(buffer)
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = (
            ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        )
        return (binary % MODULUS).toString().padStart(DIGITS, '0')
    }

    /** 校验用户输入的动态码，允许前后各 1 个时间步的偏差。 */
    fun verify(secret: String, input: String, atMs: Long = System.currentTimeMillis()): Boolean {
        val code = input.trim()
        if (code.length != DIGITS) return false
        for (w in -2..2) {
            if (code(secret, atMs + w * PERIOD * 1000L) == code) return true
        }
        return false
    }

    /** 生成可被微软验证器扫码添加的 otpauth URI。 */
    fun otpauthUri(account: String, secret: String, issuer: String = "存钱保险柜"): String {
        val acct = Uri.encode(account.replace(" ", "_"))
        val iss = Uri.encode(issuer)
        return "otpauth://totp/$iss:$acct" +
            "?secret=$secret&issuer=$iss&algorithm=SHA1&digits=$DIGITS&period=$PERIOD"
    }
}
