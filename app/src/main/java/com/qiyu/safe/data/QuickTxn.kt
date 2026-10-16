package com.qiyu.safe.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 「快捷交易」开关。开启后，存入 / 取出可用指纹或面容一步确认（免去支付密码 + 验证器码）。
 * 开启需一次性「支付密码 + 微软验证器动态码」双重授权（见 [VaultRepository.authorizeQuickTxn]）。
 * 切换会话（注册 / 登录 / 登出）会重置为关闭。
 */
object QuickTxn {
    private const val PREFS = "vault_quick"
    private const val KEY_ON = "quick_txn_enabled"
    private var prefs: SharedPreferences? = null

    val enabled = MutableStateFlow(false)

    /** 加载持久化配置；App 启动早期调用一次（之后幂等）。 */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            enabled.value = prefs!!.getBoolean(KEY_ON, false)
        }
    }

    /** 开启（应由双重授权成功后调用）。 */
    fun enable() {
        enabled.value = true
        prefs?.edit()?.putBoolean(KEY_ON, true)?.apply()
    }

    fun disable() {
        enabled.value = false
        prefs?.edit()?.putBoolean(KEY_ON, false)?.apply()
    }
}
