package com.vpn.kuayun.vpn

import android.app.Activity
import android.net.VpnService
import androidx.activity.result.ActivityResult
import androidx.core.app.ActivityCompat
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.vpn.kuayun.vpn.mihomo.MihomoInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

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
        VpnConnectionBus.update(ConnectionState.CONNECTING, error = null)
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
        VpnConnectionBus.update(ConnectionState.CONNECTING, error = null)
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

    /** 连接页会话流量：与通知栏共用 VpnSessionStatsTracker。 */
    @Command
    fun getStats(invoke: Invoke) {
        val status = VpnConnectionBus.status.value
        val ret = JSObject()
        if (status.state != ConnectionState.CONNECTED) {
            ret.put("uploadBytes", 0L)
            ret.put("downloadBytes", 0L)
            ret.put("durationMs", 0L)
            ret.put("uploadBps", 0L)
            ret.put("downloadBps", 0L)
            invoke.resolve(ret)
            return
        }
        val snap = VpnSessionStatsTracker.snapshot()
        val rates = VpnSessionStatsTracker.sampleRates(snap)
        ret.put("uploadBytes", snap.uploadBytes)
        ret.put("downloadBytes", snap.downloadBytes)
        ret.put("durationMs", snap.durationMs)
        ret.put("uploadBps", rates.uploadBps)
        ret.put("downloadBps", rates.downloadBps)
        invoke.resolve(ret)
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

    /** 应用内下载并安装 APK（DownloadManager + FileProvider）。 */
    @Command
    fun installApkUpdate(invoke: Invoke) {
        val args = invoke.getArgs()
        val url = args.getString("url", "").orEmpty()
        val versionLabel = args.getString("versionLabel", "latest").orEmpty().ifBlank { "latest" }
        val versionCode =
            runCatching { args.getDouble("versionCode").toInt() }.getOrElse {
                args.getString("versionCode", "0")?.toIntOrNull() ?: 0
            }
        if (url.isBlank()) {
            invoke.reject("下载地址无效")
            return
        }
        val installer = AppUpdateInstaller(activity.applicationContext)
        installer.attachActivity(activity)
        installer.startDownload(url, versionLabel, versionCode)
        val ret = JSObject()
        ret.put("started", true)
        invoke.resolve(ret)
    }

    /** 应用直连：枚举本机已安装应用 + 当前勾选包名。 */
    @Command
    fun listInstalledApps(invoke: Invoke) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val apps = InstalledAppCatalog(activity.applicationContext).listInstalledApps()
                    val selected = AppDirectConnectStore.userSelectedPackages(activity.applicationContext)
                    Triple(apps, selected, apps.size)
                }
            }.onSuccess { (apps, selected, count) ->
                val appsArr = JSONArray()
                apps.forEach { app ->
                    val item = JSObject()
                    item.put("packageName", app.packageName)
                    item.put("label", app.label)
                    appsArr.put(item)
                }
                val selectedArr = JSONArray()
                selected.forEach { selectedArr.put(it) }
                val ret = JSObject()
                ret.put("apps", appsArr)
                ret.put("selectedPackages", selectedArr)
                ret.put("needsPermission", InstalledAppsPermission.needsUserGrant(activity, count))
                invoke.resolve(ret)
            }.onFailure { e ->
                invoke.reject(e.message ?: "加载应用列表失败")
            }
        }
    }

    @Command
    fun getDirectConnectPackages(invoke: Invoke) {
        val selected = AppDirectConnectStore.userSelectedPackages(activity.applicationContext)
        val arr = JSONArray()
        selected.forEach { arr.put(it) }
        val ret = JSObject()
        ret.put("packages", arr)
        ret.put("count", selected.size)
        invoke.resolve(ret)
    }

    @Command
    fun setDirectConnectPackages(invoke: Invoke) {
        val args = invoke.getArgs()
        val raw = args.optJSONArray("packages")
        val packages = mutableListOf<String>()
        if (raw != null) {
            for (i in 0 until raw.length()) {
                val pkg = raw.optString(i, "").trim()
                if (pkg.isNotEmpty()) packages.add(pkg)
            }
        }
        AppDirectConnectStore.setUserSelectedPackages(activity.applicationContext, packages)
        val normalized = AppDirectConnectStore.userSelectedPackages(activity.applicationContext)
        val arr = JSONArray()
        normalized.forEach { arr.put(it) }
        val ret = JSObject()
        ret.put("packages", arr)
        ret.put("count", normalized.size)
        invoke.resolve(ret)
    }

    /** OEM「读取已安装应用」运行时权限；无声明则直接成功。 */
    @Command
    fun requestInstalledAppsPermission(invoke: Invoke) {
        if (InstalledAppsPermission.isGranted(activity)) {
            invoke.resolveObject(true)
            return
        }
        if (!InstalledAppsPermission.isPermissionDeclared(activity)) {
            invoke.resolveObject(true)
            return
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(InstalledAppsPermission.GET_INSTALLED_APPS),
            INSTALLED_APPS_PERMISSION_REQUEST,
        )
        // 系统弹窗结果异步；前端稍后重新 listInstalledApps
        invoke.resolveObject(true)
    }

    /** Always-on / lockdown / 省电 / 开机自连状态（不做自研 KS）。 */
    @Command
    fun getStabilityStatus(invoke: Invoke) {
        val ctx = activity.applicationContext
        val alwaysOn = AlwaysOnVpnDetector.detect(ctx)
        val batteryOk = BatteryOptimizationGuide.isIgnoringBatteryOptimizations(ctx)
        val boot = StabilityPrefs.isBootAutoConnectEnabled(ctx)
        var done = 0
        if (alwaysOn.alwaysOnConfigured) done++
        if (alwaysOn.lockdownConfigured) done++
        if (batteryOk) done++
        val ret = JSObject()
        ret.put("alwaysOnConfigured", alwaysOn.alwaysOnConfigured)
        ret.put("lockdownConfigured", alwaysOn.lockdownConfigured)
        ret.put("batteryOptimizationIgnored", batteryOk)
        ret.put("bootAutoConnectEnabled", boot)
        ret.put("hardeningDoneCount", done)
        ret.put("hardeningTotal", 3)
        invoke.resolve(ret)
    }

    @Command
    fun setBootAutoConnect(invoke: Invoke) {
        val args = invoke.getArgs()
        val enabled =
            when {
                args.has("enabled") -> args.optBoolean("enabled", false)
                else -> false
            }
        StabilityPrefs.setBootAutoConnectEnabled(activity.applicationContext, enabled)
        val ret = JSObject()
        ret.put("bootAutoConnectEnabled", StabilityPrefs.isBootAutoConnectEnabled(activity.applicationContext))
        invoke.resolve(ret)
    }

    @Command
    fun openVpnSettings(invoke: Invoke) {
        BatteryOptimizationGuide.openVpnSettings(activity)
        invoke.resolveObject(true)
    }

    @Command
    fun openBatteryOptimizationSettings(invoke: Invoke) {
        BatteryOptimizationGuide.openBatteryOptimizationSettings(activity)
        invoke.resolveObject(true)
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
        private const val INSTALLED_APPS_PERMISSION_REQUEST = 9102
    }
}
