package com.qiyu.safe.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证备份用的 AES-GCM 加解密与密钥派生（纯 JVM，不触 Android）。 */
class SecurityTest {

    private val salt = "0123456789abcdef".toByteArray()

    @Test
    fun aesGcm_roundTrip() {
        val key = Security.deriveAesKey("支付密码123", salt)
        val iv = Security.randomBytes(Security.AES_IV_BYTES)
        val plain = "你好，保险柜 \"json\": {\"a\":1} 备份数据"
        val cipher = Security.encryptAesGcm(plain, key, iv)
        assertEquals(plain, Security.decryptAesGcm(cipher, key, iv))
        // GCM 密文比明文长（含 16 字节认证标签）
        assertTrue(cipher.size > plain.toByteArray().size)
    }

    @Test
    fun aesGcm_wrongKeyFails() {
        val key1 = Security.deriveAesKey("aa", salt)
        val key2 = Security.deriveAesKey("bb", salt)
        val iv = Security.randomBytes(Security.AES_IV_BYTES)
        val cipher = Security.encryptAesGcm("secret", key1, iv)
        val threw = try {
            Security.decryptAesGcm(cipher, key2, iv)
            false
        } catch (e: Exception) {
            true
        }
        assertTrue("用错误密钥解密应当失败", threw)
    }

    @Test
    fun deriveAesKey_isDeterministicForSameSalt() {
        assertArrayEquals(Security.deriveAesKey("pw", salt), Security.deriveAesKey("pw", salt))
    }

    @Test
    fun deriveAesKey_differsAcrossPasswords() {
        val a = Security.deriveAesKey("one", salt)
        val b = Security.deriveAesKey("two", salt)
        assertTrue(!java.util.Arrays.equals(a, b))
        assertEquals(32, a.size) // 256 位
    }
}
