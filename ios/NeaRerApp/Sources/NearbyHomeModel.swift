import Foundation
import SwiftUI
import CryptoKit
import Security
import NeaRerWire
import NeaRerTransport

@MainActor
final class NearbyHomeModel: ObservableObject {
    struct Peer: Identifiable, Equatable {
        let id: String
        let transport: String
    }

    struct PendingApproval: Identifiable, Equatable {
        let endpointID: String
        let peerID: String
        let fingerprint: String
        var id: String { endpointID }
    }

    @Published private(set) var peerID = "Preparing identity…"
    @Published private(set) var peers: [Peer] = []
    @Published private(set) var status = "Nearby discovery is stopped"
    @Published private(set) var isSearching = false
    @Published private(set) var connectedEndpoint: String?
    @Published private(set) var identityError: String?
    @Published private(set) var pendingApprovals: [PendingApproval] = []
    @Published private(set) var securePeerIDs: Set<String> = []
    @Published private(set) var messages: [NeaRerStoredMessageV1] = []
    @Published var messageDraft = ""

    private var identity: NeaRerLocalIdentity?
    private var transport: NetworkFrameworkPeerTransport?
    private var handshakes: [String: NeaRerPeerHandshakeV3] = [:]
    private var localHellos: [String: PeerHelloV3] = [:]
    private var remoteHellos: [String: PeerHelloV3] = [:]
    private var remoteProofsVerified = Set<String>()
    private var localBindingsSent = Set<String>()
    private var remoteBindings: [String: NeaRerRecipientIdentityBindingV1] = [:]
    private var sessionKeys: [String: NeaRerCryptoV3.SessionKeys] = [:]
    private var authenticatedPeerIDsByEndpoint: [String: String] = [:]

