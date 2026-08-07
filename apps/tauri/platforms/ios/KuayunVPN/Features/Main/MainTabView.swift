import SwiftUI

struct MainTabView: View {
    var body: some View {
        TabView {
            ConnectView()
                .tabItem { Label("连接", systemImage: "shield.lefthalf.filled") }

            NodesView()
                .tabItem { Label("节点", systemImage: "server.rack") }

            ProfileView()
                .tabItem { Label("我的", systemImage: "person.crop.circle") }
        }
    }
}
