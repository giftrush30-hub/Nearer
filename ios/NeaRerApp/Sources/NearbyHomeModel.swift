import Foundation
import SwiftUI
import NeaRerTransport

@MainActor
final class NearbyHomeModel: ObservableObject {
    struct Peer: Identifiable, Equatable {
        let id: String
        let transport: String
    }

    @Published private(set) var peerID = "Preparing identity…"
    @Published private(set) var peers: [Peer] = []
    @Published private(set) var status = "Nearby discovery is stopped"
    @Published private(set) var isSearching = false
    @Published private(set) var connectedEndpoint: String?
    @Published private(set) var identityError: String?

    private var identity: NeaRerLocalIdentity?
    private var transport: NetworkFrameworkPeerTransport?

    init() {
        do {
            let loaded = try NeaRerIdentityVault.loadOrCreate()
            identity = loaded
            peerID = loaded.peerID
            status = "Your NeaRer identity is ready"
        } catch {
            identityError = error.localizedDescription
            status = "Secure identity could not be created"
        }
    }

    func startDiscovery() {
        guard let identity else {
            status = "Identity setup must succeed before discovery"
            return
        }
        guard transport == nil else { return }
        do {
            let adapter = try NetworkFrameworkPeerTransport(peerID: identity.peerID)
            adapter.onPeerFound = { [weak self] peer in
                Task { @MainActor in
                    guard let self, peer.endpointID != self.peerID else { return }
                    if !self.peers.contains(where: { $0.id == peer.endpointID }) {
                        self.peers.append(Peer(id: peer.endpointID, transport: peer.transport))
                    }
                }
            }
            adapter.onPeerLost = { [weak self] peer in
                Task { @MainActor in self?.peers.removeAll { $0.id == peer.endpointID } }
            }
            adapter.onStateChanged = { [weak self] state in
                Task { @MainActor in
                    guard let self else { return }
                    switch state {
                    case .idle: self.status = "Nearby discovery is stopped"
                    case .advertising: self.status = "Advertising your NeaRer presence"
                    case .browsing: self.status = "Searching for nearby NeaRer devices"
                    case .connected: self.status = "Transport connected. Secure peer verification is still required."
                    case .failed(let message): self.status = message
                    }
                }
            }
            adapter.onFrameReceived = { [weak self] _, _ in
                Task { @MainActor in self?.status = "A peer sent data. Secure handshake integration is required before processing it." }
            }
            transport = adapter
            try adapter.start()
            isSearching = true
            status = "Starting local discovery…"
        } catch {
            status = "Could not start nearby discovery: \(error.localizedDescription)"
        }
    }

    func connect(to peer: Peer) {
        connectedEndpoint = peer.id
        transport?.connect(to: peer.id)
        status = "Connecting to nearby device. Identity verification has not completed."
    }

    func stopDiscovery() {
        transport?.stop()
        transport = nil
        peers.removeAll()
        connectedEndpoint = nil
        isSearching = false
    }
}
