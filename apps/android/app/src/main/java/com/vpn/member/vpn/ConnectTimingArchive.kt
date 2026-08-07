package com.vpn.member.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 首连耗时本地归档（最近 30 次），供设置页与诊断对照 P2-2。 */
object ConnectTimingArchive {
    private const val PREFS = "connect_timing_archive"
    private const val KEY_SAMPLES = "samples_json"
    private const val MAX_SAMPLES = 30
    const val KPI_TARGET_MS = ConnectTimingTracker.KPI_TARGET_MS

    data class Sample(
        val atMillis: Long,
        val clickToTunMs: Long,
        val configToTunMs: Long?,
        val kpiMet: Boolean,
    )

    data class Summary(
        val count: Int,
        val p50Ms: Long?,
        val p95Ms: Long?,
        val kpiMetRate: Double,
        val recent: List<Sample>,
    )

    fun record(
        context: Context,
        clickToTunMs: Long,
        configToTunMs: Long?,
    ) {
        val list = load(context).toMutableList()
        list.add(
            0,
            Sample(
                atMillis = System.currentTimeMillis(),
                clickToTunMs = clickToTunMs,
                configToTunMs = configToTunMs,
                kpiMet = clickToTunMs <= KPI_TARGET_MS,
            ),
        )
        while (list.size > MAX_SAMPLES) {
            list.removeAt(list.lastIndex)
        }
        save(context, list)
    }

    fun summarize(context: Context): Summary {
        val samples = load(context)
        if (samples.isEmpty()) {
            return Summary(count = 0, p50Ms = null, p95Ms = null, kpiMetRate = 0.0, recent = emptyList())
        }
        val sorted = samples.map { it.clickToTunMs }.sorted()
        val p50 = percentile(sorted, 0.5)
        val p95 = percentile(sorted, 0.95)
        val met = samples.count { it.kpiMet }.toDouble() / samples.size
        return Summary(
            count = samples.size,
            p50Ms = p50,
            p95Ms = p95,
            kpiMetRate = met,
            recent = samples.take(5),
        )
    }

    fun load(context: Context): List<Sample> {
        val raw =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SAMPLES, null)
                ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Sample(
                            atMillis = o.getLong("at"),
                            clickToTunMs = o.getLong("click_to_tun_ms"),
                            configToTunMs =
                                o.optString("config_to_tun_ms").toLongOrNull()
                                    ?: if (o.has("config_to_tun_ms") && !o.isNull("config_to_tun_ms")) {
                                        o.getLong("config_to_tun_ms")
                                    } else {
                                        null
                                    },
                            kpiMet = o.getBoolean("kpi_met"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun percentile(sorted: List<Long>, p: Double): Long? {
        if (sorted.isEmpty()) return null
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    private fun save(context: Context, samples: List<Sample>) {
        val arr = JSONArray()
        samples.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("at", s.atMillis)
                    put("click_to_tun_ms", s.clickToTunMs)
                    s.configToTunMs?.let { put("config_to_tun_ms", it) }
                    put("kpi_met", s.kpiMet)
                },
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAMPLES, arr.toString())
            .apply()
    }
}
