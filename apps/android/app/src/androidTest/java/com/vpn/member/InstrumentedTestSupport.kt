package com.vpn.member

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.vpn.member.data.local.AppPreferences
import com.vpn.member.data.local.TokenStore
import com.vpn.member.vpn.PrivacyBaselineMigrator
import com.vpn.member.vpn.PrivacyOnboardingStore
import com.vpn.member.vpn.VpnSessionSnapshot
import com.vpn.member.vpn.VpnSessionStore
import com.vpn.member.vpn.ConnectionScenario
import com.vpn.member.vpn.AppRouteMode
import com.vpn.member.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object InstrumentedTestSupport {
    private const val LOCAL_EMAIL = "test1@example.com"
    private const val LOCAL_PASSWORD = "test123456"
    private const val REMOTE_EMAIL = "luban7733@gmail.com"
    private const val REMOTE_PASSWORD = "123456"

    fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private fun resolveTestCredentials(): Pair<String, String> {
        val base = BuildConfig.API_BASE_URL
        return if (base.contains("192.229.87.112")) {
            REMOTE_EMAIL to REMOTE_PASSWORD
        } else {
            LOCAL_EMAIL to LOCAL_PASSWORD
        }
    }

    fun clearAppState(context: Context = appContext()) {
        context.getSharedPreferences("vpn_member_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("privacy_onboarding", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("vpn_session_store", Context.MODE_PRIVATE).edit().clear().apply()
        (context.applicationContext as? VpnMemberApp)?.repository?.let { repo ->
            runBlocking { repo.logout() }
        }
        TokenStore(context).clearSession()
        TokenStore(context).saveNode(null)
        TokenStore(context).saveRegion(null)
        PrivacyBaselineMigrator.migrateIfNeeded(AppPreferences(context))
    }

    /** 仪器化测试前置授权，避免系统通知/VPN 弹窗阻塞 UI 自动化。 */
    fun grantInstrumentedPermissions(context: Context = appContext()) {
        val pkg = context.packageName
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            runCatching {
                shell.grantRuntimePermission(pkg, android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "appops set $pkg ACTIVATE_VPN allow",
            )
        }
    }

    fun prepareLoggedInSession(
        context: Context = appContext(),
        completePrivacyOnboarding: Boolean = true,
    ): String {
        val (email, password) = resolveTestCredentials()
        val app = context.applicationContext as VpnMemberApp
        val auth =
            runBlocking {
                app.repository.login(email, password)
            }
        val prefs = AppPreferences(context)
        prefs.setPrivacyAccepted()
        if (completePrivacyOnboarding) {
            PrivacyOnboardingStore(context).markCompleted(skippedSystemHardening = true)
        }
        app.repository.saveNode(null)
        return auth.token
    }

    /** 模拟覆盖安装后残留的「上次已连接」会话快照（Service 未运行）。 */
    fun seedStaleVpnSessionSnapshot(context: Context = appContext()) {
        VpnSessionStore(context).saveSnapshot(
            VpnSessionSnapshot(
                wasUserConnected = true,
                nodeName = "新加坡1",
                region = "sg",
                profile = ConnectionScenario.PROFILE_OVERSEAS_WEAK,
                routeMode = AppRouteMode.FULL,
                connectionScenario = ConnectionScenario.AUTO,
            ),
        )
    }

    fun launchMainActivity(context: Context = appContext()) {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    fun loginTestUser(apiBase: String = "http://10.0.2.2:48080"): String {
        val (email, password) = resolveTestCredentials()
        val client = OkHttpClient()
        val body =
            """{"email":"$email","password":"$password"}"""
                .toRequestBody("application/json".toMediaType())
        val request =
            Request.Builder()
                .url("$apiBase/api/v1/auth/login")
                .post(body)
                .build()
        val response = client.newCall(request).execute()
        assertTrue(
            "本地 API 登录失败（请确认 Docker vpn-api 在 48080 运行）: HTTP ${response.code}",
            response.isSuccessful,
        )
        val json = JSONObject(response.body!!.string())
        assertEquals(200, json.getInt("code"))
        return json.getJSONObject("data").getString("token")
    }
}
