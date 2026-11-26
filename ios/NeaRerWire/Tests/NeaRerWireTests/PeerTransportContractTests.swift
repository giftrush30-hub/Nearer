import XCTest
@testable import NeaRerTransport

final class PeerTransportContractTests: XCTestCase {
    func testDiscoveredBonjourEndpointIsNeverTrustedAsIdentity() {
        XCTAssertFalse(NearbyTransportSecurityBoundary.mayTrustDiscoveredEndpointWithoutHandshake())
    }

    func testTransportPeerMarksEndpointAsAdapterLocal() {
        let peer = NearbyTransportPeer(endpointID: "bonjour:device", transport: "network-framework")
        XCTAssertEqual(peer.endpointID, "bonjour:device")
        XCTAssertEqual(peer.transport, "network-framework")
    }
}
