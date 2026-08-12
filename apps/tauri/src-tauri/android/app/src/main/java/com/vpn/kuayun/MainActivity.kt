package com.vpn.kuayun

import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 浅色 WebView + edge-to-edge：强制深色状态栏/导航栏图标，避免白图标压在浅底上看不见。
 * 内容避让由系统 insets（setDecorFitsSystemWindows=true）负责，比依赖 WebView safe-area 更稳。
 */
class MainActivity : TauriActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}
