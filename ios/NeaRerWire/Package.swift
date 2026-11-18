// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NeaRerWire",
    platforms: [.iOS(.v13), .macOS(.v12)],
    products: [.library(name: "NeaRerWire", targets: ["NeaRerWire"])],
    targets: [
        .target(name: "NeaRerWire"),
        .testTarget(name: "NeaRerWireTests", dependencies: ["NeaRerWire"])
    ],
    swiftLanguageVersions: [.v5]
)
