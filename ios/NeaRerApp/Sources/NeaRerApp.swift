import SwiftUI

@main
struct NeaRerApp: App {
    @StateObject private var model = NearbyHomeModel()

    var body: some Scene {
        WindowGroup {
            NearbyHomeView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
        }
    }
}
