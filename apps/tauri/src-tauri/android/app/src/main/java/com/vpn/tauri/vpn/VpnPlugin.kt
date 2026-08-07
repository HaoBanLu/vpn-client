package com.vpn.tauri.vpn

import android.app.Activity
import android.net.VpnService
import androidx.activity.result.ActivityResult
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.vpn.tauri.vpn.mihomo.MihomoInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@TauriPlugin
class VpnPlugin(private val activity: Activity) : Plugin(activity) {
    private val controller = VpnController(activity.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        NetworkServices.init(activity.application)
        MihomoInitializer.ensureReady(activity.application)
        scope.launch {
            VpnConnectionBus.status.collectLatest { status ->
                emitStatus(status)
            }
        }
    }

    @Command
    fun prepare(invoke: Invoke) {
        val intent = VpnService.prepare(activity)
        if (intent != null) {
            startActivityForResult(invoke, intent, "handlePrepareResult")
        } else {
            invoke.resolveObject(true)
        }
    }

    @ActivityCallback
    fun handlePrepareResult(invoke: Invoke, result: ActivityResult) {
        val granted = result.resultCode == Activity.RESULT_OK && VpnService.prepare(activity) == null
        invoke.resolveObject(granted)
    }

    @Command
    fun connect(invoke: Invoke) {
        val args = invoke.getArgs()
        val raw = args.getString("configJson", "").orEmpty()
        val nodeName = args.getString("nodeName")
        if (raw.isBlank()) {
            invoke.reject("config is empty")
            return
        }
        val config = VpnConfigPatcher.prepareClashConfig(raw)
        controller.connect(config, nodeName)
        invoke.resolve(statusObject(ConnectionState.CONNECTING, null))
    }

    @Command
    fun reconnect(invoke: Invoke) {
        val args = invoke.getArgs()
        val raw = args.getString("configJson", "").orEmpty()
        val nodeName = args.getString("nodeName")
        if (raw.isBlank()) {
            invoke.reject("config is empty")
            return
        }
        val config = VpnConfigPatcher.prepareClashConfig(raw)
        controller.reconnect(config, nodeName)
        invoke.resolve(statusObject(ConnectionState.CONNECTING, null))
    }

    @Command
    fun disconnect(invoke: Invoke) {
        controller.disconnect()
        invoke.resolve(JSObject())
    }

    @Command
    fun getStatus(invoke: Invoke) {
        val status = VpnConnectionBus.status.value
        invoke.resolve(statusObject(status.state, status.error))
    }

    @Command
    fun probe(invoke: Invoke) {
        scope.launch {
            runCatching { ConnectivityProbe.probe() }
                .onSuccess { result ->
                    val ret = JSObject()
                    ret.put("basicOk", result.basicOk)
                    ret.put("overseasOk", result.overseasOk)
                    ret.put("slow", result.slow)
                    if (result.latencyMs != null) {
                        ret.put("latencyMs", result.latencyMs)
                    }
                    invoke.resolve(ret)
                }
                .onFailure { e ->
                    invoke.reject(e.message ?: "网络探测失败")
                }
        }
    }

    private fun emitStatus(status: VpnConnectionStatus) {
        trigger("vpn://status", statusObject(status.state, status.error))
    }

    private fun statusObject(state: ConnectionState, error: String?): JSObject {
        val ret = JSObject()
        ret.put("state", state.name.lowercase())
        ret.put("error", error)
        return ret
    }

    companion object {
        const val VPN_PREPARE_REQUEST = 9001
    }
}
