package com.vpn.kuayun

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vpn.kuayun.vpn.AppStatusNotification

/**
 * 浅色 WebView + edge-to-edge：深色状态栏图标。
 * 用 content 根布局原生 padding 避让系统栏（不依赖 WebView env/JS，真机更稳），
 * 同时把 inset 写入 CSS 变量供页面微调。
 */
class MainActivity : TauriActivity() {
    private var lastTopPx = -1
    private var lastBottomPx = -1
    private var cssInjected = false
    private var webViewPolished = false
    private var notificationAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val root = window.decorView.findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            applySystemBarPadding(v, bars.top, bars.bottom)
            // 原生已 padding：CSS 变量置 0，避免与 env 叠成双倍空白
            injectSafeAreaCss(0f, 0f, force = !cssInjected)
            WindowInsetsCompat.CONSUMED
        }
        root.requestApplyInsets()

        askNotificationPermission()
        AppStatusNotification.showIdle(this)

        listOf(300L, 1000L, 2500L).forEach { delayMs ->
            root.postDelayed({
                injectSafeAreaCss(0f, 0f, force = !cssInjected)
                polishWebView()
            }, delayMs)
        }
    }

    override fun onResume() {
        super.onResume()
        AppStatusNotification.showIdle(this)
        polishWebView()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AppStatusNotification.PERMISSION_REQUEST) {
            AppStatusNotification.showIdle(this)
        }
    }

    private fun askNotificationPermission() {
        if (notificationAsked) return
        notificationAsked = true
        AppStatusNotification.requestPostPermission(this)
    }

    private fun polishWebView() {
        val webView = findWebView(window.decorView) ?: return
        if (!webViewPolished) {
            webView.settings.apply {
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                textZoom = 100
            }
            webView.overScrollMode = View.OVER_SCROLL_NEVER
            webView.isHapticFeedbackEnabled = false
            webView.isLongClickable = false
            webView.setOnLongClickListener { true }
            webViewPolished = true
        }
    }

    private fun applySystemBarPadding(view: View, topPx: Int, bottomPx: Int) {
        if (topPx == lastTopPx && bottomPx == lastBottomPx) return
        lastTopPx = topPx
        lastBottomPx = bottomPx
        view.setPadding(view.paddingLeft, topPx, view.paddingRight, bottomPx)
    }

    private fun injectSafeAreaCss(topDp: Float, bottomDp: Float, force: Boolean) {
        val webView = findWebView(window.decorView) ?: return
        if (cssInjected && !force) return
        val top = "%.1f".format(topDp)
        val bottom = "%.1f".format(bottomDp)
        val js =
            """
            (function(){
              var r=document.documentElement;
              if(!r||!r.style) return;
              r.style.setProperty('--android-safe-top','${top}px');
              r.style.setProperty('--android-safe-bottom','${bottom}px');
            })();
            """.trimIndent()
        webView.evaluateJavascript(js) {
            cssInjected = true
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findWebView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
