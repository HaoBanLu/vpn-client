package com.vpn.member.vpn

import android.content.Context

/** 首次连接隐私引导完成状态。 */
class PrivacyOnboardingStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    fun markCompleted(skippedSystemHardening: Boolean = false) {
        prefs.edit()
            .putBoolean(KEY_COMPLETED, true)
            .putBoolean(KEY_SYSTEM_HARDENING_SKIPPED, skippedSystemHardening)
            .apply()
    }

    fun hasSkippedSystemHardening(): Boolean =
        prefs.getBoolean(KEY_SYSTEM_HARDENING_SKIPPED, false)

    /** 隐私基线已在 [PrivacyBaselineMigrator] 静默开启，不再打断连接流程。 */
    fun shouldShowOnboarding(): Boolean = false

    companion object {
        private const val PREFS_NAME = "privacy_onboarding"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_SYSTEM_HARDENING_SKIPPED = "system_hardening_skipped"
    }
}
