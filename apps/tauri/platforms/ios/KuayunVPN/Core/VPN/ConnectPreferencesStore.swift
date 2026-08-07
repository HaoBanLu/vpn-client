import Foundation

/// 选路偏好持久化，对齐 Tauri localStorage。
enum ConnectPreferencesStore {
    private enum Key {
        static let region = "kuayun_ios_region"
        static let node = "kuayun_ios_node"
        static let scenario = "kuayun_ios_scenario"
        static let routeMode = "kuayun_ios_route_mode"
    }

    static var selectedRegion: String? {
        get { UserDefaults.standard.string(forKey: Key.region) }
        set { UserDefaults.standard.set(newValue, forKey: Key.region) }
    }

    static var selectedNode: String? {
        get { UserDefaults.standard.string(forKey: Key.node) }
        set { UserDefaults.standard.set(newValue, forKey: Key.node) }
    }

    static var connectionScenario: ConnectionScenario {
        get { ConnectionScenario.normalize(UserDefaults.standard.string(forKey: Key.scenario)) }
        set { UserDefaults.standard.set(newValue.rawValue, forKey: Key.scenario) }
    }

    static var routeMode: AppRouteMode {
        get {
            let raw = UserDefaults.standard.string(forKey: Key.routeMode) ?? AppRouteMode.full.rawValue
            return AppRouteMode(rawValue: raw) ?? .full
        }
        set { UserDefaults.standard.set(newValue.rawValue, forKey: Key.routeMode) }
    }
}
