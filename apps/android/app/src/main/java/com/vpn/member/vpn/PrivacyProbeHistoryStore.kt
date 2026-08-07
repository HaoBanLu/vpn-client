package com.vpn.member.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 泄露自检历史（最近 10 次），供「连接与隐私」页展示。 */
object PrivacyProbeHistoryStore {
    private const val PREFS = "privacy_probe_history"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 10

    data class Entry(
        val atMillis: Long,
        val passed: Boolean,
        val exitIp: String?,
        val summary: String,
    )

    fun append(context: Context, result: PrivacyLeakProbe.Result) {
        // 会员向摘要；出口 IP / IPv6 / DNS 细节仍保存在 exitIp 与探针结果 toast
        val summary =
            if (result.passed) {
                "已通过"
            } else {
                "未通过 · 可能泄露真实网络信息"
            }
        val entry =
            Entry(
                atMillis = System.currentTimeMillis(),
                passed = result.passed,
                exitIp = result.exitIp,
                summary = summary,
            )
        val list = load(context).toMutableList()
        list.add(0, entry)
        while (list.size > MAX_ENTRIES) {
            list.removeAt(list.lastIndex)
        }
        save(context, list)
    }

    fun load(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRIES, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Entry(
                            atMillis = o.getLong("at"),
                            passed = o.getBoolean("passed"),
                            exitIp = o.optString("exit_ip").takeIf { it.isNotBlank() },
                            summary = o.getString("summary"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("at", e.atMillis)
                    put("passed", e.passed)
                    put("exit_ip", e.exitIp ?: "")
                    put("summary", e.summary)
                },
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, arr.toString())
            .apply()
    }
}
