package com.vpn.member.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 回归：Column(fillMaxSize) 内嵌 LazyColumn(仅 fillMaxWidth) 会在列表有数据时触发 Compose 无限高度崩溃。
 * 允许：顶层 LazyColumn(fillMaxSize)，或 LazyColumn(weight(1f) / fillMaxSize) 放在已约束高度的容器内。
 */
class ComposeLazyListLayoutConventionTest {
    @Test
    fun screenSourcesAvoidUnboundedLazyColumnInColumn() {
        val screensDir = File("src/main/java/com/vpn/member/ui/screens")
        assertTrue("screens dir missing: ${screensDir.absolutePath}", screensDir.isDirectory)

        val violations =
            screensDir
                .listFiles { file -> file.isFile && file.name.endsWith("Screen.kt") }
                .orEmpty()
                .mapNotNull { file ->
                    val risky = findRiskyLazyColumnModifier(file.readText())
                    if (risky != null) "${file.name}: $risky" else null
                }

        assertTrue(
            "以下页面 LazyColumn 高度未约束（需 fillMaxSize 或 weight(1f)）：$violations",
            violations.isEmpty(),
        )
    }

    private fun findRiskyLazyColumnModifier(source: String): String? {
        var searchFrom = 0
        while (true) {
            val start = source.indexOf("LazyColumn(", searchFrom)
            if (start < 0) return null
            val params = extractLazyColumnParams(source, start)
            if (params != null && isUnboundedLazyColumnModifier(params)) {
                return params.lineSequence().firstOrNull { it.contains("modifier") }?.trim()
            }
            searchFrom = start + "LazyColumn(".length
        }
    }

    private fun extractLazyColumnParams(source: String, startIndex: Int): String? {
        var depth = 0
        val contentStart = startIndex + "LazyColumn(".length
        val buf = StringBuilder()
        for (i in contentStart until source.length) {
            val c = source[i]
            if (c == ')') {
                if (depth == 0) return buf.toString()
                depth--
            } else if (c == '(') {
                depth++
            }
            buf.append(c)
        }
        return null
    }

    private fun isUnboundedLazyColumnModifier(params: String): Boolean {
        if (!params.contains("modifier")) return false
        val hasFillMaxSize = params.contains("fillMaxSize")
        val hasWeight = params.contains("weight(1f)") || params.contains("weight(1.0f)")
        if (hasFillMaxSize || hasWeight) return false
        return params.contains("fillMaxWidth()")
    }
}
