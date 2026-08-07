package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.NodeItem
import com.vpn.member.data.api.RegionItem
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.network.mapLoadError
import com.vpn.member.data.network.SessionInvalidatedException
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.ui.NodeListDisplay
import com.vpn.member.ui.displayNodeName
import com.vpn.member.ui.isOnline
import com.vpn.member.vpn.AppProtocolSupport
import com.vpn.member.vpn.ClientLatencyProbe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NodesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val testingLatency: Boolean = false,
    val nodes: List<NodeItem> = emptyList(),
    val regions: List<RegionItem> = emptyList(),
    /** 节点页列表筛选，仅影响浏览，不改变连接选路。null = 全部地区。 */
    val filterRegion: String? = null,
    val selectedNode: String? = null,
    val latencyMap: Map<Long, Int> = emptyMap(),
    val message: String? = null,
    val error: String? = null,
)

class NodesViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            NodesUiState(
                selectedNode = repository.getSavedNode(),
            ),
        )
    val state: StateFlow<NodesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val hasData = _state.value.nodes.isNotEmpty()
            _state.value = _state.value.copy(
                refreshing = hasData,
                loading = !hasData,
                error = null,
            )
            runCatching {
                repository.ensureNetworkAvailable()
                val nodes = repository.getNodes()
                val regions = repository.getRegions()
                val savedNode = repository.getSavedNode()
                if (
                    savedNode != null &&
                    nodes.none {
                        it.name == savedNode &&
                            AppProtocolSupport.isAppConnectable(it) &&
                            it.isOnline()
                    }
                ) {
                    repository.saveNode(null)
                }
                _state.value =
                    _state.value.copy(
                        loading = false,
                        refreshing = false,
                        nodes = nodes,
                        regions = regions,
                        selectedNode = repository.getSavedNode(),
                    )
                autoTestLatency()
            }.onFailure { e ->
                if (e is SessionInvalidatedException) return@onFailure
                _state.value =
                    _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = mapLoadError(e),
                    )
            }
        }
    }

    /** 节点页地区筛选，只过滤列表，不写入连接偏好；不自动测速。 */
    fun setFilterRegion(region: String?) {
        _state.value = _state.value.copy(filterRegion = region)
    }

    fun syncSelected(node: NodeItem) {
        if (!node.isOnline() || !AppProtocolSupport.isAppConnectable(node)) return
        repository.saveNode(node.name)
        repository.saveRegion(node.region)
        _state.value =
            _state.value.copy(
                selectedNode = node.name,
                filterRegion = node.region,
                message = null,
            )
    }

    fun selectNode(node: NodeItem) {
        when {
            !node.isOnline() ->
                _state.value = _state.value.copy(error = "该节点已离线，请选择其他节点", message = null)
            !AppProtocolSupport.isAppConnectable(node) ->
                _state.value = _state.value.copy(error = AppProtocolSupport.unsupportedReason(node), message = null)
            else -> syncSelected(node)
        }
    }

    fun clearNode() {
        repository.saveNode(null)
        _state.value = _state.value.copy(
            selectedNode = null,
            message = "已清除节点选择",
        )
    }

    fun testLatency() {
        runLatencyTest()
    }

    /** 进入节点页后自动测一次入口延迟（切换地区不自动测，需点「批量测速」）。 */
    private fun autoTestLatency() {
        if (_state.value.testingLatency) return
        val nodes = currentConnectableNodes()
        if (nodes.isEmpty()) return
        runLatencyTest()
    }

    private fun currentConnectableNodes(): List<NodeItem> =
        NodeListDisplay.filterConnectable(_state.value.nodes, _state.value.filterRegion)

    private fun runLatencyTest() {
        val nodes = currentConnectableNodes()
        if (nodes.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(testingLatency = true, error = null, message = null)
            runCatching {
                probeClientLatencies(nodes).filterValues { it > 0 }
            }
                .onSuccess { results ->
                    _state.value =
                        _state.value.copy(
                            testingLatency = false,
                            latencyMap = _state.value.latencyMap + results,
                            message = null,
                        )
                }
                .onFailure { e ->
                    if (e is SessionInvalidatedException) return@onFailure
                    _state.value =
                        _state.value.copy(
                            testingLatency = false,
                            error = ApiRequestSupport.mapError(e, "入口延迟测试失败"),
                        )
                }
        }
    }

    private suspend fun probeClientLatencies(nodes: List<NodeItem>): Map<Long, Int> =
        coroutineScope {
            nodes
                .map { node ->
                    async {
                        val endpoint = ClientLatencyProbe.parseEndpoint(node.latency_endpoint) ?: return@async node.id to -1
                        val latency = ClientLatencyProbe.probeTcp(endpoint.first, endpoint.second) ?: -1
                        node.id to latency
                    }
                }.awaitAll()
                .toMap()
        }
}
