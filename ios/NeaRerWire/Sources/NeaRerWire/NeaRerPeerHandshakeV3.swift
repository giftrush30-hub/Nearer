import Foundation
#if canImport(CryptoKit)
import CryptoKit

/// Stateful per-connection adapter for the existing NeaRer v3 authenticated ECDH handshake.
/// Trust-on-first-use pinning and recipient identity binding are separate mandatory gates.
public struct NeaRerPeerHandshakeV3 {
    public let localPeerID: String
    private let signingPrivateKey: P256.Signing.PrivateKey
    private let recipientEncryptionPrivateKey: P256.KeyAgreement.PrivateKey
    private let ephemeralPrivateKey: P256.KeyAgreement.PrivateKey
    private let nonce: Data

    public init(localPeerID: String,
                signingPrivateKey: P256.Signing.PrivateKey,
                recipientEncryptionPrivateKey: P256.KeyAgreement.PrivateKey,
                ephemeralPrivateKey: P256.KeyAgreement.PrivateKey,
                nonce: Data) throws {
        guard NeaRerIdentity.normalize(localPeerID) == localPeerID,
              nonce.count == 32 else {
            throw NeaRerWireError.invalidField("local handshake identity or nonce")
        }
        self.localPeerID = localPeerID
        self.signingPrivateKey = signingPrivateKey
        self.recipientEncryptionPrivateKey = recipientEncryptionPrivateKey
        self.ephemeralPrivateKey = ephemeralPrivateKey
        self.nonce = nonce
    }

    public func makeHello() throws -> PeerHelloV3 {
        try PeerHelloV3(
            peerID: localPeerID,
            signingPublicKeyDER: signingPrivateKey.publicKey.derRepresentation,
            nonce: nonce,
            ephemeralPublicKeyDER: ephemeralPrivateKey.publicKey.derRepresentation,
            recipientEncryptionPublicKeyDER: recipientEncryptionPrivateKey.publicKey.derRepresentation
        )
    }

    public func makeProof(for remoteHello: PeerHelloV3) throws -> PeerProofV3 {
        let challenge = try transcript(with: remoteHello)
        let signature = try NeaRerCryptoV3.signChallenge(
            peerID: localPeerID,
            signingPublicKeyDER: signingPrivateKey.publicKey.derRepresentation,
            transcript: challenge,
            using: signingPrivateKey
        )
        return try PeerProofV3(peerID: localPeerID, signatureDER: signature)
    }

    public func verify(_ remoteProof: PeerProofV3, from remoteHello: PeerHelloV3) -> Bool {
        guard remoteProof.peerID == remoteHello.peerID else { return false }
        guard let challenge = try? transcript(with: remoteHello) else { return false }
        return NeaRerCryptoV3.verifyChallenge(
            peerID: remoteHello.peerID,
            signingPublicKeyDER: remoteHello.signingPublicKeyDER,
            transcript: challenge,
            signatureDER: remoteProof.signatureDER
        )
    }

    /// Short-authentication string to compare on both devices before first-contact approval.
    public func verificationCode(with remoteHello: PeerHelloV3) throws -> String {
        try NeaRerPeerVerificationCodeV1.fromTranscript(transcript(with: remoteHello))
    }

    /// Six-digit short authentication string for a user-confirmed first contact.
    /// Both peers derive the same code from the signed v3 transcript.
    public func verificationCode(with remoteHello: PeerHelloV3) throws -> String {
        let digest = SHA256.hash(data: try transcript(with: remoteHello))
        let bytes = Array(digest.prefix(4))
        let value = (UInt32(bytes[0]) << 24) | (UInt32(bytes[1]) << 16) |
                    (UInt32(bytes[2]) << 8) | UInt32(bytes[3])
        return String(format: "%06u", value % 1_000_000)
    }

    /// Derives session keys only after the caller has verified both proofs,
    /// pinned/approved the remote identity and validated its signed recipient binding.
    public func deriveSessionKeys(with remoteHello: PeerHelloV3) throws -> NeaRerCryptoV3.SessionKeys {
        try NeaRerCryptoV3.deriveSession(
            localID: localPeerID,
            remoteID: remoteHello.peerID,
            localPrivateKey: ephemeralPrivateKey,
            remotePublicKeyDER: remoteHello.ephemeralPublicKeyDER,
            transcript: transcript(with: remoteHello)
        )
    }

    private func transcript(with remoteHello: PeerHelloV3) throws -> Data {
        let local = try NeaRerHandshakeParticipantV3(
            peerID: localPeerID, nonce: nonce,
            ephemeralPublicKeyDER: ephemeralPrivateKey.publicKey.derRepresentation,
            recipientEncryptionPublicKeyDER: recipientEncryptionPrivateKey.publicKey.derRepresentation
        )
        let remote = try NeaRerHandshakeParticipantV3(
            peerID: remoteHello.peerID, nonce: remoteHello.nonce,
            ephemeralPublicKeyDER: remoteHello.ephemeralPublicKeyDER,
            recipientEncryptionPublicKeyDER: remoteHello.recipientEncryptionPublicKeyDER
        )
        return try PeerHandshakeTranscriptV3.encode(local, remote)
    }
}
#else
import Foundation
/// CryptoKit is required for the iOS/macOS authenticated handshake implementation.
public struct NeaRerPeerHandshakeV3 {}
#endif
