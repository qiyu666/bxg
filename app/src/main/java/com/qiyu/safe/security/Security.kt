package com.qiyu.safe.security

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 密码学工具：TOTP 密钥 Base32、PBKDF2 密码哈希、QR 码生成。 */
object Security {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val secureRandom = SecureRandom()
    internal const val PBKDF2_ITERATIONS = 100_000

    // ---------- Base32 (RFC 4648, 无填充) ----------

    fun base32Encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder()
        var bits = 0
        var value = 0
        for (b in data) {
            value = (value shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(B32[(value shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(B32[(value shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    fun base32Decode(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("-", "").uppercase().trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)
        val output = java.io.ByteArrayOutputStream()
        var bits = 0
        var value = 0
        for (c in clean) {
            val idx = B32.indexOf(c)
            if (idx < 0) continue
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                output.write((value shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return output.toByteArray()
    }

    /** 生成 TOTP 用的随机 Base32 密钥（20 字节 -> 32 字符）。 */
    fun randomTotpSecret(): String = base32Encode(ByteArray(20) { secureRandom.nextInt(256).toByte() })

    // ---------- PBKDF2 密码哈希（salt 与摘要均以 Base64 存储） ----------

    /** 对密码加盐哈希，返回 (saltBase64, hashBase64)。 */
    fun hashAndSaltPassword(password: String): Pair<String, String> {
        val salt = ByteArray(16) { secureRandom.nextInt(256).toByte() }
        val hash = hashPassword(password, salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP) to
            Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, saltB64: String, hashB64: String): Boolean {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = hashPassword(password, salt)
        return expected.contentEquals(actual)
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return factory.generateSecret(spec).encoded
    }

    // ---------- QR 码 ----------

    /** 将 otpauth URI 渲染为 QR 码位图（zxing）。 */
    fun toQrBitmap(uri: String, sizePx: Int = 480): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (x in 0 until w) {
            for (y in 0 until h) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    // ---------- AES-GCM 备份加解密（数据导出/导入） ----------

    const val AES_SALT_BYTES = 16
    const val AES_IV_BYTES = 12

    /** 随机字节（SecureRandom）。 */
    fun randomBytes(n: Int): ByteArray {
        val b = ByteArray(n)
        secureRandom.nextBytes(b)
        return b
    }

    /** 用密码 + 随机盐派生 256 位 AES 密钥（PBKDF2，SHA1——Android 各 ROM 通用，SHA256 在部分机型报 InvalidKeySpecException）。 */
    fun deriveAesKey(password: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    /** AES/GCM 加密，返回含 16 字节认证标签的密文。 */
    fun encryptAesGcm(plain: String, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    /** AES/GCM 解密。密码错误或数据被篡改时抛出异常。 */
    fun decryptAesGcm(cipherData: ByteArray, key: ByteArray, iv: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(c.doFinal(cipherData), Charsets.UTF_8)
    }
}
