package com.qiyu.safe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.qiyu.safe.data.AppLock
import com.qiyu.safe.data.VaultRepository

/** 应用入口：未登录显示登录/注册页，已登录显示主壳（底部导航）。 */
@Composable
fun VaultApp(activity: FragmentActivity) {
    val state = VaultRepository.uiState.collectAsState()
    val locked = AppLock.state.collectAsState()
    when {
        !state.value.loggedIn -> AuthScreen()
        locked.value -> LockScreen(activity)
        else -> VaultShell(username = state.value.username)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VaultShell(username: String) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("存钱保险柜 · $username", style = MaterialTheme.typography.titleLarge)
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .height(64.dp)
            ) {
                BottomTab("首页", Icons.Filled.Home, tab == 0, Modifier.weight(1f)) { tab = 0 }
                BottomTab("流水", Icons.Filled.Folder, tab == 1, Modifier.weight(1f)) { tab = 1 }
                BottomTab("验证器", Icons.Filled.Security, tab == 2, Modifier.weight(1f)) { tab = 2 }
                BottomTab("设置", Icons.Filled.Settings, tab == 3, Modifier.weight(1f)) { tab = 3 }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(onOpenAuthenticator = { tab = 2 })
                1 -> HistoryScreen()
                2 -> AuthenticatorScreen()
                3 -> SettingsScreen()
                else -> {}
            }
        }
    }
}

@Composable
private fun BottomTab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
