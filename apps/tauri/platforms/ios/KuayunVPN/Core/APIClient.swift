import Foundation

/// 与控制面 `/api/v1` 通信的轻量客户端（Phase A）。
final class APIClient {
    static let shared = APIClient()

    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    private init(session: URLSession = .shared) {
        self.session = session
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }

    func login(email: String, password: String) async throws -> AuthData {
        let body = LoginRequest(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines),
            password: password,
            deviceName: UIDevice.current.name,
            clientPlatform: "ios"
        )
        let data: AuthData = try await request(
            path: "auth/login",
            method: "POST",
            body: body,
            token: nil
        )
        return data
    }

    func fetchCurrentUser(token: String) async throws -> UserBrief {
        try await request(path: "users/me", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchRegions(token: String) async throws -> RegionsData {
        try await request(path: "subscription/regions", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchNodes(token: String) async throws -> NodesData {
        try await request(path: "nodes", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchClientConfig(
        token: String,
        region: String?,
        node: String?,
        routeMode: String?,
        profile: String?
    ) async throws -> ClientConfigData {
        var query: [URLQueryItem] = []
        if let region, !region.isEmpty {
            query.append(URLQueryItem(name: "region", value: region))
        }
        if let node, !node.isEmpty {
            query.append(URLQueryItem(name: "node", value: node))
        }
        if let routeMode, !routeMode.isEmpty {
            query.append(URLQueryItem(name: "route_mode", value: routeMode))
        }
        if let profile, !profile.isEmpty {
            query.append(URLQueryItem(name: "profile", value: profile))
        }
        return try await request(
            path: "client/config",
            method: "GET",
            body: Optional<String>.none,
            token: token,
            query: query
        )
    }

    func forgotPassword(email: String) async throws {
        let _: EmptyData? = try await requestOptional(
            path: "auth/forgot-password",
            method: "POST",
            body: ForgotPasswordRequest(email: email.trimmingCharacters(in: .whitespacesAndNewlines)),
            token: nil
        )
    }

    func sendEmailCode(email: String, purpose: String) async throws {
        struct Body: Encodable { let email: String; let purpose: String }
        let _: EmptyData? = try await requestOptional(
            path: "auth/email-code/send",
            method: "POST",
            body: Body(email: email.trimmingCharacters(in: .whitespacesAndNewlines), purpose: purpose),
            token: nil
        )
    }

    func register(email: String, password: String, emailCode: String? = nil) async throws -> AuthData {
        let body = RegisterRequest(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines),
            password: password,
            emailCode: emailCode,
            deviceType: "mobile",
            clientPlatform: "ios"
        )
        return try await request(path: "auth/register", method: "POST", body: body, token: nil)
    }

    func changePassword(token: String, oldPassword: String, newPassword: String) async throws {
        let _: EmptyData? = try await requestOptional(
            path: "users/me/password",
            method: "PUT",
            body: ChangePasswordRequest(oldPassword: oldPassword, newPassword: newPassword),
            token: token
        )
    }

    func fetchActiveSubscription(token: String) async throws -> SubscriptionActive? {
        try await requestOptional(path: "subscription/active", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchUsage(token: String) async throws -> SubscriptionUsage {
        try await request(path: "subscription/usage", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchPackages(token: String) async throws -> PackagesData {
        try await request(path: "packages", method: "GET", body: Optional<String>.none, token: token)
    }

    func createOrder(token: String, packageId: UInt64) async throws -> OrderCreatedData {
        struct Body: Encodable { let packageId: UInt64; let paymentMethod: String
            enum CodingKeys: String, CodingKey { case packageId = "package_id"; case paymentMethod = "payment_method" }
        }
        return try await request(
            path: "orders",
            method: "POST",
            body: Body(packageId: packageId, paymentMethod: "balance"),
            token: token
        )
    }

    func payOrder(token: String, orderId: UInt64) async throws {
        let _: EmptyData? = try await requestOptional(path: "orders/\(orderId)/pay", method: "POST", body: Optional<String>.none, token: token)
    }

    func fetchOrders(token: String) async throws -> OrdersData {
        try await request(path: "orders", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchTrafficSummary(token: String) async throws -> TrafficSummary {
        try await request(path: "traffic/summary", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchTrafficDaily(token: String) async throws -> [DailyTrafficItem] {
        try await request(path: "traffic/daily", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchUserPreferences(token: String) async throws -> UserPreferencesData {
        try await request(path: "users/me/preferences", method: "GET", body: Optional<String>.none, token: token)
    }

    func updateUserPreferences(token: String, connectionScenario: String) async throws -> UserPreferencesData {
        struct Body: Encodable { let connectionScenario: String
            enum CodingKeys: String, CodingKey { case connectionScenario = "connection_scenario" }
        }
        return try await request(
            path: "users/me/preferences",
            method: "PUT",
            body: Body(connectionScenario: connectionScenario),
            token: token
        )
    }

    func fetchConnectDashboard(token: String, selectedNode: String?) async throws -> ConnectDashboardData {
        var query: [URLQueryItem] = []
        if let selectedNode, !selectedNode.isEmpty {
            query.append(URLQueryItem(name: "selected_node", value: selectedNode))
        }
        return try await request(
            path: "users/me/connect-dashboard",
            method: "GET",
            body: Optional<String>.none,
            token: token,
            query: query
        )
    }

    func sendHeartbeat(token: String, payload: HeartbeatRequest) async throws {
        let _: EmptyData? = try await requestOptional(path: "session/heartbeat", method: "POST", body: payload, token: token)
    }

    func fetchClientVersion(platform: String, versionCode: Int, versionName: String) async throws -> ClientVersionData {
        let query = [
            URLQueryItem(name: "platform", value: platform),
            URLQueryItem(name: "version_code", value: String(versionCode)),
            URLQueryItem(name: "version_name", value: versionName),
        ]
        return try await request(path: "client/version", method: "GET", body: Optional<String>.none, token: nil, query: query)
    }

    func uploadAppDebugLogs(token: String, entries: [AppDebugLogEntry]) async throws {
        let body = AppDebugLogsRequest(entries: entries, deviceId: "ios-\(UIDevice.current.name)")
        let _: EmptyData? = try await requestOptional(path: "users/me/app-debug-logs", method: "POST", body: body, token: token)
    }

    func batchTestLatency(token: String, nodeIds: [UInt64]) async throws -> BatchLatencyData {
        struct Body: Encodable { let nodeIds: [UInt64]
            enum CodingKeys: String, CodingKey { case nodeIds = "node_ids" }
        }
        return try await request(path: "nodes/test/batch-latency", method: "POST", body: Body(nodeIds: nodeIds), token: token)
    }

    func fetchPaymentMethods(token: String) async throws -> PaymentMethodsData {
        try await request(path: "payment-methods", method: "GET", body: Optional<String>.none, token: token)
    }

    func createRechargeOrder(token: String, amountUsdt: Double) async throws -> CreateRechargeData {
        try await request(
            path: "recharge-orders",
            method: "POST",
            body: CreateRechargeRequest(amountUsdt: amountUsdt),
            token: token
        )
    }

    func fetchRechargeOrders(token: String) async throws -> RechargeOrdersData {
        try await request(path: "recharge-orders", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchRechargeOrder(token: String, id: UInt64) async throws -> RechargeOrderItem {
        try await request(path: "recharge-orders/\(id)", method: "GET", body: Optional<String>.none, token: token)
    }

    func uploadRechargeProof(token: String, imageData: Data, filename: String = "proof.jpg") async throws -> String {
        let boundary = "Boundary-\(UUID().uuidString)"
        var urlRequest = URLRequest(url: APIConfig.endpoint("recharge-orders/proof-upload"))
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        urlRequest.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        body.append(Data("--\(boundary)\r\n".utf8))
        body.append(Data("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n".utf8))
        body.append(Data("Content-Type: image/jpeg\r\n\r\n".utf8))
        body.append(imageData)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        urlRequest.httpBody = body

        let (data, response) = try await session.data(for: urlRequest)
        guard let http = response as? HTTPURLResponse else { throw APIClientError.invalidURL }
        if http.statusCode == 401 {
            SessionNotifier.notifySessionExpired()
            throw APIClientError.unauthorized
        }
        let envelope = try decoder.decode(APIEnvelope<ProofUploadData>.self, from: data)
        if envelope.code == 401 {
            SessionNotifier.notifySessionExpired()
            throw APIClientError.unauthorized
        }
        if envelope.code != 0 && envelope.code != 200 {
            throw APIClientError.business(code: envelope.code, message: envelope.message)
        }
        guard let payload = envelope.data else {
            throw APIClientError.business(code: envelope.code, message: envelope.message)
        }
        return payload.url
    }

    func submitRechargeOrder(token: String, id: UInt64, body: RechargeSubmitBody) async throws -> RechargeOrderItem {
        try await request(path: "recharge-orders/\(id)/submit", method: "POST", body: body, token: token)
    }

    func saveRechargeTransferHint(token: String, id: UInt64, body: RechargeSubmitBody) async throws -> RechargeOrderItem {
        try await request(path: "recharge-orders/\(id)/transfer-hint", method: "POST", body: body, token: token)
    }

    func cancelRechargeOrder(token: String, id: UInt64) async throws {
        let _: EmptyData? = try await requestOptional(
            path: "recharge-orders/\(id)/cancel",
            method: "POST",
            body: Optional<String>.none,
            token: token
        )
    }

    func fetchTickets(token: String, page: Int = 1, pageSize: Int = 20) async throws -> TicketsData {
        let query = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "page_size", value: String(pageSize)),
        ]
        return try await request(
            path: "tickets",
            method: "GET",
            body: Optional<String>.none,
            token: token,
            query: query
        )
    }

    func fetchTicket(token: String, id: UInt64) async throws -> TicketItem {
        try await request(path: "tickets/\(id)", method: "GET", body: Optional<String>.none, token: token)
    }

    func createTicket(token: String, title: String, content: String, priority: String = "normal") async throws -> TicketItem {
        try await request(
            path: "tickets",
            method: "POST",
            body: CreateTicketRequest(title: title, content: content, priority: priority),
            token: token
        )
    }

    func addTicketReply(token: String, ticketId: UInt64, content: String) async throws -> TicketReplyItem {
        try await request(
            path: "tickets/\(ticketId)/replies",
            method: "POST",
            body: TicketReplyRequest(content: content),
            token: token
        )
    }

    func fetchSupportConfig(token: String) async throws -> SupportConfigData {
        try await request(path: "support-config", method: "GET", body: Optional<String>.none, token: token)
    }

    func fetchMySessions(token: String) async throws -> MemberSessionsData {
        try await request(path: "users/me/sessions", method: "GET", body: Optional<String>.none, token: token)
    }

    func revokeMySession(token: String, sessionId: String) async throws -> MemberSessionsData {
        try await request(
            path: "users/me/sessions/\(sessionId)/revoke",
            method: "POST",
            body: Optional<String>.none,
            token: token
        )
    }

    func fetchSubscriptionToken(token: String) async throws -> SubscriptionTokenData {
        try await request(path: "subscription/token", method: "GET", body: Optional<String>.none, token: token)
    }

    private struct EmptyData: Decodable {}

    struct OrderCreatedData: Decodable { let id: UInt64 }

    struct BatchLatencyData: Decodable {
        let results: [String: Int]
    }

    private func requestOptional<T: Decodable, B: Encodable>(
        path: String,
        method: String,
        body: B?,
        token: String?
    ) async throws -> T? {
        var components = URLComponents(url: APIConfig.endpoint(path), resolvingAgainstBaseURL: false)
        guard let url = components?.url else { throw APIClientError.invalidURL }
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = method
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token, !token.isEmpty {
            urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body { urlRequest.httpBody = try encoder.encode(body) }
        let (data, response) = try await session.data(for: urlRequest)
        guard let http = response as? HTTPURLResponse else { throw APIClientError.invalidURL }
        if http.statusCode == 401 {
            SessionNotifier.notifySessionExpired()
            throw APIClientError.unauthorized
        }
        let envelope = try decoder.decode(APIEnvelope<T>.self, from: data)
        if envelope.code == 401 {
            SessionNotifier.notifySessionExpired()
            throw APIClientError.unauthorized
        }
        if envelope.code != 0 && envelope.code != 200 {
            throw APIClientError.business(code: envelope.code, message: envelope.message)
        }
        return envelope.data
    }

    private func request<T: Decodable, B: Encodable>(
        path: String,
        method: String,
        body: B?,
        token: String?
    ) async throws -> T {
        try await request(path: path, method: method, body: body, token: token, query: [])
    }

    private func request<T: Decodable, B: Encodable>(
        path: String,
        method: String,
        body: B?,
        token: String?,
        query: [URLQueryItem]
    ) async throws -> T {
        var components = URLComponents(url: APIConfig.endpoint(path), resolvingAgainstBaseURL: false)
        if !query.isEmpty {
            components?.queryItems = query
        }
        guard let url = components?.url else {
            throw APIClientError.invalidURL
        }
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = method
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token, !token.isEmpty {
            urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            urlRequest.httpBody = try encoder.encode(body)
        }

        let (data, response) = try await session.data(for: urlRequest)
        guard let http = response as? HTTPURLResponse else {
            throw APIClientError.invalidURL
        }

        if http.statusCode == 401 {
            SessionNotifier.notifySessionExpired()
            throw APIClientError.unauthorized
        }

        let envelope = try decoder.decode(APIEnvelope<T>.self, from: data)
        if envelope.code == 401 {
            SessionNotifier.notifySessionExpired()
            throw APIClientError.unauthorized
        }
        if envelope.code != 0 && envelope.code != 200 {
            throw APIClientError.business(code: envelope.code, message: envelope.message)
        }
        guard let payload = envelope.data else {
            throw APIClientError.business(code: envelope.code, message: envelope.message)
        }
        return payload
    }
}

#if canImport(UIKit)
import UIKit
#else
enum UIDevice {
    static var current: Device { Device() }
    struct Device { var name: String { "iOS Simulator" } }
}
#endif
