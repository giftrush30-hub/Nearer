// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NeaRerPlatform",
    platforms: [.iOS(.v14), .macOS(.v12)],
    products: [
        .library(name: "NeaRerWire", targets: ["NeaRerWire"]),
        .library(name: "NeaRerTransport", targets: ["NeaRerTransport"])
    ],
    targets: [
        .target(name: "NeaRerWire"),
        .target(name: "NeaRerTransport", dependencies: ["NeaRerWire"]),
        .testTarget(name: "NeaRerWireTests", dependencies: ["NeaRerWire", "NeaRerTransport"])
    ],
    swiftLanguageVersions: [.v5]
)
