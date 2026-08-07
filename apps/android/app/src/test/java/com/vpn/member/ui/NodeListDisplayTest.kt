package com.vpn.member.ui

import com.vpn.member.data.api.NodeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeListDisplayTest {
  @Test
  fun sortByLatency_putsMeasuredNodesFirst() {
    val nodes =
        listOf(
            node(id = 1, name = "武汉"),
            node(id = 2, name = "贵州"),
        )
    val sorted =
        NodeListDisplay.sortByLatency(
            nodes,
            mapOf(1L to 200, 2L to 80),
        )
    assertEquals(2L, sorted.first().id)
  }

  @Test
  fun fastestNodeId_returnsLowestLatency() {
    val nodes =
        listOf(
            node(id = 1, name = "武汉"),
            node(id = 2, name = "贵州"),
        )
    assertEquals(
        2L,
        NodeListDisplay.fastestNodeId(nodes, mapOf(1L to 200, 2L to 80)),
    )
  }

  @Test
  fun shouldHideRegionWhenFilterMatches() {
    assertFalse(NodeListDisplay.shouldShowRegionLine("cn", "cn"))
    assertTrue(NodeListDisplay.shouldShowRegionLine(null, "cn"))
  }

  @Test
  fun displaySceneTags_hidesReturnHomeUnderCnFilter() {
    val tags = NodeListDisplay.displaySceneTags(listOf("双跳中转", "适合回国"), "cn")
    assertEquals(listOf("双跳中转"), tags)
  }

  private fun node(id: Long, name: String): NodeItem =
      NodeItem(
          id = id,
          name = name,
          region = "cn",
          status = "online",
      )
}
