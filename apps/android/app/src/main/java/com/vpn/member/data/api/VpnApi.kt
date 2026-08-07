package com.vpn.member.data.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

interface VpnApi {
    @GET("auth/registration-config")
    suspend fun getRegistrationConfig(): ApiResponse<RegistrationConfigData>

    @GET("auth/session-config")
    suspend fun getSessionConfig(): ApiResponse<SessionConfigData>

    @POST("auth/email-code/send")
    suspend fun sendEmailCode(@Body body: SendEmailCodeRequest): ApiResponse<Any>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): ApiResponse<Any>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): ApiResponse<Any>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<AuthData>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<AuthData>

    @GET("users/me")
    suspend fun getMe(): ApiResponse<UserBrief>

    @POST("users/me/app-debug-logs")
    suspend fun uploadAppDebugLogs(@Body body: AppDebugLogUploadRequest): ApiResponse<AppDebugLogUploadResult>

    @PUT("users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): ApiResponse<Any>

    @GET("packages")
    suspend fun getPackages(): ApiResponse<PackagesData>

    @POST("orders")
    suspend fun createOrder(@Body body: CreateOrderRequest): ApiResponse<OrderItem>

    @POST("orders/{id}/pay")
    suspend fun payOrder(@Path("id") orderId: Long): ApiResponse<Any>

    @GET("orders/{id}/status")
    suspend fun getOrderStatus(@Path("id") orderId: Long): ApiResponse<OrderStatusData>

    @GET("orders")
    suspend fun getOrders(): ApiResponse<OrdersData>

    @GET("subscription/active")
    suspend fun getActiveSubscription(): ApiResponse<ActiveSubscriptionData>

    @GET("subscription/usage")
    suspend fun getSubscriptionUsage(): ApiResponse<SubscriptionUsage>

    @GET("subscription/token")
    suspend fun getSubscriptionToken(): ApiResponse<SubscriptionTokenData>

    @GET("subscription/regions")
    suspend fun getRegions(@Query("token") token: String? = null): ApiResponse<RegionsData>

    @GET("client/config")
    suspend fun getClientConfig(
        @Query("region") region: String? = null,
        @Query("node") node: String? = null,
        @Query("profile") profile: String? = null,
        @Query("route_mode") routeMode: String? = null,
    ): ApiResponse<ClientConfigData>

    @GET("client/version")
    suspend fun getClientVersion(
        @Query("platform") platform: String = "android",
        @Query("version_code") versionCode: Int,
        @Query("version_name") versionName: String,
    ): ApiResponse<ClientVersionData>

    @GET("nodes")
    suspend fun getNodes(): ApiResponse<NodesData>

    @GET("nodes/{id}/test/latency")
    suspend fun testNodeLatency(@Path("id") nodeId: Long): ApiResponse<NodeLatencyData>

    @POST("nodes/test/batch-latency")
    suspend fun batchTestLatency(@Body body: BatchLatencyRequest): ApiResponse<BatchLatencyData>

    @GET("traffic/summary")
    suspend fun getTrafficSummary(
        @Query("start_time") startTime: String? = null,
        @Query("end_time") endTime: String? = null,
    ): ApiResponse<TrafficSummary>

    @GET("traffic/daily")
    suspend fun getTrafficDaily(
        @Query("start_time") startTime: String? = null,
        @Query("end_time") endTime: String? = null,
    ): ApiResponse<List<DailyTrafficItem>>

    @GET("payment-methods")
    suspend fun getPaymentMethods(): ApiResponse<PaymentMethodsData>

    @Multipart
    @POST("recharge-orders/proof-upload")
    suspend fun uploadRechargeProof(@Part file: MultipartBody.Part): ApiResponse<ProofUploadData>

    @POST("recharge-orders")
    suspend fun createRechargeOrder(@Body body: CreateRechargeRequest): ApiResponse<CreateRechargeData>

    @GET("recharge-orders")
    suspend fun getRechargeOrders(): ApiResponse<RechargeOrdersData>

    @GET("recharge-orders/{id}")
    suspend fun getRechargeOrder(@Path("id") id: Long): ApiResponse<RechargeOrderItem>

    @POST("recharge-orders/{id}/submit")
    suspend fun submitRechargeOrder(
        @Path("id") id: Long,
        @Body body: SubmitRechargeRequest,
    ): ApiResponse<RechargeOrderItem>

    @POST("recharge-orders/{id}/transfer-hint")
    suspend fun saveRechargeTransferHint(
        @Path("id") id: Long,
        @Body body: TransferHintRequest,
    ): ApiResponse<RechargeOrderItem>

    @POST("recharge-orders/{id}/cancel")
    suspend fun cancelRechargeOrder(@Path("id") id: Long): ApiResponse<Any>

    @POST("session/heartbeat")
    suspend fun sendHeartbeat(@Body body: SessionHeartbeatRequest): ApiResponse<SessionHeartbeatData>

    @GET("users/me/sessions")
    suspend fun getMySessions(): ApiResponse<MemberSessionsData>

    @POST("users/me/sessions/{session_id}/revoke")
    suspend fun revokeMySession(@Path("session_id") sessionId: String): ApiResponse<MemberSessionsData>

    @GET("users/me/connect-dashboard")
    suspend fun getConnectDashboard(@Query("selected_node") selectedNode: String? = null): ApiResponse<ConnectDashboardData>

    @GET("users/me/preferences")
    suspend fun getUserPreferences(): ApiResponse<UserPreferencesData>

    @PUT("users/me/preferences")
    suspend fun updateUserPreferences(@Body body: UserPreferencesUpdate): ApiResponse<UserPreferencesData>

    @GET("tickets")
    suspend fun getMyTickets(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): ApiResponse<TicketsData>

    @GET("tickets/{id}")
    suspend fun getTicketById(@Path("id") id: Long): ApiResponse<TicketItem>

    @POST("tickets")
    suspend fun createTicket(@Body body: CreateTicketRequest): ApiResponse<TicketItem>

    @POST("tickets/{id}/replies")
    suspend fun addTicketReply(
        @Path("id") id: Long,
        @Body body: AddTicketReplyRequest,
    ): ApiResponse<TicketReplyItem>

    @GET("support-config")
    suspend fun getSupportConfig(): ApiResponse<SupportConfigData>

    @POST("client/push-token")
    suspend fun registerPushToken(@Body body: PushTokenRequest): ApiResponse<Any>
}
