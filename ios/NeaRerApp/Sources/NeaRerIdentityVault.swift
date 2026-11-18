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
            return Data(bytes.map { UInt8(alphabet[Int($0 & 31)].asciiValue!) })
        }(), encoding: .utf8).unwrapOrThrow()
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