    init() {
        do {
            let loaded = try NeaRerIdentityVault.loadOrCreate()
            identity = loaded
            peerID = loaded.peerID
            status = "Your NeaRer identity is ready"
            messages = (try? NeaRerLocalMessageStoreV1.load()) ?? []
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
                    case .idle:
                        self.status = "Nearby discovery is stopped"
                    case .advertising:
                        self.status = "Advertising your NeaRer presence"
                    case .browsing:
                        self.status = "Searching for nearby NeaRer devices"
                    case .connected(let endpoint):
                        self.connectedEndpoint = endpoint
                        self.status = "Local link established. Starting secure identity verification…"
                        self.beginHandshake(endpointID: endpoint)
                    case .failed(let message):
                        self.status = message
                    }
                }
            }
            adapter.onFrameReceived = { [weak self] endpoint, frame in
                Task { @MainActor in self?.receiveHandshakeFrame(frame, from: endpoint) }
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
        status = "Connecting to nearby device. Identity verification is required."
    }

    func approvePeer(_ pending: PendingApproval) {
        guard let identity,
              let remoteHello = remoteHellos[pending.endpointID],
              let binding = remoteBindings[pending.endpointID],
              remoteProofsVerified.contains(pending.endpointID),
              binding.deviceID == remoteHello.peerID,
              binding.recipientEncryptionPublicKeyDER == remoteHello.recipientEncryptionPublicKeyDER else {
            status = "Peer verification is incomplete. No trust was saved."
            return
        }
        do {
            _ = try NeaRerPeerTrustVault.approveFirstContact(
                peerID: remoteHello.peerID,
                signingPublicKeyDER: remoteHello.signingPublicKeyDER,
                recipientEncryptionPublicKeyDER: remoteHello.recipientEncryptionPublicKeyDER,
                userApproved: true
            )
            _ = try NeaRerPeerTrustVault.acceptVerifiedBinding(
                binding, nowMilliseconds: Int64(Date().timeIntervalSince1970 * 1_000)
            )
            guard let handshake = handshakes[pending.endpointID] else {
                throw NeaRerPeerTrustErrorV1.invalidPeer
            }
            let keys = try handshake.deriveSessionKeys(with: remoteHello)
            sessionKeys[pending.endpointID] = keys
            authenticatedPeerIDsByEndpoint[pending.endpointID] = remoteHello.peerID
            securePeerIDs.insert(remoteHello.peerID)
            pendingApprovals.removeAll { $0.endpointID == pending.endpointID }
            status = "Secure peer verification completed for \(remoteHello.peerID). You can now exchange encrypted text messages."
        } catch {
            status = "Peer trust was not saved: \(error.localizedDescription)"
        }
    }

    func sendMessage(to remotePeerID: String) {
        let text = messageDraft
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              text.utf8.count <= 16_000,
              let identity,
              let endpoint = authenticatedPeerIDsByEndpoint.first(where: { $0.value == remotePeerID })?.key,
              let keys = sessionKeys[endpoint],
              let transport else {
            status = "A verified connection is required before sending a message."
            return
        }
        do {
            let messageID = UUID().uuidString
            let packet = try NeaRerCryptoV3.seal(
                session: keys, senderID: identity.peerID, recipientID: remotePeerID,
                messageID: messageID, plaintext: Data(text.utf8)
            )
            transport.send(try packet.encode(), to: endpoint)
            let stored = NeaRerStoredMessageV1(
                id: messageID, peerID: remotePeerID, senderID: identity.peerID,
                text: text, timestampMilliseconds: Int64(Date().timeIntervalSince1970 * 1_000)
            )
            messages = try NeaRerLocalMessageStoreV1.append(stored)
            messageDraft = ""
            status = "Encrypted message handed to the nearby connection. Delivery acknowledgement is not implemented yet."
        } catch {
            status = "Message could not be encrypted or stored: \(error.localizedDescription)"
        }
    }

    func rejectPeer(_ pending: PendingApproval) {
        pendingApprovals.removeAll { $0.endpointID == pending.endpointID }
        clearHandshake(pending.endpointID)
        transport?.disconnect(from: pending.endpointID)
        status = "Peer declined. No identity was trusted."
    }

    func stopDiscovery() {
        transport?.stop()
        transport = nil
        peers.removeAll()
        pendingApprovals.removeAll()
        connectedEndpoint = nil
        isSearching = false
        handshakes.removeAll()
        localHellos.removeAll()
        remoteHellos.removeAll()
        remoteProofsVerified.removeAll()
        localBindingsSent.removeAll()
        remoteBindings.removeAll()
        sessionKeys.removeAll()
        authenticatedPeerIDsByEndpoint.removeAll()
        securePeerIDs.removeAll()
        status = "Nearby discovery is stopped"
    }

    private func beginHandshake(endpointID: String) {
        guard let identity, handshakes[endpointID] == nil else { return }
        do {
            let ephemeral = P256.KeyAgreement.PrivateKey()
            var nonce = Data(count: 32)
            let statusCode = nonce.withUnsafeMutableBytes {
                SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!)
            }
            guard statusCode == errSecSuccess else {
                throw NSError(domain: NSOSStatusErrorDomain, code: Int(statusCode))
            }
            let handshake = try NeaRerPeerHandshakeV3(
                localPeerID: identity.peerID,
                signingPrivateKey: identity.signingKey,
                recipientEncryptionPrivateKey: identity.recipientEncryptionKey,
                ephemeralPrivateKey: ephemeral,
                nonce: nonce
            )
            let hello = try handshake.makeHello()
            handshakes[endpointID] = handshake
            localHellos[endpointID] = hello
            try send(hello.encode(), to: endpointID)
        } catch {
            status = "Could not begin secure handshake: \(error.localizedDescription)"
            transport?.disconnect(from: endpointID)
        }
    }

    private func receiveSecureMessage(_ packet: SecurePacketV1, endpointID: String) throws {
        guard let identity,
              let remotePeerID = authenticatedPeerIDsByEndpoint[endpointID],
              let keys = sessionKeys[endpointID] else {
            throw NeaRerPeerTrustErrorV1.userApprovalRequired
        }
        let plaintext = try NeaRerCryptoV3.open(
            session: keys, recipientID: identity.peerID,
            senderID: remotePeerID, packet: packet
        )
        guard let text = String(data: plaintext, encoding: .utf8),
              !text.isEmpty, text.utf8.count <= 16_000 else {
            throw NeaRerWireError.invalidField("message text")
        }
        let stored = NeaRerStoredMessageV1(
            id: packet.messageID, peerID: remotePeerID, senderID: remotePeerID,
            text: text, timestampMilliseconds: Int64(Date().timeIntervalSince1970 * 1_000)
        )
        messages = try NeaRerLocalMessageStoreV1.append(stored)
        status = "Received and saved an encrypted message from \(remotePeerID)."
    }

    private func receiveHandshakeFrame(_ frame: Data, from endpointID: String) {
        guard let object = try? JSONSerialization.jsonObject(with: frame) as? [String: Any],
              let kind = object["kind"] as? String else {
            status = "Rejected an unrecognized peer frame."
            transport?.disconnect(from: endpointID)
            return
        }
        do {
            switch kind {
            case "peer_hello_v3":
                try receiveHello(PeerHelloV3.decode(frame), endpointID: endpointID)
            case "peer_proof_v3":
                try receiveProof(PeerProofV3.decode(frame), endpointID: endpointID)
            case "mesh_identity_binding_v1":
                try receiveBinding(NeaRerRecipientIdentityBindingV1.decode(frame), endpointID: endpointID)
            case "secure_packet_v1":
                try receiveSecureMessage(SecurePacketV1.decode(frame), endpointID: endpointID)
            default:
                status = "Ignored unsupported pre-session frame: \(kind)"
            }
        } catch {
            status = "Secure handshake rejected: \(error.localizedDescription)"
            transport?.disconnect(from: endpointID)
            clearHandshake(endpointID)
        }
    }

    private func receiveHello(_ hello: PeerHelloV3, endpointID: String) throws {
        guard let identity, hello.peerID != identity.peerID,
              let handshake = handshakes[endpointID] else {
            throw NeaRerWireError.invalidPeerID
        }
        if let existing = remoteHellos[endpointID], existing != hello {
            throw NeaRerPeerTrustErrorV1.keyChanged
        }
        remoteHellos[endpointID] = hello
        let proof = try handshake.makeProof(for: hello)
        try send(proof.encode(), to: endpointID)
        status = "Authenticating nearby peer \(hello.peerID)…"
    }

    private func receiveProof(_ proof: PeerProofV3, endpointID: String) throws {
        guard let handshake = handshakes[endpointID],
              let hello = remoteHellos[endpointID],
              handshake.verify(proof, from: hello) else {
            throw NeaRerPeerTrustErrorV1.invalidKey
        }
        remoteProofsVerified.insert(endpointID)
        try sendLocalBinding(to: endpointID)
        try updatePendingApproval(endpointID: endpointID)
    }

    private func sendLocalBinding(to endpointID: String) throws {
        guard !localBindingsSent.contains(endpointID), let identity else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let binding = try NeaRerRecipientIdentityBindingV1.create(
            deviceID: identity.peerID,
            signingPublicKeyDER: identity.signingPublicKeyDER,
            recipientEncryptionPublicKeyDER: identity.recipientEncryptionPublicKeyDER,
            sequence: try NeaRerIdentityVault.nextRecipientBindingSequence(),
            issuedAtMilliseconds: now,
            expiresAtMilliseconds: now + 24 * 60 * 60 * 1_000
        ) { bytes in
            try NeaRerCryptoV3.signDetached(bytes, using: identity.signingKey)
        }
        try send(binding.encode(), to: endpointID)
        localBindingsSent.insert(endpointID)
    }

    private func receiveBinding(_ binding: NeaRerRecipientIdentityBindingV1, endpointID: String) throws {
        guard let hello = remoteHellos[endpointID],
              remoteProofsVerified.contains(endpointID),
              binding.deviceID == hello.peerID,
              binding.recipientEncryptionPublicKeyDER == hello.recipientEncryptionPublicKeyDER,
              binding.verify(
                pinnedSigningPublicKeyDER: hello.signingPublicKeyDER,
                nowMilliseconds: Int64(Date().timeIntervalSince1970 * 1_000)
              ) else {
            throw NeaRerPeerTrustErrorV1.invalidKey
        }
        remoteBindings[endpointID] = binding
        try updatePendingApproval(endpointID: endpointID)
    }

    private func updatePendingApproval(endpointID: String) throws {
        guard let hello = remoteHellos[endpointID],
              remoteProofsVerified.contains(endpointID),
              let binding = remoteBindings[endpointID] else { return }
        let trust = try NeaRerPeerTrustVault.evaluate(
            peerID: hello.peerID,
            signingPublicKeyDER: hello.signingPublicKeyDER,
            recipientEncryptionPublicKeyDER: hello.recipientEncryptionPublicKeyDER
        )
        guard trust != .keyChanged else {
            throw NeaRerPeerTrustErrorV1.keyChanged
        }
        let fingerprint = Data(SHA256.hash(data: hello.signingPublicKeyDER))
            .map { String(format: "%02X", $0) }.joined()
        if !pendingApprovals.contains(where: { $0.endpointID == endpointID }) {
            pendingApprovals.append(PendingApproval(
                endpointID: endpointID, peerID: hello.peerID,
                fingerprint: fingerprint
            ))
        }
        status = "Peer \(hello.peerID) authenticated. Check the fingerprint and approve to trust this device."
        _ = binding
    }

    private func send(_ frame: Data, to endpointID: String) throws {
        guard let transport else { throw TransportFrameError.emptyFrame }
        transport.send(frame, to: endpointID)
    }

    private func clearHandshake(_ endpointID: String) {
        handshakes.removeValue(forKey: endpointID)
        localHellos.removeValue(forKey: endpointID)
        remoteHellos.removeValue(forKey: endpointID)
        remoteProofsVerified.remove(endpointID)
        localBindingsSent.remove(endpointID)
        remoteBindings.removeValue(forKey: endpointID)
        sessionKeys.removeValue(forKey: endpointID)
        authenticatedPeerIDsByEndpoint.removeValue(forKey: endpointID)
    }
}
