import Foundation
import CryptoKit
import Security

struct NeaRerLocalIdentity {
    let peerID: String
    let signingKey: P256.Signing.PrivateKey
    let recipientEncryptionKey: P256.KeyAgreement.PrivateKey

    var signingPublicKeyDER: Data { signingKey.publicKey.derRepresentation }
    var recipientEncryptionPublicKeyDER: Data { recipientEncryptionKey.publicKey.derRepresentation }
}

/// Stores the NeaRer ID and private key material in device-only Keychain items.
/// This first implementation uses Keychain protection; Secure Enclave-backed keys
/// remain a follow-up hardware capability enhancement.
enum NeaRerIdentityVault {
    private static let service = "com.nearer.ios.identity.v1"
    private static let alphabet = Array("23456789ABCDEFGHJKLMNPQRSTUVWXYZ")

    static func loadOrCreate() throws -> NeaRerLocalIdentity {
        let peerID = try String(data: item(account: "peer-id") {
            let bytes = try randomBytes(count: 12)
            return Data(bytes.map { alphabet[Int($0 & 31)].asciiValue! })
        }, encoding: .utf8).unwrapOrThrow()
        let signingData = try item(account: "signing-p256") {
            try P256.Signing.PrivateKey().rawRepresentation
        }
        let recipientData = try item(account: "recipient-p256") {
            try P256.KeyAgreement.PrivateKey().rawRepresentation
        }
        return NeaRerLocalIdentity(
            peerID: peerID,
            signingKey: try P256.Signing.PrivateKey(rawRepresentation: signingData),
            recipientEncryptionKey: try P256.KeyAgreement.PrivateKey(rawRepresentation: recipientData)
        )
    }

    /// Allocates a monotonic device-local sequence for signed recipient-key bindings.
    /// The value is persisted in Keychain so reconnects cannot replay sequence 1.
    static func nextRecipientBindingSequence() throws -> UInt64 {
        let account = "recipient-binding-sequence"
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        let current: UInt64
        if status == errSecItemNotFound {
            current = 0
        } else if status == errSecSuccess, let data = result as? Data, data.count == 8 {
            current = data.reduce(UInt64(0)) { ($0 << 8) | UInt64($1) }
        } else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        guard current < UInt64(Int64.max) else {
            throw NSError(domain: "com.nearer.identity", code: 2,
                          userInfo: [NSLocalizedDescriptionKey: "Recipient binding sequence is exhausted"])
        }
        let next = current + 1
        var encoded = Data()
        for shift in stride(from: 56, through: 0, by: -8) {
            encoded.append(UInt8((next >> UInt64(shift)) & 0xff))
        }
        if status == errSecItemNotFound {
            var insert = query
            insert.removeValue(forKey: kSecReturnData as String)
            insert.removeValue(forKey: kSecMatchLimit as String)
            insert[kSecValueData as String] = encoded
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            let addStatus = SecItemAdd(insert as CFDictionary, nil)
            guard addStatus == errSecSuccess else {
                throw NSError(domain: NSOSStatusErrorDomain, code: Int(addStatus))
            }
        } else {
            let updateQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: account
            ]
            let update = [kSecValueData as String: encoded]
            let updateStatus = SecItemUpdate(updateQuery as CFDictionary, update as CFDictionary)
            guard updateStatus == errSecSuccess else {
                throw NSError(domain: NSOSStatusErrorDomain, code: Int(updateStatus))
            }
        }
        return next
    }

    private static func item(account: String, create: () throws -> Data) throws -> Data {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecSuccess, let data = result as? Data { return data }
        guard status == errSecItemNotFound else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        let value = try create()
        var insert = query
        insert.removeValue(forKey: kSecReturnData as String)
        insert.removeValue(forKey: kSecMatchLimit as String)
        insert[kSecValueData as String] = value
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let addStatus = SecItemAdd(insert as CFDictionary, nil)
        if addStatus == errSecDuplicateItem {
            var retry: CFTypeRef?
            let retryStatus = SecItemCopyMatching(query as CFDictionary, &retry)
            guard retryStatus == errSecSuccess, let data = retry as? Data else {
                throw NSError(domain: NSOSStatusErrorDomain, code: Int(retryStatus))
            }
            return data
        }
        guard addStatus == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(addStatus))
        }
        return value
    }

    private static func randomBytes(count: Int) throws -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: count)
        let status = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        guard status == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(status)) }
        return bytes
    }
}

private extension Optional where Wrapped == String {
    func unwrapOrThrow() throws -> String {
        guard let value = self else {
            throw NSError(domain: "com.nearer.identity", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "Could not restore the NeaRer identity"])
        }
        return value
    }
}
