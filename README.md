# 存钱保险柜（Savings Vault）

一个离线优先的 Android 储蓄记账应用：在本地记录你的"银行存款"（活期 / 死期）、
提供**双重校验的存取款**、**生物识别应用锁**，并把一切数据保存在设备本地，
**不联网、不上云**。

包名：`com.qiyu.safe` · 目标版本：`v2.2`（`versionCode 16`）

---

## 功能特性

- **注册 / 登录**：以「支付密码」作为主密钥（PBKDF2 加盐哈希，不存明文）。
- **TOTP 动态密码（微软验证器 / Google Authenticator）**：注册即生成专属 TOTP 密钥，
  通过 QR 码扫码绑定；存取款时额外校验 6 位动态码。
- **活期 / 死期存单**：
  - 活期：随时可取。
  - 死期：设定期限（月），到期前锁定、**到期自动转活期**。
- **余额汇总**：总余额、活期可用、死期锁定 分项展示。
- **储蓄目标**：可设定目标金额，主界面展示进度。
- **流水**：存入 / 取出 / 死期转活期 三类流水记录。
- **应用内锁**：切到后台或冷启动后自动锁定，回到 App 需**生物识别（指纹/面容）
  或支付密码**解锁；解锁失败可回退到支付密码输入。
- **崩溃日志**：应用锁页可查看并复制上一次闪退的堆栈，便于排障。
- **加密备份 / 恢复**：在「设置」页导出 / 导入加密 JSON 备份（AES-256-GCM，用支付密码加密），用于换机或重装后迁移数据。
- **快捷交易（指纹存取款）**：在「设置」开启（需一次性「支付密码 + 验证器动态码」双重授权）。开启后，
  存入 / 取出对话框出现「👆 指纹快速存/取」按钮，用指纹或面容一步确认即可完成交易，免双因子输入。
- **自动锁定开关**：「切后台 / 冷启动自动锁定」可在设置里关闭；关闭后回到应用不再要求解锁。

## 安全设计

| 关注点 | 实现 |
| --- | --- |
| 支付密码 | PBKDF2WithHmacSHA1，100,000 次迭代，随机 16 字节盐，明文不落盘（SHA1 兼容各 ROM；SHA256 在部分机型报 InvalidKeySpecException） |
| 双因素 | 存取款必须同时通过「支付密码 + TOTP 动态码」校验 |
| TOTP | 遵循 RFC 6238（SHA1 / 6 位 / 30 秒），校验允许 ±2 个时间步偏差 |
| 应用锁 | 生物识别（`androidx.biometric`，指纹/面容 + 设备密码兜底）+ 支付密码兜底 |
| 数据本地化 | 仅写入应用私有目录 `vault.db`，`allowBackup=false`，无任何网络请求 |
| 数据备份 | 导出/导入的 JSON 经 **AES-256-GCM** 加密，密钥由支付密码经 PBKDF2 派生；密码错误则无法解密 |
| 快捷交易 | 开启需「支付密码 + TOTP」双重授权；之后每笔用 **BIOMETRIC_STRONG** 指纹/面容确认（无设备密码兜底），金额/活期/死期规则仍生效 |
| 金额精度 | 一律以「分」（`Long`）为单位存储，避免浮点误差 |

## 技术栈

- **语言 / UI**：Kotlin 1.9.23 + Jetpack Compose（Material3，BOM 2024.05.00）
- **构建**：Android Gradle Plugin 8.5.2；`compileSdk 34`，`minSdk 24`，`targetSdk 34`
- **数据库**：原生 `SQLiteOpenHelper`（非 Room）
- **关键依赖**：`zxing`（QR 码）、`androidx.biometric`（生物识别）、
  `androidx.fragment`（`BiometricPrompt` 所需的 `FragmentActivity`）

## 目录结构

```
app/src/main/java/com/qiyu/safe/
├── MainActivity.kt          # 入口（FragmentActivity）：装崩溃捕获、恢复会话、启动应用锁
├── data/
│   ├── Models.kt            # 实体：Deposit / Transaction / 余额 / 快照
│   ├── VaultDatabase.kt     # SQLite 建表与升级（DB_VERSION）
│   ├── VaultRepository.kt   # 业务核心：注册/登录/存取款/转活期/余额/目标
│   ├── SessionManager.kt    # 当前登录用户（内存 + SharedPreferences）
│   ├── AppLock.kt           # 应用内锁状态（StateFlow，可开关自动锁定）
│   ├── QuickTxn.kt          # 快捷交易开关（指纹存取款，需双重授权）
│   └── CrashLog.kt         # 未捕获异常 → last_crash.txt
├── security/
│   ├── Security.kt          # Base32 / PBKDF2 / AES-GCM / QR 生成
│   └── Totp.kt              # TOTP 生成与校验
└── ui/
    ├── VaultApp.kt          # 根 Composable：锁定 → 解锁页，否则 → 主界面
    ├── AuthScreen.kt        # 注册 / 登录
    ├── AuthenticatorScreen.kt # 验证器 QR 码绑定
    ├── HomeScreen.kt        # 余额 / 目标 / 存取款
    ├── HistoryScreen.kt     # 流水
    ├── SettingsScreen.kt    # 改密码 / 重绑验证器 / 退出
    ├── LockScreen.kt        # 解锁页（生物识别 + 支付密码兜底）
    └── theme/Theme.kt
```

## 构建

> 项目不含 Gradle wrapper，请确保本机装有 **JDK 17**、**Android SDK（API 34）** 与 **Gradle 8.5+**，
> 或在 Android Studio 中直接打开项目。

```bash
# 生成 debug APK
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 运行 TOTP 等单元测试
gradle :app:testDebugUnitTest
```

本机调试时通过 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装。

## 使用流程

1. 首次启动：注册用户名 + 支付密码 → 自动进入并生成 TOTP 密钥。
2. 在「验证器」页用微软验证器 / Google Authenticator 扫码绑定。
3. 「存入 / 取出」时需同时输入**支付密码**与**验证器 6 位动态码**。
4. 切后台或重启 App 后，回到应用会显示**解锁页**：指纹/面容或支付密码解锁。
5. （可选）在「设置」开启**快捷交易**（需一次双重授权），之后存/取款可直接用指纹确认。

## 隐私

所有数据（账户、存单、流水、目标、会话）均保存在设备本地私有数据库 `vault.db`，
应用不含任何网络权限与上报逻辑。卸载应用即清除全部数据。
