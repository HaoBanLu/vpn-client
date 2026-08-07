package com.vpn.member.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.lastInvalidationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "last_invalidation",
)

data class PendingInvalidation(
    val title: String,
    val message: String,
    val appCode: String?,
    val timestamp: Long,
)

/** 冷启动登录页补发：持久化上次会话失效原因。 */
class LastInvalidationStore(
    private val context: Context,
) {
    suspend fun save(
        title: String,
        message: String,
        appCode: String?,
    ) {
        context.lastInvalidationDataStore.edit { prefs ->
            prefs[KEY_TITLE] = title
            prefs[KEY_MESSAGE] = message
            prefs[KEY_APP_CODE] = appCode.orEmpty()
            prefs[KEY_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun peek(): PendingInvalidation? =
        context.lastInvalidationDataStore.data.map { prefs ->
            val title = prefs[KEY_TITLE]?.trim().orEmpty()
            val message = prefs[KEY_MESSAGE]?.trim().orEmpty()
            if (title.isBlank() && message.isBlank()) {
                null
            } else {
                PendingInvalidation(
                    title = title.ifBlank { "登录状态已失效" },
                    message = message.ifBlank { "请重新登录" },
                    appCode = prefs[KEY_APP_CODE]?.takeIf { it.isNotBlank() },
                    timestamp = prefs[KEY_TIMESTAMP] ?: 0L,
                )
            }
        }.first()

    suspend fun consume(): PendingInvalidation? {
        val pending = peek()
        if (pending != null) {
            clear()
        }
        return pending
    }

    suspend fun clear() {
        context.lastInvalidationDataStore.edit { it.clear() }
    }

    /** 仪器化/同步测试用。 */
    fun consumeBlocking(): PendingInvalidation? = runBlocking { consume() }

    companion object {
        private val KEY_TITLE = stringPreferencesKey("title")
        private val KEY_MESSAGE = stringPreferencesKey("message")
        private val KEY_APP_CODE = stringPreferencesKey("app_code")
        private val KEY_TIMESTAMP = longPreferencesKey("timestamp")
    }
}
