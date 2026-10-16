package com.qiyu.safe

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.qiyu.safe.data.AppLock
import com.qiyu.safe.data.CrashLog
import com.qiyu.safe.data.QuickTxn
import com.qiyu.safe.data.VaultRepository
import com.qiyu.safe.ui.VaultApp
import com.qiyu.safe.ui.theme.VaultTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        AppLock.init(this)
        QuickTxn.init(this)
        VaultRepository.init(this)
        // 冷启动：已有登录会话且启用自动锁定时，需重新解锁
        if (VaultRepository.uiState.value.loggedIn && AppLock.autoLockEnabled.value) AppLock.lock()
        setContent {
            VaultTheme {
                VaultApp(activity = this)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 切后台即上锁（用户可在设置里关闭自动锁定），回来需生物识别 / 支付密码
        if (VaultRepository.uiState.value.loggedIn && AppLock.autoLockEnabled.value) AppLock.lock()
    }
}
