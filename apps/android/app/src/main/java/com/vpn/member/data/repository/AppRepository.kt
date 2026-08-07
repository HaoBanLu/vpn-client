package com.vpn.member.data.repository

import com.vpn.member.VpnMemberApp
import com.vpn.member.debug.AppDebugLogEntry
import com.vpn.member.debug.AppDebugLogger
import com.vpn.member.debug.DebugDeviceMeta
import com.vpn.member.BuildConfig
import com.vpn.member.data.api.ApiResponse
import com.vpn.member.data.api.AuthData
import com.vpn.member.data.api.BatchLatencyRequest
import com.vpn.member.data.api.AddTicketReplyRequest
import com.vpn.member.data.api.ChangePasswordRequest
import com.vpn.member.data.api.ClientConfigData
import com.vpn.member.data.api.CreateTicketRequest
import com.vpn.member.data.api.DailyTrafficItem
import com.vpn.member.data.api.LoginRequest
import com.vpn.member.data.api.NodeItem
import com.vpn.member.data.api.OrderItem
import com.vpn.member.vpn.AppRouteMode
import com.vpn.member.vpn.AppDirectConnectStore
import com.vpn.member.vpn.DirectBypassRule
import com.vpn.member.vpn.DirectBypassRuleStore
import com.vpn.member.vpn.ExitIpProbeContext
import com.vpn.member.vpn.mihomo.MihomoGeoAssetManager
import com.vpn.member.vpn.ClashConfigStore
import com.vpn.member.vpn.ProtectionLevelResolver
import com.vpn.member.vpn.VpnSessionSnapshot
import com.vpn.member.vpn.VpnSessionStore
import com.vpn.member.data.api.OrderStatusData
import com.vpn.member.data.api.PackageItem
import com.vpn.member.data.api.PushTokenRequest
import com.vpn.member.data.api.RegisterRequest
import com.vpn.member.data.api.RegionItem
import com.vpn.member.data.api.SubscriptionActive
import com.vpn.member.data.api.SupportConfigData
import com.vpn.member.data.api.TicketItem
import com.vpn.member.data.api.TicketReplyItem
import com.vpn.member.data.api.TicketsData
import com.vpn.member.data.api.AppDebugLogUploadEntry
import com.vpn.member.data.api.AppDebugLogUploadRequest
import com.vpn.member.data.api.ConnectDashboardData
import com.vpn.member.data.api.LineLeaseItem
import com.vpn.member.data.api.LineStatusData
import com.vpn.member.data.api.MemberSessionsData
import com.vpn.member.data.api.SessionHeartbeatData
import com.vpn.member.data.api.SessionHeartbeatRequest
import com.vpn.member.data.api.UserPreferencesData
import com.vpn.member.data.api.UserPreferencesUpdate
import com.vpn.member.data.api.SubscriptionUsage
import com.vpn.member.data.api.TrafficSummary
import com.vpn.member.data.api.UserBrief
import com.vpn.member.data.api.VpnApi
import com.vpn.member.data.device.DeviceInfoProvider
import com.vpn.member.data.device.InstalledAppCatalog
import com.vpn.member.data.device.InstalledAppsPermission
import com.vpn.member.data.local.AppPreferences
import com.vpn.member.data.local.TokenStore
import com.vpn.member.data.network.ApiErrors
import com.vpn.member.data.network.NetworkStatus
import com.vpn.member.data.network.NO_NETWORK_MESSAGE
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.network.ApiErrorTelemetry
import com.vpn.member.data.network.SessionInvalidatedException
import com.vpn.member.data.session.SessionAuth
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AppException(
    val userMessage: String,
    val appCode: String? = null,
    val retryable: Boolean = false,
    val traceId: String? = null,
) : Exception(userMessage)

