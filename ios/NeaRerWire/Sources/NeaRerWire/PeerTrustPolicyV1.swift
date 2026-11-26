import Foundation
#if canImport(CryptoKit)
import CryptoKit
#endif

public struct NeaRerPeerTrustRecordV1: Equatable, Codable {
    public let peerID: String
    public let signingPublicKeyDER: Data
    public let recipientEncryptionPublicKeyDER: Data
    public let lastBindingSequence: UInt64

    public init(peerID: String, signingPublicKeyDER: Data,
                recipientEncryptionPublicKeyDER: Data, lastBindingSequence: UInt64 = 0) {
        self.peerID = peerID
        self.signingPublicKeyDER = signingPublicKeyDER
        self.recipientEncryptionPublicKeyDER = recipientEncryptionPublicKeyDER
        self.lastBindingSequence = lastBindingSequence
    }
}

public enum NeaRerPeerTrustResultV1: Equatable {
    case firstSeen
    case match
    case keyChanged
}

public enum NeaRerPeerTrustErrorV1: Error, Equatable {
    case invalidPeer
    case invalidKey
    case userApprovalRequired
    case keyChanged
    case replayedBinding
}

/// Pure trust policy shared by the iOS Keychain vault and deterministic tests.
/// First-contact pins are created only after explicit user approval.
public enum NeaRerPeerTrustPolicyV1 {
    public static func evaluate(peerID: String, signingPublicKeyDER: Data,
                                recipientEncryptionPublicKeyDER: Data,
                                saved: NeaRerPeerTrustRecordV1?) throws -> NeaRerPeerTrustResultV1 {
        try validate(peerID: peerID, signing: signingPublicKeyDER, recipient: recipientEncryptionPublicKeyDER)
        guard let saved else { return .firstSeen }
        guard saved.peerID == peerID,
              constantTimeEqual(saved.signingPublicKeyDER, signingPublicKeyDER),
              constantTimeEqual(saved.recipientEncryptionPublicKeyDER, recipientEncryptionPublicKeyDER) else {
            return .keyChanged
        }
        return .match
    }

    public static func approveFirstContact(peerID: String, signingPublicKeyDER: Data,
                                           recipientEncryptionPublicKeyDER: Data,
                                           saved: NeaRerPeerTrustRecordV1?,
                                           userApproved: Bool) throws -> NeaRerPeerTrustRecordV1 {
        guard userApproved else { throw NeaRerPeerTrustErrorV1.userApprovalRequired }
        let result = try evaluate(peerID: peerID, signingPublicKeyDER: signingPublicKeyDER,
                                  recipientEncryptionPublicKeyDER: recipientEncryptionPublicKeyDER,
                                  saved: saved)
        guard result != .keyChanged else { throw NeaRerPeerTrustErrorV1.keyChanged }
        if let saved { return saved }
        return NeaRerPeerTrustRecordV1(
            peerID: peerID, signingPublicKeyDER: signingPublicKeyDER,
            recipientEncryptionPublicKeyDER: recipientEncryptionPublicKeyDER
        )
    }

    public static func acceptBindingSequence(_ sequence: UInt64,
                                             record: NeaRerPeerTrustRecordV1) throws -> NeaRerPeerTrustRecordV1 {
        guard sequence > record.lastBindingSequence else {
            throw NeaRerPeerTrustErrorV1.replayedBinding
        }
        return NeaRerPeerTrustRecordV1(
            peerID: record.peerID,
            signingPublicKeyDER: record.signingPublicKeyDER,
            recipientEncryptionPublicKeyDER: record.recipientEncryptionPublicKeyDER,
            lastBindingSequence: sequence
        )
    }

    private static func validate(peerID: String, signing: Data, recipient: Data) throws {
        guard NeaRerIdentity.normalize(peerID) == peerID else {
            throw NeaRerPeerTrustErrorV1.invalidPeer
        }
        guard isP256(signing), isP256(recipient) else {
            throw NeaRerPeerTrustErrorV1.invalidKey
        }
    }

    private static func isP256(_ der: Data) -> Bool {
        #if canImport(CryptoKit)
        return (try? P256.Signing.PublicKey(derRepresentation: der)) != nil
        #else
        return false
        #endif
    }

    private static func constantTimeEqual(_ lhs: Data, _ rhs: Data) -> Bool {
        guard lhs.count == rhs.count else { return false }
        var difference: UInt8 = 0
        for index in lhs.indices { difference |= lhs[index] ^ rhs[index] }
        return difference == 0
    }
}
