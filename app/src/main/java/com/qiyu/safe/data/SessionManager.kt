package com.qiyu.safe.data

import android.content.Context

/** 当前登录用户会话（内存 + SharedPreferences 持久化，应用重启后保持登录）。 */
object SessionManager {
    private const val PREFS = "vault_session"
    private const val KEY_USER = "current_user_id"

    @Volatile
    var currentUserId: Long = -1L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun restore(context: Context) {
        currentUserId = prefs(context).getLong(KEY_USER, -1L)
    }

    fun persist(context: Context, userId: Long) {
        currentUserId = userId
        prefs(context).edit().putLong(KEY_USER, userId).apply()
    }

    fun clear(context: Context) {
        currentUserId = -1L
        prefs(context).edit().remove(KEY_USER).apply()
    }
}