class AppRepository(
    private val api: VpnApi,
    private val tokenStore: TokenStore,
    private val preferences: AppPreferences,
    private val appContext: Context,
) {
    val isLoggedIn: Boolean get() = !tokenStore.getJwt().isNullOrBlank()

    val sessionActive get() = tokenStore.sessionActive

    fun isNetworkAvailable(): Boolean = NetworkStatus.isOnline(appContext)

    /** 无网络时抛出业务错误，不自动登出（断网是暂时的）。 */
    fun ensureNetworkAvailable() {
        if (isNetworkAvailable()) return
        throw AppException(NO_NETWORK_MESSAGE)
    }

    fun isPrivacyAccepted(): Boolean = preferences.isPrivacyAccepted()

    /** 注册勾选条款后调用；触发 geodata 后台下载。 */
    fun acceptPrivacy() {
        if (preferences.isPrivacyAccepted()) return
        preferences.setPrivacyAccepted()
        MihomoGeoAssetManager.scheduleInstall(appContext)
    }

    /** 已登录老用户迁移：补写隐私同意，不增 UI。 */
    fun ensurePrivacyAcceptedIfLoggedIn() {
        if (!isLoggedIn || preferences.isPrivacyAccepted()) return
        acceptPrivacy()
    }

    fun getDirectConnectPackages(): Set<String> = AppDirectConnectStore.userSelectedPackages(preferences)

    fun setDirectConnectPackages(packages: Set<String>) {
        preferences.setDirectConnectPackages(packages)
    }

    fun getDirectConnectCount(): Int = AppDirectConnectStore.directConnectCount(preferences)

    fun getDirectBypassRules(): List<DirectBypassRule> = DirectBypassRuleStore.loadRules(preferences)

    fun setDirectBypassRules(rules: List<DirectBypassRule>) {
        DirectBypassRuleStore.saveRules(preferences, rules)
    }

    fun getDirectBypassRuleCount(): Int = DirectBypassRuleStore.enabledCount(preferences)

    private val vpnSessionStore by lazy { VpnSessionStore(appContext, preferences) }

    fun saveVpnSessionSnapshot(snapshot: VpnSessionSnapshot) = vpnSessionStore.saveSnapshot(snapshot)

    fun getVpnSessionSnapshot(): VpnSessionSnapshot? = vpnSessionStore.readSnapshot()

    fun clearVpnSessionSnapshot() = vpnSessionStore.clearSnapshot()

    fun incrementVpnReconnectAttempts(): Int = vpnSessionStore.incrementReconnectAttempts()

    fun resetVpnReconnectAttempts() = vpnSessionStore.resetReconnectAttempts()

    fun getVpnReconnectAttempts(): Int = vpnSessionStore.getReconnectAttempts()

    fun isAutoReconnectEnabled(): Boolean = preferences.isAutoReconnectEnabled()

    fun isKillSwitchEnabled(): Boolean = preferences.isKillSwitchEnabled()

    fun isBootAutoConnectEnabled(): Boolean = preferences.isBootAutoConnectEnabled()

    fun setAutoReconnectEnabled(enabled: Boolean) = preferences.setAutoReconnectEnabled(enabled)

    fun setKillSwitchEnabled(enabled: Boolean) = preferences.setKillSwitchEnabled(enabled)

    fun hasUserModifiedKillSwitch(): Boolean = preferences.hasUserModifiedKillSwitch()

    fun isIpv6LeakProtectionEnabled(): Boolean = preferences.isIpv6LeakProtectionEnabled()

    fun isPrivacyBaselineReady(): Boolean =
        ProtectionLevelResolver.isPrivacyBaselineReady(
            killSwitchEnabled = isKillSwitchEnabled(),
            ipv6ProtectionEnabled = isIpv6LeakProtectionEnabled(),
        )

    fun isReconnectKillSwitchHoldEnabled(): Boolean = preferences.isReconnectKillSwitchHoldEnabled()

    fun isBlockOnConnectFailureEnabled(): Boolean = preferences.isBlockOnConnectFailureEnabled()

    fun setIpv6LeakProtectionEnabled(enabled: Boolean) = preferences.setIpv6LeakProtectionEnabled(enabled)

    fun setReconnectKillSwitchHoldEnabled(enabled: Boolean) =
        preferences.setReconnectKillSwitchHoldEnabled(enabled)

    fun setBlockOnConnectFailureEnabled(enabled: Boolean) =
        preferences.setBlockOnConnectFailureEnabled(enabled)

    fun getLastPrivacyProbeAt(): Long = preferences.getLastPrivacyProbeAt()

    fun setLastPrivacyProbeAt(timestamp: Long) = preferences.setLastPrivacyProbeAt(timestamp)

    fun getDirectConnectPackageCount(): Int = preferences.getDirectConnectPackages().size

    fun setBootAutoConnectEnabled(enabled: Boolean) = preferences.setBootAutoConnectEnabled(enabled)

    fun isBatteryOptimizationGuideDismissed(): Boolean = preferences.isBatteryOptimizationGuideDismissed()

    fun setBatteryOptimizationGuideDismissed(dismissed: Boolean) =
        preferences.setBatteryOptimizationGuideDismissed(dismissed)

    fun getTunStackMode(): String =
        com.vpn.member.vpn.TunStackMode.resolve(preferences.getTunStackMode())

    fun setTunStackMode(mode: String) = preferences.setTunStackMode(mode)

    fun getTunStackAutoSwitchNote(): String? = preferences.getTunStackAutoSwitchNote()

    fun applicationContext(): Context = appContext

    fun listLaunchableApps(): List<com.vpn.member.data.device.LaunchableApp> =
        InstalledAppCatalog(appContext).listInstalledApps()

    fun isInstalledAppsPermissionGranted(): Boolean = InstalledAppsPermission.isGranted(appContext)

    private suspend fun <T> callApi(block: suspend () -> T): T =
        runCatching { block() }.getOrElse { throw mapThrowable(it) }

    private fun reportApiFailure(endpoint: String, error: Throwable, attempted: Int) {
        ApiErrorTelemetry.record(endpoint, error, succeeded = false)
        if (!AppDebugLogger.isEnabled()) return
        val msg = (error as? AppException)?.userMessage ?: error.message.orEmpty()
        AppDebugLogger.warn(
            category = "api_error",
            message = "$endpoint: ${msg.take(120)}",
            context =
                ApiRequestSupport.buildErrorContext(error) +
                    mapOf("endpoint" to endpoint, "attempt" to attempted.toString()),
        )
    }

    /** 幂等读接口：网络抖动时自动退避重试。 */
    private suspend fun <T> callApiRead(endpoint: String, block: suspend () -> T): T =
        ApiRequestSupport.withRetry(
            onRetry = { attempt, err ->
                reportApiFailure(endpoint, err, attempted = attempt)
            },
        ) {
            try {
                callApi(block)
            } catch (e: Throwable) {
                reportApiFailure(endpoint, e, attempted = ApiRequestSupport.DEFAULT_MAX_ATTEMPTS)
                throw e
            }
        }

    suspend fun getRegistrationConfig(): com.vpn.member.data.api.RegistrationConfigData =
        callApiRead("registration_config") { unwrap(api.getRegistrationConfig()) }

    suspend fun sendEmailCode(email: String, purpose: String) {
        callApi {
            val resp = api.sendEmailCode(com.vpn.member.data.api.SendEmailCodeRequest(email, purpose))
            ensureApiSuccess(resp)
        }
    }

    suspend fun forgotPassword(email: String) {
        callApi {
            val resp = api.forgotPassword(com.vpn.member.data.api.ForgotPasswordRequest(email))
            ensureApiSuccess(resp)
        }
    }

    suspend fun resetPassword(email: String, emailCode: String, newPassword: String) {
        callApi {
            val device = DeviceInfoProvider.get(appContext)
            val resp = api.resetPassword(
                com.vpn.member.data.api.ResetPasswordRequest(
                    email = email,
                    email_code = emailCode,
                    new_password = newPassword,
                    device_id = device.deviceId,
                    device_name = device.deviceName,
                    device_type = device.deviceType,
                    app_version = device.appVersion,
                    device_brand = device.deviceBrand,
                    device_model = device.deviceModel,
                    os_name = device.osName,
                    os_version = device.osVersion,
                    client_platform = device.clientPlatform,
                    login_source = device.loginSource,
                ),
            )
            ensureApiSuccess(resp)
        }
    }

    suspend fun register(email: String, password: String, emailCode: String? = null): AuthData =
        callApi {
            val device = DeviceInfoProvider.get(appContext)
            unwrapAuth(
                api.register(
                    RegisterRequest(
                        email = email,
                        password = password,
                        email_code = emailCode,
                        device_id = device.deviceId,
                        device_name = device.deviceName,
                        device_type = device.deviceType,
                        app_version = device.appVersion,
                        device_brand = device.deviceBrand,
                        device_model = device.deviceModel,
                        os_name = device.osName,
                        os_version = device.osVersion,
                        client_platform = device.clientPlatform,
                        login_source = device.loginSource,
                    ),
                ),
            )
        }

    suspend fun login(email: String, password: String): AuthData =
        callApi {
            val device = DeviceInfoProvider.get(appContext)
            unwrapAuth(
                api.login(
                    LoginRequest(
                        email = email,
                        password = password,
                        device_id = device.deviceId,
                        device_name = device.deviceName,
                        device_type = device.deviceType,
                        app_version = device.appVersion,
                        device_brand = device.deviceBrand,
                        device_model = device.deviceModel,
                        os_name = device.osName,
                        os_version = device.osVersion,
                        client_platform = device.clientPlatform,
                        login_source = device.loginSource,
                    ),
                ),
            )
        }

    suspend fun logout() {
        if (isLoggedIn) {
            runCatching {
                unregisterPushToken(DeviceInfoProvider.get(appContext).deviceId)
            }
        }
        tokenStore.clearSession()
        clearVpnSessionSnapshot()
        ClashConfigStore.wipe(appContext)
    }

    suspend fun syncPushToken(token: String) {
        if (!isLoggedIn || token.isBlank()) return
        callApi {
            val device = DeviceInfoProvider.get(appContext)
            ensureApiSuccess(
                api.registerPushToken(
                    PushTokenRequest(
                        token = token,
                        device_id = device.deviceId,
                        platform = "android",
                        enabled = true,
                    ),
                ),
            )
        }
    }

    suspend fun unregisterPushToken(deviceId: String) {
        if (deviceId.isBlank()) return
        callApi {
            ensureApiSuccess(
                api.registerPushToken(
                    PushTokenRequest(
                        token = "revoked",
                        device_id = deviceId,
                        platform = "android",
                        enabled = false,
                    ),
                ),
            )
        }
    }

    data class SavedLoginCredentials(
        val email: String = "",
        val password: String = "",
        val remember: Boolean = true,
    )

    fun getSavedLoginCredentials(): SavedLoginCredentials {
        val remember = tokenStore.isRememberLoginEnabled()
        if (!remember) {
            return SavedLoginCredentials(remember = false)
        }
        return SavedLoginCredentials(
            email = tokenStore.getSavedLoginEmail().orEmpty(),
            password = tokenStore.getSavedLoginPassword().orEmpty(),
            remember = true,
        )
    }

    fun saveLoginCredentials(remember: Boolean, email: String, password: String) {
        tokenStore.saveLoginCredentials(remember, email, password)
    }

    suspend fun getMe(): UserBrief = callApiRead("users_me") {
        val user = unwrap(api.getMe())
        applyAppDebugFlag(user.app_debug_enabled)
        user
    }

    fun isAppDebugEnabled(): Boolean = tokenStore.isAppDebugEnabled()

    suspend fun uploadAppDebugLogs(entries: List<AppDebugLogEntry>) {
        if (!isAppDebugEnabled() || entries.isEmpty()) return
        try {
            callApi {
                val device = DeviceInfoProvider.get(appContext)
                val body =
                    AppDebugLogUploadRequest(
                        device_id = device.deviceId,
                        device_meta = DebugDeviceMeta.build(appContext),
                        entries =
                            entries.map {
                                AppDebugLogUploadEntry(
                                    level = it.level,
                                    category = it.category,
                                    message = it.message,
                                    context = it.context.ifEmpty { null },
                                    client_at = it.clientAt,
                                )
                            },
                    )
                val resp = api.uploadAppDebugLogs(body)
                if (resp.code == 403) {
                    applyAppDebugFlag(false)
                }
            }
        } catch (e: SessionInvalidatedException) {
            throw e
        } catch (_: Throwable) {
            // 诊断日志上传失败不影响主流程
        }
    }

    private fun applyAppDebugFlag(enabled: Boolean) {
        tokenStore.saveAppDebugEnabled(enabled)
        (appContext.applicationContext as? VpnMemberApp)?.refreshAppDebugLogger()
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        callApi {
            ensureApiSuccess(api.changePassword(ChangePasswordRequest(oldPassword, newPassword)))
        }
    }

    suspend fun getPackages(): List<PackageItem> = callApiRead("packages") { unwrap(api.getPackages()).packages }

    suspend fun getPaymentMethods(): com.vpn.member.data.api.PaymentMethodsData =
        callApiRead("payment_methods") { unwrap(api.getPaymentMethods()) }

    suspend fun createRechargeOrder(amountUsdt: Double): com.vpn.member.data.api.CreateRechargeData =
        callApi { unwrap(api.createRechargeOrder(com.vpn.member.data.api.CreateRechargeRequest(amountUsdt))) }

    suspend fun getRechargeOrders(): List<com.vpn.member.data.api.RechargeOrderItem> =
        callApiRead("recharge_orders") { unwrap(api.getRechargeOrders()).orders }

    suspend fun getRechargeOrder(id: Long): com.vpn.member.data.api.RechargeOrderItem =
        callApiRead("recharge_order") { unwrap(api.getRechargeOrder(id)) }

    suspend fun uploadRechargeProof(uri: Uri): ProofUploadResult {
        val resolver = appContext.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw AppException("读取截图失败")
        if (bytes.isEmpty()) {
            throw AppException("截图文件为空")
        }
        val fileName = resolveProofFileName(uri, mimeType)
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        val url = callApi { unwrap(api.uploadRechargeProof(part)).url }
        return ProofUploadResult(url = url, fileName = fileName)
    }

    private fun resolveProofFileName(uri: Uri, mimeType: String): String {
        val resolver = appContext.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)?.trim()
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        val ext = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }
        return "proof.$ext"
    }

    suspend fun submitRechargeOrder(
        id: Long,
        fromAddress: String?,
        proofImageUrl: String?,
        txid: String?,
    ): com.vpn.member.data.api.RechargeOrderItem =
        callApi {
            unwrap(
                api.submitRechargeOrder(
                    id,
                    com.vpn.member.data.api.SubmitRechargeRequest(
                        from_address = fromAddress?.trim()?.takeIf { it.isNotEmpty() },
                        proof_image_url = proofImageUrl?.trim()?.takeIf { it.isNotEmpty() },
                        txid = txid?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                ),
            )
        }

    suspend fun saveRechargeTransferHint(
        id: Long,
        fromAddress: String?,
        proofImageUrl: String?,
        txid: String?,
    ): com.vpn.member.data.api.RechargeOrderItem =
        callApi {
            unwrap(
                api.saveRechargeTransferHint(
                    id,
                    com.vpn.member.data.api.TransferHintRequest(
                        from_address = fromAddress?.trim()?.takeIf { it.isNotEmpty() },
                        proof_image_url = proofImageUrl?.trim()?.takeIf { it.isNotEmpty() },
                        txid = txid?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                ),
            )
        }

    suspend fun cancelRechargeOrder(id: Long) {
        callApi {
            ensureApiSuccess(api.cancelRechargeOrder(id))
        }
    }

    suspend fun createOrder(packageId: Long, paymentMethod: String = "balance"): Long =
        callApi {
            unwrap(
                api.createOrder(
                    com.vpn.member.data.api.CreateOrderRequest(packageId, paymentMethod)
                )
            ).id
        }

    suspend fun payOrder(orderId: Long) {
        callApi {
            ensureApiSuccess(api.payOrder(orderId))
        }
    }

    suspend fun pollOrderStatus(orderId: Long): OrderStatusData = callApiRead("order_status") { unwrap(api.getOrderStatus(orderId)) }

    suspend fun getOrders(): List<OrderItem> = callApiRead("orders") { unwrap(api.getOrders()).orders }

    suspend fun getMyTickets(page: Int = 1, pageSize: Int = 20): TicketsData =
        callApiRead("tickets") { unwrap(api.getMyTickets(page, pageSize)) }

    suspend fun getTicketById(id: Long): TicketItem = callApiRead("ticket_detail") { unwrap(api.getTicketById(id)) }

    suspend fun createTicket(title: String, content: String, priority: String = "normal"): TicketItem =
        callApi { unwrap(api.createTicket(CreateTicketRequest(title, content, priority))) }

    suspend fun addTicketReply(ticketId: Long, content: String): TicketReplyItem =
        callApi { unwrap(api.addTicketReply(ticketId, AddTicketReplyRequest(content))) }

    suspend fun getSupportConfig(): SupportConfigData = callApiRead("support_config") {
        try {
            unwrap(api.getSupportConfig())
        } catch (_: AppException) {
            SupportConfigData()
        }
    }

    suspend fun getActiveSubscription(): SubscriptionActive? = callApiRead("subscription_active") {
        try {
            unwrap(api.getActiveSubscription()).subscription
        } catch (e: AppException) {
            if (e.appCode == "NO_ACTIVE_SUBSCRIPTION") null else throw e
        }
    }

    suspend fun getUsage(): SubscriptionUsage = callApiRead("subscription_usage") { unwrap(api.getSubscriptionUsage()) }

    suspend fun getRegions(): List<RegionItem> = callApiRead("regions") { unwrap(api.getRegions()).regions }

    suspend fun getNodes(): List<NodeItem> = callApiRead("nodes") { unwrap(api.getNodes()).nodes }

    suspend fun testNodeLatency(nodeId: Long): Int =
        callApiRead("node_latency") { unwrap(api.testNodeLatency(nodeId)).latency }

    suspend fun batchTestLatency(nodeIds: List<Long>): Map<Long, Int> {
        if (nodeIds.isEmpty()) return emptyMap()
        return callApiRead("batch_latency") {
            val raw = unwrap(api.batchTestLatency(BatchLatencyRequest(nodeIds))).results
            raw.mapNotNull { (key, value) ->
                key.toLongOrNull()?.let { it to value }
            }.toMap()
        }
    }

    suspend fun getTrafficSummary(): TrafficSummary = callApiRead("traffic_summary") { unwrap(api.getTrafficSummary()) }

    suspend fun getTrafficDaily(): List<DailyTrafficItem> = callApiRead("traffic_daily") { unwrap(api.getTrafficDaily()) }

    suspend fun getClientConfig(
        region: String? = null,
        node: String? = null,
        profile: String? = null,
        routeMode: String? = null,
    ): ClientConfigData {
        val normalizedRouteMode = AppRouteMode.normalizeStoredRouteMode(routeMode)
        if (normalizedRouteMode != tokenStore.getRouteMode()) {
            tokenStore.saveRouteMode(normalizedRouteMode)
        }
        val resolvedProfile = profile ?: inferClientProfile(region)
        return callApiRead("client_config") { unwrap(api.getClientConfig(region, node, resolvedProfile, normalizedRouteMode)) }
    }

    /** 节点地区（含 cn）仅用于筛节点，不决定 profile；默认 overseas_weak。 */
    private fun inferClientProfile(@Suppress("UNUSED_PARAMETER") region: String?): String = "overseas_weak"

    fun migrateRouteModeToFull() {
        val normalized = AppRouteMode.normalizeStoredRouteMode(tokenStore.getRouteMode())
        if (normalized != tokenStore.getRouteMode()) {
            tokenStore.saveRouteMode(normalized)
        }
    }

    fun getSavedRouteMode(): String = tokenStore.getRouteMode()

    fun saveRouteMode(mode: String) {
        tokenStore.saveRouteMode(mode)
        if (AppRouteMode.isDomesticDirectEnabled(mode)) {
            MihomoGeoAssetManager.scheduleInstall(appContext)
        }
    }

    fun isDomesticDirectEnabled(): Boolean =
        AppRouteMode.isDomesticDirectEnabled(tokenStore.getRouteMode())

    suspend fun checkForUpdate(): com.vpn.member.data.api.ClientVersionData = callApiRead("client_version") {
        val update = unwrap(
            api.getClientVersion(
                platform = "android",
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
            ),
        )
        val normalizedUrl = update.download_url?.let { resolveDownloadUrl(it) }
        update.copy(download_url = normalizedUrl)
    }

    suspend fun checkForUpdateAndRecord(): com.vpn.member.data.api.ClientVersionData {
        val update = checkForUpdate()
        preferences.setLastUpdateCheckAt(System.currentTimeMillis())
        return update
    }

    fun shouldRunPeriodicUpdateCheck(): Boolean =
        com.vpn.member.update.AppUpdateChecker.shouldRunPeriodicCheck(preferences.getLastUpdateCheckAt())

    fun isUpdateDismissed(versionCode: Int): Boolean =
        versionCode > 0 && preferences.getDismissedUpdateVersionCode() == versionCode

    fun dismissUpdate(versionCode: Int) {
        if (versionCode > 0) {
            preferences.setDismissedUpdateVersionCode(versionCode)
        }
    }

    private fun resolveDownloadUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val baseUrl = BuildConfig.APP_BASE_URL.trimEnd('/')
        val normalized = if (url.startsWith("/")) url else "/$url"
        return baseUrl + normalized
    }

    suspend fun buildClashSubscriptionUrl(profile: String? = null): String = callApiRead("subscription_token") {
        val token = unwrap(api.getSubscriptionToken()).token
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        val profileQuery = profile?.takeIf { it.isNotBlank() }?.let { "&profile=$it" }.orEmpty()
        "${BuildConfig.API_BASE_URL}subscription/clash?token=$encodedToken$profileQuery"
    }

    fun saveRegion(region: String?) {
        tokenStore.saveRegion(region)
    }

    fun getSavedRegion(): String? = tokenStore.getRegion()

    fun saveNode(nodeName: String?) {
        tokenStore.saveNode(nodeName)
    }

    fun getSavedNode(): String? = tokenStore.getNode()

    /** 经 VPN mixed-port 调自研 `/client/exit-ip` 时使用的鉴权上下文。 */
    fun exitIpProbeContext(): ExitIpProbeContext =
        ExitIpProbeContext(
            apiBaseUrl = BuildConfig.API_BASE_URL,
            authToken = tokenStore.getJwt(),
        )

    private fun unwrapAuth(resp: ApiResponse<AuthData>): AuthData {
        val data = unwrap(resp)
        tokenStore.saveJwt(data.token)
        applyAppDebugFlag(data.user.app_debug_enabled)
        return data
    }

    private fun <T> unwrap(resp: ApiResponse<T>): T {
        if (resp.code == 200 && resp.data != null) {
            return resp.data
        }
        throw appExceptionFromResponse(resp)
    }

    private fun ensureApiSuccess(resp: ApiResponse<*>) {
        if (resp.code == 200) return
        throw appExceptionFromResponse(resp)
    }

    private fun appExceptionFromResponse(resp: ApiResponse<*>): AppException =
        AppException(
            userMessage = mapApiMessage(resp.message, resp.app_code),
            appCode = resp.app_code,
            retryable = resp.retryable ?: (resp.code >= 500 || resp.code == 429),
            traceId = resp.trace_id,
        )

    private fun mapApiMessage(message: String, appCode: String?): String =
        when (appCode) {
            "LOGIN_ON_ANOTHER_DEVICE" -> message.ifBlank { "账号已在其他设备登录，请重新登录" }
            "SESSION_REVOKED" -> message.ifBlank { "登录状态已失效，请重新登录" }
            "LOGIN_DENIED_NEW_DEVICE" -> message.ifBlank { "当前账号已达到在线设备上限，请先在其他设备退出后再试" }
            "INSUFFICIENT_BALANCE" -> message.ifBlank { "余额不足，请先充值" }
            "RECHARGE_DISABLED" -> message.ifBlank { "USDT 充值暂未开放" }
            "RECHARGE_AMOUNT_TOO_LOW" -> message.ifBlank { "充值金额低于最低限额" }
            "RECHARGE_AMOUNT_TOO_HIGH" -> message.ifBlank { "充值金额超过单笔上限" }
            "TXID_ALREADY_USED" -> message.ifBlank { "该交易哈希已使用" }
            "RECHARGE_INVALID_STATUS" -> when {
                message.contains("付款", ignoreCase = true) -> message
                message.contains("截图", ignoreCase = true) -> message
                else -> message.ifBlank { "当前充值单状态不可操作" }
            }
            "RECHARGE_ORDER_EXPIRED" -> message.ifBlank { "充值单已过期，请重新发起" }
            else -> translateApiMessage(message)
        }

    private fun translateApiMessage(message: String): String {
        val normalized = message.trim()
        if (normalized.isBlank()) return ""
        val lower = normalized.lowercase()
        return when {
            lower == "invalid credentials" -> "邮箱或密码错误"
            lower == "invalid request" -> "请求参数无效"
            lower.contains("email already exists") ||
                lower.contains("duplicate") && lower.contains("email") -> "该邮箱已注册"
            lower.contains("user not found") -> "用户不存在"
            lower.contains("invalid email") -> "邮箱格式不正确"
            lower.contains("password") && lower.contains("at least") -> "密码长度不符合要求"
            lower.contains("invalid old password") ||
                lower.contains("old password") ||
                lower.contains("current password") -> "当前密码错误"
            lower.contains("unauthorized") ||
                lower.contains("authorization header required") ||
                lower.contains("invalid token") ||
                lower.contains("token expired") -> "登录状态已失效，请重新登录"
            lower.contains("insufficient balance") -> "余额不足，请先充值"
            lower.contains("package not found") -> "套餐不存在"
            lower.contains("order not found") -> "订单不存在"
            lower.contains("order is not pending") -> "订单不是待支付状态"
            lower.contains("invalid payment method") -> "支付方式无效"
            lower.contains("no active subscription") -> "暂无有效套餐，请先购买"
            lower.contains("subscription expired") -> "套餐已过期，请续费"
            lower.contains("traffic quota exceeded") -> "流量已用尽，请续费"
            lower.contains("no available nodes") -> "暂无可用节点，请稍后重试"
            lower.contains("network") -> "网络异常，请稍后重试"
            else -> normalized
        }
    }

    suspend fun sendHeartbeat(
        vpnConnected: Boolean,
        probeStatus: String? = null,
        connectedNode: String? = null,
        probeLatencyMs: Int? = null,
        exitIp: String? = null,
        exitCountry: String? = null,
        exitCity: String? = null,
    ): SessionHeartbeatData =
        callApi {
            unwrap(
                api.sendHeartbeat(
                    SessionHeartbeatRequest(
                        vpn_connected = vpnConnected,
                        probe_status = probeStatus,
                        connected_node = connectedNode,
                        probe_latency_ms = probeLatencyMs,
                        exit_ip = exitIp,
                        exit_country = exitCountry,
                        exit_city = exitCity,
                    ),
                ),
            )
        }

    suspend fun getMySessions(): MemberSessionsData = callApiRead("sessions") { unwrap(api.getMySessions()) }

    suspend fun revokeMySession(sessionId: String): MemberSessionsData =
        callApi { unwrap(api.revokeMySession(sessionId)) }

    suspend fun getConnectDashboard(selectedNode: String? = null): ConnectDashboardData =
        callApiRead("connect_dashboard") { unwrap(api.getConnectDashboard(selectedNode)) }

    suspend fun getUserPreferences(): UserPreferencesData = callApiRead("user_preferences") { unwrap(api.getUserPreferences()) }

    /** @deprecated 请使用 [updateUserPreferences] */
    suspend fun updateUserPreferencesIpMode(mode: String): UserPreferencesData =
        updateUserPreferences(UserPreferencesUpdate(ip_binding_mode = mode))

    suspend fun updateUserPreferences(update: UserPreferencesUpdate): UserPreferencesData =
        callApi { unwrap(api.updateUserPreferences(update)) }

    private fun mapThrowable(error: Throwable): Throwable {
        if (error is SessionInvalidatedException) throw error
        if (error is AppException) return error
        if (error is HttpException) {
            val body = error.response()?.errorBody()?.string().orEmpty()
            val appCode = SessionAuth.parseAppCode(body)
            val message = SessionAuth.parseMessage(body).orEmpty()
            val retryable = parseRetryable(body, error.code())
            val traceID = parseTraceID(body)
            if (error.code() == 401) {
                val path = error.response()?.raw()?.request?.url?.encodedPath.orEmpty()
                if (SessionAuth.shouldInvalidateSession(path, hadAuth = true, appCode)) {
                    if (!tokenStore.getJwt().isNullOrBlank()) {
                        SessionAuth.invalidateIfNeeded(
                            tokenStore = tokenStore,
                            path = path,
                            hadAuth = true,
                            body = body,
                            appCode = appCode,
                        )
                    }
                    return AppException(
                        userMessage = SessionAuth.sessionInvalidationMessage(body, appCode),
                        appCode = appCode,
                        retryable = false,
                        traceId = traceID,
                    )
                }
            }
            if (appCode != null || message.isNotBlank()) {
                return AppException(
                    userMessage = mapApiMessage(message, appCode),
                    appCode = appCode,
                    retryable = retryable,
                    traceId = traceID,
                )
            }
        }
        return error
    }

    private fun parseRetryable(body: String, httpCode: Int): Boolean {
        runCatching { JSONObject(body).optBoolean("retryable", httpCode >= 500 || httpCode == 429) }
            .getOrNull()
            ?.let { return it }
        return httpCode >= 500 || httpCode == 429
    }

    private fun parseTraceID(body: String): String? =
        runCatching { JSONObject(body).optString("trace_id").takeIf { it.isNotBlank() } }.getOrNull()
}

data class ProofUploadResult(
    val url: String,
    val fileName: String,
)
