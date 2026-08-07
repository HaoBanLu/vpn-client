package com.vpn.member.vpn

import java.util.TimeZone

/** 根据系统时区粗判用户是否在内地/港澳台（用于海外回国场景的栈与文案策略）。 */
object OverseasLocaleHint {
    private val chinaTimeZoneIds =
        setOf(
            "Asia/Shanghai",
            "Asia/Chongqing",
            "Asia/Harbin",
            "Asia/Urumqi",
            "Asia/Hong_Kong",
            "Asia/Macau",
            "Asia/Taipei",
        )

    fun isOverseasTimezone(timezoneId: String = TimeZone.getDefault().id): Boolean {
        val id = timezoneId.trim()
        if (id.isBlank()) return false
        return chinaTimeZoneIds.none { id.equals(it, ignoreCase = true) }
    }
}
