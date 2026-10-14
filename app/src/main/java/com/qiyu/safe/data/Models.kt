package com.qiyu.safe.data

/** 存单期限类型：活期（随时可取）或死期（到期前锁定，到期自动转活期）。 */
enum class TermType(val label: String) {
    DEMAND("活期"),
    FIXED("死期")
}

/** 一笔存款（存单）。金额统一以「分」为单位存储，避免浮点误差。 */
data class Deposit(
    val id: Long,
    val amountFen: Long,
    val term: TermType,
    /** 死期存单的期限（月）。活期为 0。 */
    val periodMonths: Int,
    val startTs: Long,
    /** 到期时间戳（毫秒）。活期为 0（即刻可用）。死期 = startTs + periodMonths。 */
    val matureTs: Long,
    /** 是否已（自动）转成活期。 */
    val converted: Boolean,
    val note: String
)

/** 资金流水记录。 */
data class Transaction(
    val id: Long,
    val kind: Kind,
    val amountFen: Long,
    val ts: Long,
    val note: String
) {
    enum class Kind(val label: String) {
        DEPOSIT("存入"),
        WITHDRAW("取出"),
        CONVERT("死期转活期")
    }
}

/** 账户余额快照。 */
data class VaultBalances(
    /** 总余额 = 全部存入 - 全部取出 */
    val totalFen: Long,
    /** 活期可用 = 活期存单 + 已转活期的死期存单 - 已取出 */
    val demandFen: Long,
    /** 死期锁定 = 尚未到期的死期存单 */
    val fixedLockedFen: Long
)

/** 供 Compose 展示的完整账户数据。 */
data class VaultSnapshot(
    val balances: VaultBalances,
    val deposits: List<Deposit>,
    val transactions: List<Transaction>,
    /** 储蓄目标（分），0 表示未设目标 */
    val goalFen: Long = 0
)

/** 格式化：分 -> 「¥xxx.xx」 */
fun formatYuan(fen: Long): String =
    "¥" + String.format(java.util.Locale.ROOT, "%d.%02d", fen / 100, fen % 100)

fun formatFen(fen: Long): String =
    String.format(java.util.Locale.ROOT, "%d.%02d", fen / 100, fen % 100)
