import Foundation
import NeaRerWire

public struct NearbyTransportPeer: Equatable, Sendable {
    /// Adapter-local endpoint handle; never treat this as an authenticated NeaRer ID.
    public let endpointID: String
    public let transport: String
    public init(endpointID: String, transport: String) {
        self.endpointID = endpointID
        self.transport = transport
    }
}

public enum NearbyTransportState: Equatable, Sendable {
    case idle
    case advertising
    case browsing
    case connected(String)
    case failed(String)
}

public protocol NearbyPeerTransport: AnyObject {
    var onPeerFound: ((NearbyTransportPeer) -> Void)? { get set }
    var onPeerLost: ((NearbyTransportPeer) -> Void)? { get set }
    var onStateChanged: ((NearbyTransportState) -> Void)? { get set }
    var onFrameReceived: ((String, Data) -> Void)? { get set }

    func start() throws
    func connect(to endpointID: String)
    func send(_ frame: Data, to endpointID: String)
    func disconnect(from endpointID: String)
    func stop()
}

/// The only identity that can authorize chat is the authenticated ID from
/// NeaRerPeerHandshake. A Bonjour endpoint string is discovery metadata only.
public enum NearbyTransportSecurityBoundary {
    public static func mayTrustDiscoveredEndpointWithoutHandshake() -> Bool { false }
}
