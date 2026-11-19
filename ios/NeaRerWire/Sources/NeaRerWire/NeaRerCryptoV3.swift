#if canImport(CryptoKit)
import Foundation
import CryptoKit

/// Apple implementation of NeaRer's current Android P-256 / HKDF / AES-GCM chat session.
/// Persist and protect private keys with Keychain/Secure Enclave outside this value-only API.
public enum NeaRerCryptoV3 {
    public struct SessionKeys: Equatable {
        public let sendKey: Data
        public let receiveKey: Data
    }

    public static func signDetached(_ transcript: Data, using privateKey: P256.Signing.PrivateKey) throws -> Data {
        try privateKey.signature(for: transcript).derRepresentation
    }

    public static func verifyDetached(publicKeyDER: Data, transcript: Data, signatureDER: Data) -> Bool {
        guard let publicKey = try? P256.Signing.PublicKey(derRepresentation: publicKeyDER),
              let signature = try? P256.Signing.ECDSASignature(derRepresentation: signatureDER) else {
            return false
        }
        return publicKey.isValidSignature(signature, for: transcript)
    }

    /// Matches Android SecurePeerIdentity.signChallenge / verifyChallenge exactly.
    /// Android signs a domain-separated message containing the peer ID, SHA-256
    /// fingerprint of its signing key, and base64 of the v3 transcript challenge.
    public static func signChallenge(peerID: String, signingPublicKeyDER: Data,
                                     transcript: Data, using privateKey: P256.Signing.PrivateKey) throws -> Data {
        let message = try peerProofMessage(peerID: peerID, signingPublicKeyDER: signingPublicKeyDER,
                                           transcript: transcript)
        return try privateKey.signature(for: message).derRepresentation
    }

    public static func verifyChallenge(peerID: String, signingPublicKeyDER: Data,
                                        transcript: Data, signatureDER: Data) -> Bool {
        guard let message = try? peerProofMessage(peerID: peerID,
                                                  signingPublicKeyDER: signingPublicKeyDER,
                                                  transcript: transcript),
              let publicKey = try? P256.Signing.PublicKey(derRepresentation: signingPublicKeyDER),
              let signature = try? P256.Signing.ECDSASignature(derRepresentation: signatureDER) else {
            return false
        }
        return publicKey.isValidSignature(signature, for: message)
    }

    private static func peerProofMessage(peerID: String, signingPublicKeyDER: Data,
                                         transcript: Data) throws -> Data {
        guard NeaRerIdentity.normalize(peerID) == peerID,
              (64...512).contains(signingPublicKeyDER.count) else {
            throw NeaRerWireError.invalidField("peer proof identity")
        }
        let fingerprint = Data(SHA256.hash(data: signingPublicKeyDER))
            .map { String(format: "%02x", $0) }.joined()
        let challenge = transcript.base64EncodedString()
        return Data("NeaRer-peer-proof-v1\n\(peerID)\n\(fingerprint)\n\(challenge)".utf8)
    }

    /// Matches Android SecurePeerIdentity.signChallenge() including its proof domain,
    /// SHA-256 public-key fingerprint, and Base64 transcript encoding.
    public static func signPeerProof(peerID: String, transcript: Data,
                                     using privateKey: P256.Signing.PrivateKey) throws -> Data {
        let publicDER = privateKey.publicKey.derRepresentation
        return try privateKey.signature(for: peerProofMessage(peerID: peerID, publicKeyDER: publicDER,
                                                               transcript: transcript)).derRepresentation
    }

    public static func verifyPeerProof(peerID: String, publicKeyDER: Data,
                                       transcript: Data, signatureDER: Data) -> Bool {
        guard NeaRerIdentity.normalize(peerID) != nil,
              let publicKey = try? P256.Signing.PublicKey(derRepresentation: publicKeyDER),
              let signature = try? P256.Signing.ECDSASignature(derRepresentation: signatureDER) else {
            return false
        }
        return publicKey.isValidSignature(
            signature,
            for: peerProofMessage(peerID: peerID, publicKeyDER: publicKeyDER, transcript: transcript)
        )
    }

