package com.qiyu.safe.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.qiyu.safe.data.AppLock
import com.qiyu.safe.data.CrashLog
import com.qiyu.safe.data.VaultRepository

/**
 * 生物识别解锁。Android 8.0+ 用 BiometricPrompt（指纹/人脸，或设备密码兜底）；
 * 更低版本或无硬件时仅提供支付密码解锁。
 * Android 9（API 28）需要运行时申请 USE_FINGERPRINT 权限，故先申请再用。
 */
object BiometricHelper {
    /** 生物识别状态码（canAuthenticate 结果）；<28 返回 -100，异常返回 -99。 */
    fun status(ctx: Context): Int {
        if (Build.VERSION.SDK_INT < 28) return -100
        return try {
            BiometricManager.from(ctx).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } catch (e: Throwable) {
            -99
        }
    }

    fun available(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        if (Build.VERSION.SDK_INT == 28 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED
        ) return false
        return status(ctx) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** 一行诊断：版本 · Android API · 生物识别可用性（状态码）。 */
    fun diagnostic(ctx: Context): String {
        val ver = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (e: Throwable) {
            "?"
        }
        val s = status(ctx)
        val ok = if (s == BiometricManager.BIOMETRIC_SUCCESS) "可用" else "不可用"
        return "v$ver · API ${Build.VERSION.SDK_INT} · 生物识别=$ok(状态码$s)"
    }

    /** 描述当前生物识别为何不可用（用于解锁页提示）；可用时返回空串。 */
    fun statusText(ctx: Context): String {
        if (Build.VERSION.SDK_INT < 28) return "系统低于 Android 9，仅支持支付密码解锁"
        val s = status(ctx)
        if (s == -99) return "生物识别初始化失败，请用支付密码解锁"
        return when (s) {
            BiometricManager.BIOMETRIC_SUCCESS -> ""
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "未录入指纹/人脸：请到 系统设置→安全 录入后再用"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "本机无指纹/人脸硬件，请用支付密码解锁"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "生物识别硬件暂不可用，请用支付密码解锁"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "系统需安全更新，请用支付密码解锁"
            else -> "生物识别暂不可用（状态码 $s），请用支付密码解锁"
        }
    }

    fun unlock(
        activity: FragmentActivity,
        ctx: Context,
        onSuccess: () -> Unit,
        onFallback: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT < 28) {
            onFallback()
            return
        }
        try {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFallback()
                    onError?.invoke("生物识别失败(code=$errorCode)：$errString")
                }

                override fun onAuthenticationFailed() {}
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(ctx), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁保险柜")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(info)
        } catch (e: Throwable) {
            android.util.Log.e("BiometricHelper", "biometric unlock failed", e)
            onFallback()
            onError?.invoke("生物识别异常(${e::class.java.simpleName})：${e.message}")
        }
    }

    /** 通用生物识别确认（快捷交易）：仅指纹/面容（BIOMETRIC_STRONG），成功 onSuccess，失败/取消 onFail。 */
    fun confirm(
        activity: FragmentActivity,
        ctx: Context,
        onSuccess: () -> Unit,
        onFail: (String) -> Unit
    ) {
        if (!available(ctx)) {
            onFail(statusText(ctx).ifEmpty { "生物识别不可用" })
            return
        }
        try {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFail("生物识别失败(code=$errorCode)：$errString")
                }

                override fun onAuthenticationFailed() {}
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(ctx), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("确认交易")
                .setSubtitle("验证指纹 / 面容以完成存取款")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("取消")
                .build()
            prompt.authenticate(info)
        } catch (e: Throwable) {
            android.util.Log.e("BiometricHelper", "biometric confirm failed", e)
            onFail("生物识别异常：${e.message}")
        }
    }
}

/** 解锁页：生物识别优先（含权限申请），支付密码兜底。解锁后调用 AppLock.unlock()。 */
@Composable
fun LockScreen(activity: FragmentActivity) {
    val ctx = activity
    var bioOk by remember { mutableStateOf(BiometricHelper.available(ctx)) }
    var pw by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val crash = remember { CrashLog.last(ctx) }
    val clipboard = LocalClipboardManager.current

    // 仅 Android 9（API 28）需运行时申请指纹权限；10+ 是普通权限，清单声明即自动授予
    val requestFingerprint = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            bioOk = BiometricHelper.available(ctx)
        } else {
            msg = "未授予指纹权限，请用支付密码解锁"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔒", style = MaterialTheme.typography.displayMedium)
        Text("保险柜已锁定", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            "使用生物识别或支付密码解锁",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (bioOk) {
            Button(
                onClick = {
                    BiometricHelper.unlock(
                        activity = activity,
                        ctx = ctx,
                        onSuccess = { AppLock.unlock() },
                        onFallback = { msg = "生物识别失败或已取消，请用下方支付密码" },
                        onError = { msg = it }
                    )
                },
                modifier = Modifier.fillMaxWidth(0.72f).padding(top = 24.dp)
            ) { Text("指纹 / 面容 / 设备密码 解锁") }
        } else {
            Text(
                text = BiometricHelper.statusText(ctx),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // Android 9 上指纹权限未授予时，允许手动申请一次
        if (!bioOk && Build.VERSION.SDK_INT == 28) {
            Button(
                onClick = { requestFingerprint.launch(Manifest.permission.USE_FINGERPRINT) },
                modifier = Modifier.fillMaxWidth(0.72f).padding(top = 16.dp)
            ) { Text("申请使用指纹") }
        }

        OutlinedTextField(
            value = pw,
            onValueChange = { pw = it; msg = null },
            label = { Text("支付密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.72f).padding(top = 16.dp)
        )
        Button(
            onClick = {
                if (VaultRepository.verifyPaymentPassword(pw)) AppLock.unlock() else msg = "支付密码错误"
            },
            modifier = Modifier.fillMaxWidth(0.72f).padding(top = 8.dp)
        ) { Text("支付密码解锁") }
        msg?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            text = BiometricHelper.diagnostic(ctx),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp)
        )
        if (crash != null) {
            Text("检测到上次闪退：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp))
            Text(crash.take(300), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(crash)); msg = "已复制崩溃日志，请发给开发者" },
                modifier = Modifier.fillMaxWidth(0.72f).padding(top = 8.dp)
            ) { Text("复制崩溃日志发给我") }
        }
    }
}
