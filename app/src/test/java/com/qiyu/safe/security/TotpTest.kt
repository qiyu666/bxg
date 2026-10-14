package com.qiyu.safe.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 直接对本 App 真实的 Kotlin 代码做单元测试（纯 JVM，不走转译），
 * 用 RFC 6238 官方 TOTP 向量验证 Totp + Security.base32 的正确性。
 */
class TotpTest {

    private val rfcKey = "12345678901234567890".toByteArray()

    @Test
    fun base32Encode_matchesStandard() {
        // "12345678901234567890" 的标准 base32 值
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Security.base32Encode(rfcKey))
    }

    @Test
    fun base32_decodeRoundTrip() {
        val b32 = Security.base32Encode(rfcKey)
        assertArrayEquals(rfcKey, Security.base32Decode(b32))
    }

    @Test
    fun totp_matchesRFC6238Vectors() {
        val b32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        // RFC 6238（SHA1, period=30, 6 位）：这些值已用 pyotp 交叉确认
        assertEquals("287082", Totp.code(b32, 59_000L))
        assertEquals("081804", Totp.code(b32, 1_111_111_109_000L))
        assertEquals("050471", Totp.code(b32, 1_111_111_111_000L))
    }

    @Test
    fun verify_acceptsCurrentCode() {
        val b32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val now = 59_000L
        val code = Totp.code(b32, now)
        assertTrue(Totp.verify(b32, code, now))
        // 相邻周期（±1 步）也应通过
        assertTrue(Totp.verify(b32, Totp.code(b32, now + 30_000L), now))
    }
}
