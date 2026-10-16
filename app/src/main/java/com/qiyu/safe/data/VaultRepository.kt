package com.qiyu.safe.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.qiyu.safe.security.Security
import com.qiyu.safe.security.Totp
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** 操作结果。 */
sealed class OpResult {
    data class Ok(val message: String = "") : OpResult()
    data class Err(val code: String, val message: String) : OpResult()
}

/** 供 Compose 展示的账户 UI 状态。 */
data class UiState(
    val loggedIn: Boolean = false,
    val username: String = "",
    val totpConfigured: Boolean = false,
    val snapshot: VaultSnapshot =
        VaultSnapshot(VaultBalances(0, 0, 0), emptyList(), emptyList())
)

private data class UserRow(val id: Long, val username: String, val totpSecret: String, val salt: String, val hash: String)

/**
 * 核心业务逻辑：注册/登录、存取款（微软验证器 + 支付密码双重校验）、
 * 死期到期自动转活期、余额计算。数据全部保存在本地 SQLite。
 */
object VaultRepository {
    private lateinit var context: Context
    private lateinit var db: VaultDatabase

    val uiState = MutableStateFlow(UiState())

    fun init(ctx: Context) {
        if (::db.isInitialized) return
        context = ctx.applicationContext
        db = VaultDatabase(context)
        SessionManager.restore(context)
        refresh()
    }

    // ---------------- 会话 ----------------

    fun logout() {
        SessionManager.clear(context)
        QuickTxn.disable()
        uiState.update { UiState() }
    }

    /** 校验当前用户的支付密码（用于解锁）。 */
    fun verifyPaymentPassword(password: String): Boolean {
        val uid = SessionManager.currentUserId
        if (uid < 0) return false
        return checkPaymentPassword(uid, password)
    }

    /** 设置储蓄目标（分）。0 = 不设置目标。 */
    fun setGoal(fen: Long) {
        val uid = SessionManager.currentUserId
        if (uid < 0) return
        db.writableDatabase.update(
            "users", ContentValues().apply { put("goal_fen", fen.coerceAtLeast(0)) },
            "id=?", arrayOf(uid.toString())
        )
        refresh()
    }

