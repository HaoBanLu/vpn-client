package com.vpn.member.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vpn.member.vpn.AppRouteMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenStore(context: Context) {
    private val prefs: SharedPreferences = createPreferences(context.applicationContext)
    private val _sessionActive = MutableStateFlow(!prefs.getString(KEY_JWT, null).isNullOrBlank())
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    fun saveJwt(token: String) {
        prefs.edit().putString(KEY_JWT, token).apply()
        _sessionActive.value = true
    }

    /** 测试/关键路径：确保 JWT 已落盘后再发起鉴权请求。 */
    fun ensureJwtPersisted() {
        prefs.edit().putString(KEY_JWT, prefs.getString(KEY_JWT, null)).commit()
    }

    fun getJwt(): String? = prefs.getString(KEY_JWT, null)

    /** 仅清除登录会话，保留节点偏好与「记住账号密码」等本地设置。 */
    fun clearSession() {
        prefs.edit().remove(KEY_JWT).apply()
        _sessionActive.value = false
    }

    /** @deprecated 请使用 [clearSession]；保留供极少数需全量清空的场景。 */
    fun clear() {
        prefs.edit().clear().apply()
        _sessionActive.value = false
    }

    fun isRememberLoginEnabled(): Boolean = prefs.getBoolean(KEY_REMEMBER_LOGIN, true)

    fun getSavedLoginEmail(): String? = prefs.getString(KEY_SAVED_EMAIL, null)

    fun getSavedLoginPassword(): String? = prefs.getString(KEY_SAVED_PASSWORD, null)

    fun saveLoginCredentials(remember: Boolean, email: String, password: String) {
        val editor = prefs.edit().putBoolean(KEY_REMEMBER_LOGIN, remember)
        if (remember) {
            editor
                .putString(KEY_SAVED_EMAIL, email.trim())
                .putString(KEY_SAVED_PASSWORD, password)
        } else {
            editor
                .remove(KEY_SAVED_EMAIL)
                .remove(KEY_SAVED_PASSWORD)
        }
        editor.apply()
    }

    fun saveRegion(region: String?) {
        if (region.isNullOrBlank()) {
            prefs.edit().remove(KEY_REGION).apply()
        } else {
            prefs.edit().putString(KEY_REGION, region).apply()
        }
    }

    fun getRegion(): String? = prefs.getString(KEY_REGION, null)

    fun saveNode(nodeName: String?) {
        if (nodeName.isNullOrBlank()) {
            prefs.edit().remove(KEY_NODE).apply()
        } else {
            prefs.edit().putString(KEY_NODE, nodeName).apply()
        }
    }

    fun getNode(): String? = prefs.getString(KEY_NODE, null)

    fun saveRouteMode(mode: String) {
        prefs.edit().putString(KEY_ROUTE_MODE, mode).apply()
    }

    fun getRouteMode(): String = prefs.getString(KEY_ROUTE_MODE, AppRouteMode.FULL) ?: AppRouteMode.FULL

    fun saveAppDebugEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_DEBUG_ENABLED, enabled).apply()
    }

    fun isAppDebugEnabled(): Boolean = prefs.getBoolean(KEY_APP_DEBUG_ENABLED, false)

    companion object {
        private const val TAG = "TokenStore"
        private const val SECURE_PREFS_NAME = "vpn_member_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "vpn_member_compat_prefs"
        private const val KEY_JWT = "jwt"
        private const val KEY_REMEMBER_LOGIN = "remember_login"
        private const val KEY_SAVED_EMAIL = "saved_email"
        private const val KEY_SAVED_PASSWORD = "saved_password"
        private const val KEY_REGION = "region"
        private const val KEY_NODE = "node"
        private const val KEY_ROUTE_MODE = "route_mode"
        private const val KEY_APP_DEBUG_ENABLED = "app_debug_enabled"

        /**
         * 兼容部分 ROM 的 AndroidKeyStore/安全组件异常：
         * 优先使用 EncryptedSharedPreferences，失败时降级到普通 SharedPreferences，避免启动崩溃。
         */
        private fun createPreferences(context: Context): SharedPreferences {
            return runCatching {
                EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_NAME,
                    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.onFailure { err ->
                Log.e(TAG, "secure prefs unavailable, fallback to compat prefs", err)
            }.getOrElse {
                context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }
}
