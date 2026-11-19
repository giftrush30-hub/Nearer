import Foundation
import Security
import NeaRerWire

/// Device-only Keychain storage for first-contact-approved peer key pins.
/// A discovered Bonjour name is never written here without explicit approval.
enum NeaRerPeerTrustVault {
    private static let service = "com.nearer.ios.peer-trust.v1"

    static func evaluate(peerID: String, signingPublicKeyDER: Data,
                         recipientEncryptionPublicKeyDER: Data) throws -> NeaRerPeerTrustResultV1 {
        let saved = try load(peerID: peerID)
        return try NeaRerPeerTrustPolicyV1.evaluate(
            peerID: peerID, signingPublicKeyDER: signingPublicKeyDER,
            recipientEncryptionPublicKeyDER: recipientEncryptionPublicKeyDER, saved: saved
        )
    }

    /// Call only after the app has shown a verification code/fingerprint and the user approved it.
    static func approveFirstContact(peerID: String, signingPublicKeyDER: Data,
                                    recipientEncryptionPublicKeyDER: Data,
                                    userApproved: Bool) throws -> NeaRerPeerTrustRecordV1 {
        let saved = try load(peerID: peerID)
        let approved = try NeaRerPeerTrustPolicyV1.approveFirstContact(
            peerID: peerID, signingPublicKeyDER: signingPublicKeyDER,
            recipientEncryptionPublicKeyDER: recipientEncryptionPublicKeyDER,
            saved: saved, userApproved: userApproved
        )
        try save(approved)
        return approved
    }

    /// Binding signatures and expiry must be verified before this monotonic sequence is persisted.
    static func acceptVerifiedBinding(_ binding: NeaRerRecipientIdentityBindingV1,
                                      nowMilliseconds: Int64) throws -> NeaRerPeerTrustRecordV1 {
        guard let saved = try load(peerID: binding.deviceID),
              binding.verify(pinnedSigningPublicKeyDER: saved.signingPublicKeyDER,
                             nowMilliseconds: nowMilliseconds),
              binding.recipientEncryptionPublicKeyDER == saved.recipientEncryptionPublicKeyDER else {
            throw NeaRerPeerTrustErrorV1.keyChanged
        }
        let updated = try NeaRerPeerTrustPolicyV1.acceptBindingSequence(
            binding.sequence, record: saved
        )
        try save(updated)
        return updated
    }

    static func pinnedRecord(peerID: String) throws -> NeaRerPeerTrustRecordV1? {
        try load(peerID: peerID)
    }

    private static func load(peerID: String) throws -> NeaRerPeerTrustRecordV1? {
        guard NeaRerIdentity.normalize(peerID) == peerID else {
            throw NeaRerPeerTrustErrorV1.invalidPeer
        }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: peerID,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        return try JSONDecoder().decode(NeaRerPeerTrustRecordV1.self, from: data)
    }

    private static func save(_ record: NeaRerPeerTrustRecordV1) throws {
        let data = try JSONEncoder().encode(record)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: record.peerID
        ]
        let update: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            let addStatus = SecItemAdd(insert as CFDictionary, nil)
            guard addStatus == errSecSuccess else {
                throw NSError(domain: NSOSStatusErrorDomain, code: Int(addStatus))
            }
        } else if status != errSecSuccess {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
    }
}
