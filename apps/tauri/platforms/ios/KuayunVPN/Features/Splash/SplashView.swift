import SwiftUI

struct SplashView: View {
    var onFinish: () -> Void

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.04, green: 0.05, blue: 0.09), Color(red: 0.08, green: 0.11, blue: 0.18)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            VStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 20)
                    .fill(LinearGradient(colors: [.cyan, .blue], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 72, height: 72)
                    .overlay {
                        Image(systemName: "cloud.fill")
                            .font(.title)
                            .foregroundStyle(.white)
                    }
                Text("跨云")
                    .font(.title.bold())
                Text("安全加速 · 全球互联")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                ProgressView()
                    .padding(.top, 8)
            }
        }
        .task {
            try? await Task.sleep(nanoseconds: 500_000_000)
            onFinish()
        }
    }
}
