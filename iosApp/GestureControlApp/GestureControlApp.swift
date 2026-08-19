import SwiftUI
import GestureControlKit

@main
struct GestureControlApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        Text(GestureControlKit.shared.statusMessage())
            .padding()
    }
}
