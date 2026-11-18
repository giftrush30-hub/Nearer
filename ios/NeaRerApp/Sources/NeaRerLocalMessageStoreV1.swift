import Foundation
import CryptoKit
import Security

struct NeaRerStoredMessageV1: Codable, Equatable, Identifiable {
    let id: String
    let peerID: String
    let senderID: String
    let text: String
    let timestampMilliseconds: Int64
}

/// Small encrypted local conversation store. The JSON payload is sealed with
/// AES-GCM; its random 256-bit key is stored in a device-only Keychain item.
enum NeaRerLocalMessageStoreV1 {
    private static let keychainService = "com.nearer.ios.message-store.v1"
    private static let keychainAccount = "database-key"

    static func load() throws -> [NeaRerStoredMessageV1] {
        let url = try storeURL()
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        let encrypted = try Data(contentsOf: url)
        guard !encrypted.isEmpty else { return [] }
        let box = try AES.GCM.SealedBox(combined: encrypted)
        let plaintext = try AES.GCM.open(box, using: SymmetricKey(data: loadOrCreateKey()))
        return try JSONDecoder().decode([NeaRerStoredMessageV1].self, from: plaintext)
    }

    static func append(_ message: NeaRerStoredMessageV1) throws -> [NeaRerStoredMessageV1] {
        var messages = try load()
        guard !messages.contains(where: { $0.id == message.id }) else { return messages }
        messages.append(message)
        let plaintext = try JSONEncoder().encode(messages)
        let box = try AES.GCM.seal(plaintext, using: SymmetricKey(data: loadOrCreateKey()))
        guard let encrypted = box.combined else {
            throw NSError(domain: "com.nearer.message-store", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "Could not seal the local message database"])
        }
        let url = try storeURL()
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try encrypted.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutableURL = url
        try? mutableURL.setResourceValues(values)
        return messages
    }

    private static func storeURL() throws -> URL {
        try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: true
        ).appendingPathComponent("NeaRer/messages-v1.aesgcm")
    }

    private static func loadOrCreateKey() throws -> Data {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecSuccess, let key = result as? Data, key.count == 32 { return key }
        guard status == errSecItemNotFound else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        var key = Data(count: 32)
        let randomStatus = key.withUnsafeMutableBytes {
            SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!)
        }
        guard randomStatus == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(randomStatus))
        }
        var insert = query
        insert.removeValue(forKey: kSecReturnData as String)
        insert.removeValue(forKey: kSecMatchLimit as String)
        insert[kSecValueData as String] = key
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let addStatus = SecItemAdd(insert as CFDictionary, nil)
        if addStatus == errSecDuplicateItem {
            var retry: CFTypeRef?
            let retryStatus = SecItemCopyMatching(query as CFDictionary, &retry)
            guard retryStatus == errSecSuccess, let stored = retry as? Data, stored.count == 32 else {
                throw NSError(domain: NSOSStatusErrorDomain, code: Int(retryStatus))
            }
            key = stored
        } else if addStatus != errSecSuccess {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(addStatus))
        }
        return key
    }
}
