import Foundation

/// Swift implementation of NeaRer's existing Android v3 handshake and secure-packet envelopes.
/// This module intentionally contains no radio, UI or cryptographic trust-decision code.
public enum NeaRerWireError: Error, Equatable {
    case invalidPeerID
    case invalidKind
    case invalidField(String)
    case invalidPublicKey
    case malformedJSON
}

public enum NeaRerIdentity {
    private static let alphabet = Set("23456789ABCDEFGHJKLMNPQRSTUVWXYZ")

    /// Accepts current 12-character IDs and legacy six-character hexadecimal IDs.
    public static func normalize(_ value: String) -> String? {
        let candidate = value.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if candidate.count == 6,
           candidate.unicodeScalars.allSatisfy({ "0123456789ABCDEF".unicodeScalars.contains($0) }) {
            return candidate
        }
        guard candidate.count == 12,
              candidate.allSatisfy({ alphabet.contains($0) }) else { return nil }
        return candidate
    }
}

public struct PeerHelloV3: Equatable {
    public let peerID: String
    public let signingPublicKeyDER: Data
    public let nonce: Data
    public let ephemeralPublicKeyDER: Data
    public let recipientEncryptionPublicKeyDER: Data

    public init(peerID: String, signingPublicKeyDER: Data, nonce: Data,
                ephemeralPublicKeyDER: Data, recipientEncryptionPublicKeyDER: Data) throws {
        guard NeaRerIdentity.normalize(peerID) == peerID else { throw NeaRerWireError.invalidPeerID }
        guard nonce.count == 32 else { throw NeaRerWireError.invalidField("nonce") }
        guard (64...512).contains(signingPublicKeyDER.count),
              (64...512).contains(ephemeralPublicKeyDER.count),
              (64...512).contains(recipientEncryptionPublicKeyDER.count) else {
            throw NeaRerWireError.invalidField("public key length")
        }
        // This is a wire codec, not a trust decision. The iOS crypto adapter must parse
        // these DER blobs as P-256 keys and verify signatures before accepting a peer.
        self.peerID = peerID
        self.signingPublicKeyDER = signingPublicKeyDER
        self.nonce = nonce
        self.ephemeralPublicKeyDER = ephemeralPublicKeyDER
        self.recipientEncryptionPublicKeyDER = recipientEncryptionPublicKeyDER
    }

    public func encode() throws -> Data {
        let object: [String: Any] = [
            "kind": "peer_hello_v3",
            "peerId": peerID,
            "publicKey": signingPublicKeyDER.base64EncodedString(),
            "nonce": nonce.base64EncodedString(),
            "ephemeralPublicKey": ephemeralPublicKeyDER.base64EncodedString(),
            "recipientEncryptionPublicKey": recipientEncryptionPublicKeyDER.base64EncodedString()
        ]
        return try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
    }

    public static func decode(_ data: Data) throws -> PeerHelloV3 {
        guard data.count <= 4_096,
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NeaRerWireError.malformedJSON
        }
        guard object["kind"] as? String == "peer_hello_v3" else { throw NeaRerWireError.invalidKind }
        func bytes(_ key: String, maximum: Int) throws -> Data {
            guard let encoded = object[key] as? String,
                  let decoded = Data(base64Encoded: encoded),
                  !decoded.isEmpty, decoded.count <= maximum else {
                throw NeaRerWireError.invalidField(key)
            }
            return decoded
        }
        guard let peerID = object["peerId"] as? String else { throw NeaRerWireError.invalidPeerID }
        return try PeerHelloV3(
            peerID: peerID,
            signingPublicKeyDER: bytes("publicKey", maximum: 512),
            nonce: bytes("nonce", maximum: 32),
            ephemeralPublicKeyDER: bytes("ephemeralPublicKey", maximum: 512),
            recipientEncryptionPublicKeyDER: bytes("recipientEncryptionPublicKey", maximum: 512)
        )
    }
}

public struct PeerProofV3: Equatable {
    public let peerID: String
    public let signatureDER: Data

    public init(peerID: String, signatureDER: Data) throws {
        guard NeaRerIdentity.normalize(peerID) != nil else { throw NeaRerWireError.invalidPeerID }
        guard (1...128).contains(signatureDER.count) else { throw NeaRerWireError.invalidField("signature") }
        self.peerID = peerID
        self.signatureDER = signatureDER
    }

    public func encode() throws -> Data {
        try JSONSerialization.data(withJSONObject: [
            "kind": "peer_proof_v3", "peerId": peerID,
            "signature": signatureDER.base64EncodedString()
        ], options: [.sortedKeys])
    }

    public static func decode(_ data: Data) throws -> PeerProofV3 {
        guard data.count <= 1_024,
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NeaRerWireError.malformedJSON
        }
        guard object["kind"] as? String == "peer_proof_v3" else { throw NeaRerWireError.invalidKind }
        guard let peerID = object["peerId"] as? String,
              let encoded = object["signature"] as? String,
              let signature = Data(base64Encoded: encoded) else {
            throw NeaRerWireError.invalidField("proof")
        }
        return try PeerProofV3(peerID: peerID, signatureDER: signature)
    }
}

public struct SecurePacketV1: Equatable {
    public let messageID: String
    public let nonce: Data
    public let ciphertext: Data

    public init(messageID: String, nonce: Data, ciphertext: Data) throws {
        guard !messageID.isEmpty, messageID.utf8.count <= 80 else { throw NeaRerWireError.invalidField("id") }
        guard nonce.count == 12 else { throw NeaRerWireError.invalidField("nonce") }
        guard (16...16_384).contains(ciphertext.count) else { throw NeaRerWireError.invalidField("ciphertext") }
        self.messageID = messageID
        self.nonce = nonce
        self.ciphertext = ciphertext
    }

    public func encode() throws -> Data {
        try JSONSerialization.data(withJSONObject: [
            "kind": "secure_packet_v1", "version": 1, "id": messageID,
            "nonce": nonce.base64EncodedString(), "ciphertext": ciphertext.base64EncodedString()
        ], options: [.sortedKeys])
    }

    public static func decode(_ data: Data) throws -> SecurePacketV1 {
        guard data.count <= 32_768,
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NeaRerWireError.malformedJSON
        }
        guard object["kind"] as? String == "secure_packet_v1",
              let version = object["version"] as? Int, version == 1 else {
            throw NeaRerWireError.invalidKind
        }
        guard let messageID = object["id"] as? String,
              let nonceText = object["nonce"] as? String,
              let cipherText = object["ciphertext"] as? String,
              let nonce = Data(base64Encoded: nonceText),
              let ciphertext = Data(base64Encoded: cipherText) else {
            throw NeaRerWireError.invalidField("secure packet")
        }
        return try SecurePacketV1(messageID: messageID, nonce: nonce, ciphertext: ciphertext)
    }
}
