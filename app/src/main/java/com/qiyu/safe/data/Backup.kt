package com.qiyu.safe.data

import android.util.Base64
import com.qiyu.safe.security.Security
import org.json.JSONArray
import org.json.JSONObject

/** 备份里的用户部分（不含自增 id，导入时重新生成）。 */
data class BackupUser(
    val username: String,
    val email: String,
    val totpSecret: String,
    val passSalt: String,
    val passHash: String,
    val goalFen: Long
)

/** 解析出的一整份备份数据。 */
data class BackupParsed(
    val user: BackupUser,
    val deposits: List<Deposit>,
    val transactions: List<Transaction>
)

/**
 * 加密备份编解码。
 * 外层是明文 JSON（描述加解密参数 + 密文），其中 `data` 字段是 AES-GCM 密文（Base64）。
 * 未输入导出时使用的支付密码时，`data` 无法解密。
 */
object Backup {
    const val MAGIC = "savings-vault-export"
    const val VERSION = 1

    /** 把某用户的数据序列化为「待加密」的明文 JSON。 */
    fun writeBody(user: BackupUser, deposits: List<Deposit>, transactions: List<Transaction>): String {
        val u = JSONObject()
            .put("username", user.username)
            .put("email", user.email)
            .put("totp_secret", user.totpSecret)
            .put("pass_salt", user.passSalt)
            .put("pass_hash", user.passHash)
            .put("goal_fen", user.goalFen)

        val darr = JSONArray()
        for (d in deposits) {
            darr.put(
                JSONObject()
                    .put("amount_fen", d.amountFen)
                    .put("term", d.term.name)
                    .put("period_months", d.periodMonths)
                    .put("start_ts", d.startTs)
                    .put("mature_ts", d.matureTs)
                    .put("converted", if (d.converted) 1 else 0)
                    .put("note", d.note)
            )
        }
        val tarr = JSONArray()
        for (t in transactions) {
            tarr.put(
                JSONObject()
                    .put("kind", t.kind.name)
                    .put("amount_fen", t.amountFen)
                    .put("ts", t.ts)
                    .put("note", t.note)
            )
        }
        return JSONObject()
            .put("user", u)
            .put("deposits", darr)
            .put("transactions", tarr)
            .toString()
    }

    /** 反序列化被加密前的 body 明文 JSON。 */
    fun readBody(json: String): BackupParsed {
        val root = JSONObject(json)
        val u = root.getJSONObject("user")
        val user = BackupUser(
            username = u.getString("username"),
            email = u.optString("email"),
            totpSecret = u.optString("totp_secret"),
            passSalt = u.optString("pass_salt"),
            passHash = u.optString("pass_hash"),
            goalFen = u.optLong("goal_fen", 0L)
        )
        val deposits = ArrayList<Deposit>()
        val darr = root.optJSONArray("deposits") ?: JSONArray()
        for (i in 0 until darr.length()) {
            val o = darr.getJSONObject(i)
            val term = try {
                TermType.valueOf(o.optString("term", "DEMAND"))
            } catch (e: IllegalArgumentException) {
                TermType.DEMAND
            }
            deposits.add(
                Deposit(
                    id = 0L,
                    amountFen = o.optLong("amount_fen", 0L),
                    term = term,
                    periodMonths = o.optInt("period_months", 0),
                    startTs = o.optLong("start_ts", 0L),
                    matureTs = o.optLong("mature_ts", 0L),
                    converted = o.optInt("converted", 0) == 1,
                    note = o.optString("note")
                )
            )
        }
        val transactions = ArrayList<Transaction>()
        val tarr = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until tarr.length()) {
            val o = tarr.getJSONObject(i)
            val kind = try {
                Transaction.Kind.valueOf(o.optString("kind", "DEPOSIT"))
            } catch (e: IllegalArgumentException) {
                Transaction.Kind.DEPOSIT
            }
            transactions.add(
                Transaction(
                    id = 0L,
                    kind = kind,
                    amountFen = o.optLong("amount_fen", 0L),
                    ts = o.optLong("ts", 0L),
                    note = o.optString("note")
                )
            )
        }
        return BackupParsed(user, deposits, transactions)
    }

    /** 生成加密的导出文件内容（字符串）。密钥由支付密码 + 随机盐派生。 */
    fun encryptExport(bodyJson: String, password: String): String {
        val salt = Security.randomBytes(Security.AES_SALT_BYTES)
        val iv = Security.randomBytes(Security.AES_IV_BYTES)
        val key = Security.deriveAesKey(password, salt)
        val cipher = Security.encryptAesGcm(bodyJson, key, iv)
        return JSONObject()
            .put("magic", MAGIC)
            .put("version", VERSION)
            .put("cipher", "AES-256-GCM")
            .put("kdf", "PBKDF2WithHmacSHA1")
            .put("kdfIterations", Security.PBKDF2_ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("exportedAt", System.currentTimeMillis())
            .put("data", Base64.encodeToString(cipher, Base64.NO_WRAP))
            .toString()
    }

    /** 解析并解密导出文件。@return 被加密前的 body 明文 JSON。密码错误/文件损坏时抛出异常。 */
    fun decryptImport(fileJson: String, password: String): String {
        val obj = JSONObject(fileJson)
        if (obj.optString("magic") != MAGIC) throw IllegalArgumentException("不是有效的保险柜备份文件")
        val iterations = obj.optInt("kdfIterations", Security.PBKDF2_ITERATIONS)
        val salt = Base64.decode(obj.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(obj.getString("data"), Base64.NO_WRAP)
        val key = Security.deriveAesKey(password, salt, iterations)
        return Security.decryptAesGcm(data, key, iv)
    }
}