    private static func peerProofMessage(peerID: String, publicKeyDER: Data, transcript: Data) -> Data {
        let fingerprint = SHA256.hash(data: publicKeyDER).map { String(format: "%02x", $0) }.joined()
        let challenge = transcript.base64EncodedString()
        return Data("NeaRer-peer-proof-v1\n\(peerID)\n\(fingerprint)\n\(challenge)".utf8)
    }

    /// Derives the same 64-byte HKDF output as Android NearbyMessageCrypto.deriveSession().
    public static func deriveSession(localID: String, remoteID: String,
                                     localPrivateKey: P256.KeyAgreement.PrivateKey,
                                     remotePublicKeyDER: Data, transcript: Data) throws -> SessionKeys {
        guard let normalizedLocal = NeaRerIdentity.normalize(localID),
              let normalizedRemote = NeaRerIdentity.normalize(remoteID),
              normalizedLocal == localID, normalizedRemote == remoteID,
              localID != remoteID else { throw NeaRerWireError.invalidPeerID }
        let remotePublicKey = try P256.KeyAgreement.PublicKey(derRepresentation: remotePublicKeyDER)
        let sharedSecret = try localPrivateKey.sharedSecretFromKeyAgreement(with: remotePublicKey)
        let salt = Data(SHA256.hash(data: transcript))
        let info = Data("NeaRer-chat-E2E-v1".utf8)
        let material = sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self, salt: salt, sharedInfo: info, outputByteCount: 64
        )
        let bytes = material.withUnsafeBytes { Data($0) }
        let first = bytes.prefix(32)
        let second = bytes.suffix(32)
        if localID < remoteID {
            return SessionKeys(sendKey: Data(first), receiveKey: Data(second))
        }
        return SessionKeys(sendKey: Data(second), receiveKey: Data(first))
    }

    /// AES-GCM payload bytes match Java Cipher.doFinal(): ciphertext followed by the 16-byte tag.
    public static func seal(session: SessionKeys, senderID: String, recipientID: String,
                            messageID: String, plaintext: Data) throws -> SecurePacketV1 {
        guard session.sendKey.count == 32,
              NeaRerIdentity.normalize(senderID) != nil,
              NeaRerIdentity.normalize(recipientID) != nil else {
            throw NeaRerWireError.invalidField("session or peer identity")
        }
        let nonce = AES.GCM.Nonce()
        let key = SymmetricKey(data: session.sendKey)
        let box = try AES.GCM.seal(plaintext, using: key, nonce: nonce,
                                   authenticating: associatedData(senderID, recipientID, messageID))
        var combined = Data(box.ciphertext)
        combined.append(box.tag)
        return try SecurePacketV1(messageID: messageID, nonce: Data(nonce), ciphertext: combined)
    }

    public static func open(session: SessionKeys, recipientID: String, senderID: String,
                            packet: SecurePacketV1) throws -> Data {
        guard session.receiveKey.count == 32 else { throw NeaRerWireError.invalidField("receive key") }
        let tagStart = packet.ciphertext.count - 16
        let ciphertext = packet.ciphertext.prefix(tagStart)
        let tag = packet.ciphertext.suffix(16)
        let nonce = try AES.GCM.Nonce(data: packet.nonce)
        let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
        return try AES.GCM.open(box, using: SymmetricKey(data: session.receiveKey),
                                authenticating: associatedData(senderID, recipientID, packet.messageID))
    }

    private static func associatedData(_ senderID: String, _ recipientID: String, _ messageID: String) -> Data {
        Data("NeaRer-secure-message-v1\n\(senderID)\n\(recipientID)\n\(messageID)".utf8)
    }
}
#else
/// CryptoKit is available on supported Apple OS versions. Linux runs the wire-only test target.
public enum NeaRerCryptoV3 {
    public static let isAvailable = false
}
#endif
