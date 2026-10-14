package com.qiyu.safe.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 备份 JSON 的序列化 / 反序列化往返（org.json，JVM 可跑）。 */
class BackupTest {

    private val user = BackupUser("alice", "a@x.com", "TOTPSECRET", "SALT", "HASH", 50_000L)

    private val deposits = listOf(
        Deposit(1L, 100_000L, TermType.DEMAND, 0, 1000L, 0L, false, "存款"),
        Deposit(2L, 200_000L, TermType.FIXED, 12, 1000L, 2000L, false, "定期")
    )

    private val txns = listOf(
        Transaction(1L, Transaction.Kind.DEPOSIT, 100_000L, 1000L, "存入"),
        Transaction(2L, Transaction.Kind.WITHDRAW, 50_000L, 1500L, "取出")
    )

    @Test
    fun body_roundTrip() {
        val json = Backup.writeBody(user, deposits, txns)
        val parsed = Backup.readBody(json)

        assertEquals(user.username, parsed.user.username)
        assertEquals(user.email, parsed.user.email)
        assertEquals(user.totpSecret, parsed.user.totpSecret)
        assertEquals(user.passSalt, parsed.user.passSalt)
        assertEquals(user.passHash, parsed.user.passHash)
        assertEquals(user.goalFen, parsed.user.goalFen)

        assertEquals(deposits.size, parsed.deposits.size)
        assertEquals(TermType.FIXED, parsed.deposits[1].term)
        assertEquals(12, parsed.deposits[1].periodMonths)
        assertEquals(200_000L, parsed.deposits[1].amountFen)

        assertEquals(txns.size, parsed.transactions.size)
        assertEquals(Transaction.Kind.WITHDRAW, parsed.transactions[1].kind)
        assertEquals(50_000L, parsed.transactions[1].amountFen)
    }

    @Test
    fun readBody_emptyLists() {
        val parsed = Backup.readBody(Backup.writeBody(user, emptyList(), emptyList()))
        assertEquals(0, parsed.deposits.size)
        assertEquals(0, parsed.transactions.size)
    }
}
