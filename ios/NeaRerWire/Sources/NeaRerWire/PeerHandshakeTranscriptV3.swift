import Foundation

/// Reproduces NearbyMessageCrypto.transcript() byte-for-byte.
/// IDs are restricted to NeaRer's ASCII alphabet, so Swift and Kotlin lexical ordering agree.
public struct NeaRerHandshakeParticipantV3: Equatable {
    public let peerID: String
    public let nonce: Data
    public let ephemeralPublicKeyDER: Data
    public let recipientEncryptionPublicKeyDER: Data

    public init(peerID: String, nonce: Data, ephemeralPublicKeyDER: Data,
                recipientEncryptionPublicKeyDER: Data) throws {
        guard NeaRerIdentity.normalize(peerID) == peerID else { throw NeaRerWireError.invalidPeerID }
        guard nonce.count == 32 else { throw NeaRerWireError.invalidField("nonce") }
        guard !ephemeralPublicKeyDER.isEmpty, ephemeralPublicKeyDER.count <= 512,
              !recipientEncryptionPublicKeyDER.isEmpty, recipientEncryptionPublicKeyDER.count <= 512 else {
            throw NeaRerWireError.invalidField("transcript public key")
        }
        self.peerID = peerID
        self.nonce = nonce
        self.ephemeralPublicKeyDER = ephemeralPublicKeyDER
        self.recipientEncryptionPublicKeyDER = recipientEncryptionPublicKeyDER
    }
}

public enum PeerHandshakeTranscriptV3 {
    private static let domain = Data("NeaRer-Nearby-Peer-Handshake-v3".utf8)

    /// Domain-separated, big-endian, length-prefixed transcript matching Android DataOutputStream.
    public static func encode(_ first: NeaRerHandshakeParticipantV3,
                              _ second: NeaRerHandshakeParticipantV3) throws -> Data {
        guard first.peerID != second.peerID else { throw NeaRerWireError.invalidPeerID }
        let ordered = [first, second].sorted { $0.peerID < $1.peerID }
        var output = domain
        for peer in ordered {
            let id = Data(peer.peerID.utf8)
            try appendShortLength(id.count, to: &output)
            output.append(id)
            guard peer.nonce.count <= 255 else { throw NeaRerWireError.invalidField("nonce") }
            output.append(UInt8(peer.nonce.count))
            output.append(peer.nonce)
            try appendShortLength(peer.ephemeralPublicKeyDER.count, to: &output)
            output.append(peer.ephemeralPublicKeyDER)
            try appendShortLength(peer.recipientEncryptionPublicKeyDER.count, to: &output)
            output.append(peer.recipientEncryptionPublicKeyDER)
        }
        return output
    }

    private static func appendShortLength(_ count: Int, to output: inout Data) throws {
        guard (0...Int(UInt16.max)).contains(count) else {
            throw NeaRerWireError.invalidField("transcript field length")
        }
        output.append(UInt8((count >> 8) & 0xff))
        output.append(UInt8(count & 0xff))
    }
}
