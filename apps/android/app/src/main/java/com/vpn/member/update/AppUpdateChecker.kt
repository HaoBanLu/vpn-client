package com.vpn.member.update

import com.vpn.member.data.api.ClientVersionData
import com.vpn.member.data.repository.AppRepository

object AppUpdateChecker {
    const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    fun shouldRunPeriodicCheck(lastCheckAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (lastCheckAt <= 0L) return false
        return now - lastCheckAt >= CHECK_INTERVAL_MS
    }

    fun shouldShowAutoPrompt(update: ClientVersionData, repository: AppRepository): Boolean {
        if (!update.has_update) return false
        return update.force_update ||
            !repository.isUpdateDismissed(update.latest_version_code)
    }
}