    private fun loadGoal(uid: Long): Long {
        return db.readableDatabase.rawQuery(
            "SELECT COALESCE(goal_fen,0) FROM users WHERE id=?", arrayOf(uid.toString())
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    fun register(username: String, email: String, password: String): OpResult {
        val name = username.trim()
        if (name.isEmpty()) return OpResult.Err("USER", "请输入用户名")
        if (password.length < 6) return OpResult.Err("PASS", "支付密码至少 6 位")
        var exists = false
        db.readableDatabase.rawQuery("SELECT 1 FROM users WHERE username=?", arrayOf(name)).use { c ->
            exists = c.moveToFirst()
        }
        if (exists) return OpResult.Err("DUP", "该用户名已注册，请换一个")
        val (salt, hash) = Security.hashAndSaltPassword(password)
        val cv = ContentValues().apply {
            put("username", name)
            put("email", email.trim().ifEmpty { null })
            put("pass_salt", salt)
            put("pass_hash", hash)
            put("totp_secret", Security.randomTotpSecret())
            put("created_at", System.currentTimeMillis())
        }
        val uid = db.writableDatabase.insert("users", null, cv)
        SessionManager.persist(context, uid)
        AppLock.unlock()
        QuickTxn.disable()
        refresh()
        return OpResult.Ok("注册成功，请绑定微软验证器")
    }

    fun login(username: String, password: String): OpResult {
        val name = username.trim()
        if (name.isEmpty()) return OpResult.Err("USER", "请输入用户名")
        db.readableDatabase.rawQuery("SELECT id, pass_salt, pass_hash FROM users WHERE username=?", arrayOf(name)).use { c ->
            if (!c.moveToFirst()) return OpResult.Err("NOUSER", "用户不存在，请先注册")
            val uid = c.getLong(0)
            val salt = c.getString(1)
            val hash = c.getString(2)
            if (!Security.verifyPassword(password, salt, hash)) return OpResult.Err("PASS", "支付密码错误")
            SessionManager.persist(context, uid)
            AppLock.unlock()
            QuickTxn.disable()
            refresh()
            return OpResult.Ok("登录成功")
        }
        return OpResult.Err("ERR", "登录失败")
    }

    fun changePaymentPassword(oldPass: String, newPass: String): OpResult {
        val uid = SessionManager.currentUserId
        if (uid < 0) return OpResult.Err("NOUSER", "未登录")
        if (newPass.length < 6) return OpResult.Err("PASS", "新支付密码至少 6 位")
        db.readableDatabase.rawQuery("SELECT pass_salt, pass_hash FROM users WHERE id=?", arrayOf(uid.toString())).use { c ->
            if (!c.moveToFirst()) return OpResult.Err("NOUSER", "用户不存在")
            if (!Security.verifyPassword(oldPass, c.getString(0), c.getString(1))) {
                return OpResult.Err("PASS", "原支付密码错误")
            }
            val (salt, hash) = Security.hashAndSaltPassword(newPass)
            db.writableDatabase.update(
                "users",
                ContentValues().apply {
                    put("pass_salt", salt)
                    put("pass_hash", hash)
                },
                "id=?",
                arrayOf(uid.toString())
            )
            return OpResult.Ok("支付密码已更新")
        }
    }

    // ---------------- 验证器 ----------------

    /** 当前用户的验证器 otpauth URI（用于扫码），未绑定返回 null。 */
    fun authenticatorUri(): String? {
        val uid = SessionManager.currentUserId
        if (uid < 0) return null
        return db.readableDatabase.rawQuery(
            "SELECT totp_secret, username FROM users WHERE id=?", arrayOf(uid.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            val secret = c.getString(0)
            if (secret.isNullOrEmpty()) return null
            Totp.otpauthUri(c.getString(1), secret)
        }
    }

    fun totpSecret(): String? {
        val uid = SessionManager.currentUserId
        if (uid < 0) return null
        return db.readableDatabase.rawQuery("SELECT totp_secret FROM users WHERE id=?", arrayOf(uid.toString())).use { c ->
            if (!c.moveToFirst()) return null
            val s = c.getString(0)
            s?.ifEmpty { null }
        }
    }

    /** 重新生成验证器密钥（相当于在验证器中重新添加账号）。 */
    fun rebindAuthenticator(): String? {
        val uid = SessionManager.currentUserId
        if (uid < 0) return null
        val secret = Security.randomTotpSecret()
        db.writableDatabase.update(
            "users",
            ContentValues().apply { put("totp_secret", secret) },
            "id=?",
            arrayOf(uid.toString())
        )
        refresh()
        return authenticatorUri()
    }

    private fun loadTotpSecret(uid: Long): String? =
        db.readableDatabase.rawQuery("SELECT totp_secret FROM users WHERE id=?", arrayOf(uid.toString())).use { c ->
            if (!c.moveToFirst()) return null
            val s = c.getString(0)
            s?.ifEmpty { null }
        }

    // ---------------- 存取款 ----------------

    private fun checkPaymentPassword(uid: Long, password: String): Boolean {
        return db.readableDatabase.rawQuery(
            "SELECT pass_salt, pass_hash FROM users WHERE id=?", arrayOf(uid.toString())
        ).use { c ->
            if (!c.moveToFirst()) return false
            Security.verifyPassword(password, c.getString(0), c.getString(1))
        }
    }

    /** 存入。需通过「支付密码 + 微软验证器动态码」双重校验。 */
    fun deposit(
        amountFen: Long,
        term: TermType,
        periodMonths: Int,
        note: String,
        totpCode: String,
        paymentPassword: String
    ): OpResult {
        val uid = SessionManager.currentUserId
        if (uid < 0) return OpResult.Err("NOUSER", "未登录")
        if (amountFen <= 0) return OpResult.Err("AMOUNT", "金额必须大于 0")
        if (!checkPaymentPassword(uid, paymentPassword)) return OpResult.Err("PASS", "支付密码错误")
        val secret = loadTotpSecret(uid)
        if (secret.isNullOrEmpty()) return OpResult.Err("TOTP", "请先绑定微软验证器")
        if (!Totp.verify(secret, totpCode.trim())) return OpResult.Err("TOTP", "验证器动态码错误")

        val now = System.currentTimeMillis()
        val mature = if (term == TermType.FIXED) addMonths(now, periodMonths) else 0L
        db.writableDatabase.insert(
            "deposits",
            null,
            ContentValues().apply {
                put("user_id", uid)
                put("amount_fen", amountFen)
                put("term", term.name)
                put("period_months", if (term == TermType.FIXED) periodMonths else 0)
                put("start_ts", now)
                put("mature_ts", mature)
                put("converted", 0)
                put("note", note.trim())
            }
        )
        val desc = if (term == TermType.FIXED) "死期 ${periodMonths} 个月" else "活期"
        insertTxn(uid, "DEPOSIT", amountFen, now, null, "$desc${note.trim().let { if (it.isEmpty()) "" else "（$it）" }}")
        refresh()
        return OpResult.Ok("存入成功")
    }

    /** 取出。只能取活期可用部分，且需通过「支付密码 + 微软验证器动态码」双重校验。 */
    fun withdraw(amountFen: Long, totpCode: String, paymentPassword: String): OpResult {
        val uid = SessionManager.currentUserId
        if (uid < 0) return OpResult.Err("NOUSER", "未登录")
        if (amountFen <= 0) return OpResult.Err("AMOUNT", "金额必须大于 0")
        if (!checkPaymentPassword(uid, paymentPassword)) return OpResult.Err("PASS", "支付密码错误")
        val secret = loadTotpSecret(uid)
        if (secret.isNullOrEmpty()) return OpResult.Err("TOTP", "请先绑定微软验证器")
        if (!Totp.verify(secret, totpCode.trim())) return OpResult.Err("TOTP", "验证器动态码错误")
        val demand = computeBalances(uid).demandFen
        if (amountFen > demand) {
            return OpResult.Err("OVER", "超出活期可用余额（死期未到期的部分不可取出）")
        }
        insertTxn(uid, "WITHDRAW", amountFen, System.currentTimeMillis(), null, "活期取出")
        refresh()
        return OpResult.Ok("取出成功")
    }

    // ---------------- 快捷交易（指纹确认，免双因子） ----------------

    /** 开启快捷交易：需「支付密码 + 验证器动态码」双重授权，成功后 QuickTxn.enable()。 */
    fun authorizeQuickTxn(paymentPassword: String, totpCode: String): OpResult {
        val uid = SessionManager.currentUserId
        if (uid < 0) return OpResult.Err("NOUSER", "未登录")
        if (!checkPaymentPassword(uid, paymentPassword)) return OpResult.Err("PASS", "支付密码错误")
        val secret = loadTotpSecret(uid)
        if (secret.isNullOrEmpty()) return OpResult.Err("TOTP", "请先绑定微软验证器")
        if (!Totp.verify(secret, totpCode.trim())) return OpResult.Err("TOTP", "验证器动态码错误")
        QuickTxn.enable()
        return OpResult.Ok("快捷交易已开启")
    }

    /** 快捷存入（已授权，免双因子）：直接记账。 */
    fun quickDeposit(amountFen: Long, term: TermType, periodMonths: Int, note: String): OpResult {
        if (!QuickTxn.enabled.value) return OpResult.Err("OFF", "快捷交易未开启")
        val uid = SessionManager.currentUserId
        if (uid < 0) return OpResult.Err("NOUSER", "未登录")
        if (amountFen <= 0) return OpResult.Err("AMOUNT", "金额必须大于 0")
        val now = System.currentTimeMillis()
        val mature = if (term == TermType.FIXED) addMonths(now, periodMonths) else 0L
        db.writableDatabase.insert(
            "deposits", null,
            ContentValues().apply {
                put("user_id", uid)
                put("amount_fen", amountFen)
                put("term", term.name)
                put("period_months", if (term == TermType.FIXED) periodMonths else 0)
                put("start_ts", now)
                put("mature_ts", mature)
                put("converted", 0)
                put("note", note.trim())
            }
        )
        val desc = if (term == TermType.FIXED) "死期 ${periodMonths} 个月" else "活期"
        val notePart = note.trim().let { if (it.isEmpty()) "" else "（$it）" }
        insertTxn(uid, "DEPOSIT", amountFen, now, null, "$desc（指纹）$notePart")
        refresh()
        return OpResult.Ok("已存入（指纹）")
    }

    /** 快捷取出（已授权，免双因子）：仅限活期可用部分。 */
    fun quickWithdraw(amountFen: Long): OpResult {
        if (!QuickTxn.enabled.value) return OpResult.Err("OFF", "快捷交易未开启")
        val uid = SessionManager.currentUserId
        if (uid < 0) return OpResult.Err("NOUSER", "未登录")
        if (amountFen <= 0) return OpResult.Err("AMOUNT", "金额必须大于 0")
        if (amountFen > computeBalances(uid).demandFen) {
            return OpResult.Err("OVER", "超出活期可用余额（死期未到期部分不可取出）")
        }
        insertTxn(uid, "WITHDRAW", amountFen, System.currentTimeMillis(), null, "活期取出（指纹）")
        refresh()
        return OpResult.Ok("已取出（指纹）")
    }

    // ---------------- 备份（加密 JSON 导出 / 导入） ----------------

    /** 导出当前用户全部数据为「加密 JSON 字符串」。密钥由支付密码派生；未登录返回空串。 */
    fun exportBackup(password: String): String {
        val uid = SessionManager.currentUserId
        if (uid < 0) return ""
        val user = db.readableDatabase.rawQuery(
            "SELECT username, COALESCE(email,''), COALESCE(totp_secret,''), pass_salt, pass_hash, COALESCE(goal_fen,0) FROM users WHERE id=?",
            arrayOf(uid.toString())
        ).use { c ->
            if (!c.moveToFirst()) return ""
            BackupUser(
                username = c.getString(0),
                email = c.getString(1),
                totpSecret = c.getString(2),
                passSalt = c.getString(3),
                passHash = c.getString(4),
                goalFen = c.getLong(5)
            )
        }
        val body = Backup.writeBody(user, loadDeposits(uid), loadTransactions(uid))
        return Backup.encryptExport(body, password)
    }

    /** 导入「加密 JSON 字符串」。解密后用支付密码，成功后切换会话到导入的账号。 */
    fun importBackup(encryptedJson: String, password: String): OpResult {
        val bodyJson: String = try {
            Backup.decryptImport(encryptedJson, password)
        } catch (e: Exception) {
            return OpResult.Err("DECRYPT", "解密失败：支付密码错误或文件已损坏")
        }
        val parsed: BackupParsed = try {
            Backup.readBody(bodyJson)
        } catch (e: Exception) {
            return OpResult.Err("FORMAT", "备份内容解析失败")
        }
        val u = parsed.user
        if (u.username.isBlank()) return OpResult.Err("FORMAT", "备份缺少用户名")

        val wdb = db.writableDatabase
        val existing = wdb.rawQuery("SELECT id FROM users WHERE username=?", arrayOf(u.username)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        }
        val uid: Long
        wdb.beginTransaction()
        try {
            uid = if (existing > 0) {
                wdb.delete("deposits", "user_id=?", arrayOf(existing.toString()))
                wdb.delete("transactions", "user_id=?", arrayOf(existing.toString()))
                wdb.update(
                    "users",
                    ContentValues().apply {
                        put("email", u.email)
                        put("totp_secret", u.totpSecret)
                        put("pass_salt", u.passSalt)
                        put("pass_hash", u.passHash)
                        put("goal_fen", u.goalFen)
                    },
                    "id=?", arrayOf(existing.toString())
                )
                existing
            } else {
                wdb.insert(
                    "users", null,
                    ContentValues().apply {
                        put("username", u.username)
                        put("email", u.email)
                        put("pass_salt", u.passSalt)
                        put("pass_hash", u.passHash)
                        put("totp_secret", u.totpSecret)
                        put("goal_fen", u.goalFen)
                        put("created_at", System.currentTimeMillis())
                    }
                )
            }
            for (d in parsed.deposits) {
                wdb.insert(
                    "deposits", null,
                    ContentValues().apply {
                        put("user_id", uid)
                        put("amount_fen", d.amountFen)
                        put("term", d.term.name)
                        put("period_months", d.periodMonths)
                        put("start_ts", d.startTs)
                        put("mature_ts", d.matureTs)
                        put("converted", if (d.converted) 1 else 0)
                        put("note", d.note)
                    }
                )
            }
            for (t in parsed.transactions) {
                wdb.insert(
                    "transactions", null,
                    ContentValues().apply {
                        put("user_id", uid)
                        put("kind", t.kind.name)
                        put("amount_fen", t.amountFen)
                        put("ts", t.ts)
                        putNull("ref_deposit")
                        put("note", t.note)
                    }
                )
            }
            wdb.setTransactionSuccessful()
        } finally {
            wdb.endTransaction()
        }

        SessionManager.persist(context, uid)
        AppLock.unlock()
        refresh()
        return OpResult.Ok("已导入「${u.username}」：${parsed.deposits.size} 笔存单 / ${parsed.transactions.size} 笔流水")
    }

    // ---------------- 数据加载与余额 ----------------

    fun refresh() {
        val uid = SessionManager.currentUserId
        if (uid < 0) {
            uiState.update { UiState() }
            return
        }
        autoConvertMatured(uid)
        val balances = computeBalances(uid)
        val deposits = loadDeposits(uid)
        val txns = loadTransactions(uid)
        val row = loadUser(uid) ?: return
        uiState.update {
            UiState(
                loggedIn = true,
                username = row.username,
                totpConfigured = row.totpSecret.isNotBlank(),
                snapshot = VaultSnapshot(balances, deposits, txns, goalFen = loadGoal(uid))
            )
        }
    }

    private fun loadUser(uid: Long): UserRow? {
        return db.readableDatabase.rawQuery(
            "SELECT id, username, COALESCE(totp_secret,''), pass_salt, pass_hash FROM users WHERE id=?",
            arrayOf(uid.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            UserRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
        }
    }

    private fun computeBalances(uid: Long): VaultBalances {
        val now = System.currentTimeMillis()
        val db = this.db.readableDatabase
        var demandDeposits = 0L
        db.rawQuery(
            "SELECT COALESCE(SUM(amount_fen),0) FROM deposits WHERE user_id=? AND (term='DEMAND' OR (term='FIXED' AND converted=1))",
            arrayOf(uid.toString())
        ).use { c -> if (c.moveToFirst()) demandDeposits = c.getLong(0) }

        var fixedLocked = 0L
        db.rawQuery(
            "SELECT COALESCE(SUM(amount_fen),0) FROM deposits WHERE user_id=? AND term='FIXED' AND converted=0 AND mature_ts>?",
            arrayOf(uid.toString(), now.toString())
        ).use { c -> if (c.moveToFirst()) fixedLocked = c.getLong(0) }

        var withdrawn = 0L
        db.rawQuery(
            "SELECT COALESCE(SUM(amount_fen),0) FROM transactions WHERE user_id=? AND kind='WITHDRAW'",
            arrayOf(uid.toString())
        ).use { c -> if (c.moveToFirst()) withdrawn = c.getLong(0) }

        val demandFen = demandDeposits - withdrawn
        return VaultBalances(totalFen = demandFen + fixedLocked, demandFen = demandFen, fixedLockedFen = fixedLocked)
    }

    /** 将已到期的死期存单自动转成活期，并记一条「转活期」流水。 */
    private fun autoConvertMatured(uid: Long) {
        val now = System.currentTimeMillis()
        val wdb = db.writableDatabase
        val ids = ArrayList<Long>()
        wdb.rawQuery(
            "SELECT id FROM deposits WHERE user_id=? AND term='FIXED' AND converted=0 AND mature_ts<=?",
            arrayOf(uid.toString(), now.toString())
        ).use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
        if (ids.isEmpty()) return
        for (id in ids) {
            wdb.update("deposits", ContentValues().apply { put("converted", 1) }, "id=?", arrayOf(id.toString()))
            insertTxn(uid, "CONVERT", 0, now, id, "死期到期，自动转活期")
        }
    }

    private fun loadDeposits(uid: Long): List<Deposit> {
        val list = ArrayList<Deposit>()
        db.readableDatabase.rawQuery(
            "SELECT id, amount_fen, term, period_months, start_ts, mature_ts, converted, note FROM deposits WHERE user_id=? ORDER BY start_ts DESC",
            arrayOf(uid.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val termName = c.getString(2)
                list.add(
                    Deposit(
                        id = c.getLong(0),
                        amountFen = c.getLong(1),
                        term = TermType.valueOf(termName),
                        periodMonths = c.getInt(3),
                        startTs = c.getLong(4),
                        matureTs = c.getLong(5),
                        converted = c.getInt(6) == 1,
                        note = c.getString(7) ?: ""
                    )
                )
            }
        }
        return list
    }

    private fun loadTransactions(uid: Long): List<Transaction> {
        val list = ArrayList<Transaction>()
        db.readableDatabase.rawQuery(
            "SELECT id, kind, amount_fen, ts, note FROM transactions WHERE user_id=? ORDER BY ts DESC, id DESC",
            arrayOf(uid.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val kindName = c.getString(1)
                val kind = try {
                    Transaction.Kind.valueOf(kindName)
                } catch (e: IllegalArgumentException) {
                    Transaction.Kind.DEPOSIT
                }
                list.add(Transaction(c.getLong(0), kind, c.getLong(2), c.getLong(3), c.getString(4) ?: ""))
            }
        }
        return list
    }

    private fun insertTxn(uid: Long, kind: String, amountFen: Long, ts: Long, refDeposit: Long?, note: String) {
        db.writableDatabase.insert(
            "transactions",
            null,
            ContentValues().apply {
                put("user_id", uid)
                put("kind", kind)
                put("amount_fen", amountFen)
                put("ts", ts)
                if (refDeposit != null) put("ref_deposit", refDeposit) else putNull("ref_deposit")
                put("note", note)
            }
        )
    }

    private fun addMonths(fromMs: Long, months: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMs; add(Calendar.MONTH, months) }
        return cal.timeInMillis
    }
}
