package com.vpn.member.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 保护等级与相关设置变更历史（最近 10 条），供「连接与隐私」页展示。 */
object ProtectionLevelChangeStore {
    private const val PREFS = "protection_level_history"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 10

    data class Entry(
        val atMillis: Long,
        val event: String,
        val summary: String,
    )

    fun appendLevelChange(
        context: Context,
        from: ProtectionLevel?,
        to: ProtectionLevel,
        reason: String,
    ) {
        if (from == to) return
        val fromLabel = from?.name ?: "—"
        append(
            context,
            event = "level_change",
            summary = "$fromLabel → ${to.name}（$reason）",
        )
    }

    fun appendSettingChange(
        context: Context,
        setting: String,
        enabled: Boolean,
    ) {
        append(
            context,
            event = "setting_change",
            summary = "$setting：${if (enabled) "开启" else "关闭"}",
        )
    }

    fun load(context: Context): List<Entry> {
        val raw =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRIES, null)
                ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Entry(
                            atMillis = o.getLong("at"),
                            event = o.getString("event"),
                            summary = o.getString("summary"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun append(context: Context, event: String, summary: String) {
        val list = load(context).toMutableList()
        list.add(
            0,
            Entry(
                atMillis = System.currentTimeMillis(),
                event = event,
                summary = summary,
            ),
        )
        while (list.size > MAX_ENTRIES) {
            list.removeAt(list.lastIndex)
        }
        save(context, list)
    }

    private fun save(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("at", e.atMillis)
                    put("event", e.event)
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
