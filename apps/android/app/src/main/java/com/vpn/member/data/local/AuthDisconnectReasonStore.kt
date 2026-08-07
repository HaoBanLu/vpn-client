package com.vpn.member.data.local

import android.content.Context

/** Kill Switch 前台通知副标题：说明鉴权/套餐触发的断网原因。 */
object AuthDisconnectReasonStore {
    private const val PREFS_NAME = "auth_disconnect_reason"
    private const val KEY_SUBTITLE = "subtitle"

    fun set(
        context: Context,
        subtitle: String,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUBTITLE, subtitle.trim())
            .apply()
    }

    fun peek(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUBTITLE, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SUBTITLE)
            .apply()
    }
}
