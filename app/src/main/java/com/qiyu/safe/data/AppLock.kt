package com.qiyu.safe.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 应用内锁状态。true = 已锁定（显示解锁页，需生物识别或支付密码）。
 * 「切后台 / 冷启动自动锁定」可由用户在设置里开关；登录、注册、或成功解锁会放开。
 */
object AppLock {
    private const val PREFS = "vault_lock"
    private const val KEY_AUTO = "auto_lock_enabled"
    private var prefs: SharedPreferences? = null

    val state = MutableStateFlow(false)

    /** 是否启用「切后台 / 冷启动自动锁定」，默认开。 */
    val autoLockEnabled = MutableStateFlow(true)

    /** 加载持久化配置；应在 App 启动早期调用一次（之后幂等）。 */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            autoLockEnabled.value = prefs!!.getBoolean(KEY_AUTO, true)
        }
    }

    fun setAutoLock(enabled: Boolean) {
        autoLockEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO, enabled)?.apply()
    }

    fun lock() {
        if (!state.value) state.value = true
    }

    fun unlock() {
        state.value = false
    }
}
