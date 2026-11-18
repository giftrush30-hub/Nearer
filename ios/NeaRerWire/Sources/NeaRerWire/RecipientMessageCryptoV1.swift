#if canImport(CryptoKit)
import Foundation
import CryptoKit

public struct NeaRerRecipientCiphertextV1: Equatable {
    public let ephemeralPublicKeyDER: Data
    public let nonce: Data
    /// Android Cipher.doFinal() format: ciphertext followed by the 16-byte GCM tag.
    public let ciphertextAndTag: Data
}

/// Cross-platform port of Android RecipientMessageCrypto (recipient-addressed mesh E2E).
/// This key is distinct from the direct-session ECDH key and is never a relay key.
public enum NeaRerRecipientMessageCryptoV1 {
    private static let maximumLifetimeMilliseconds: Int64 = 7 * 24 * 60 * 60 * 1_000
    private static let keyInfo = Data("NeaRer-recipient-aes-256-gcm-v1".utf8)

    public static func encrypt(originID: String, destinationID: String, packetID: String,
                               createdAtMilliseconds: Int64, expiresAtMilliseconds: Int64,
                               hopLimit: UInt8, recipientPublicKeyDER: Data,
                               plaintext: Data) throws -> NeaRerRecipientCiphertextV1 {
        try validate(originID: originID, destinationID: destinationID, packetID: packetID,
                     created: createdAtMilliseconds, expires: expiresAtMilliseconds,
                     hopLimit: hopLimit, plaintextCount: plaintext.count)
        let recipientKey = try P256.KeyAgreement.PublicKey(derRepresentation: recipientPublicKeyDER)
        let ephemeral = P256.KeyAgreement.PrivateKey()
        let sharedSecret = try ephemeral.sharedSecretFromKeyAgreement(with: recipientKey)
        let aad = associatedData(originID: originID, destinationID: destinationID, packetID: packetID,
                                 created: createdAtMilliseconds, expires: expiresAtMilliseconds,
                                 hopLimit: hopLimit)
        let salt = Data(SHA256.hash(data: aad))
        let key = HKDF<SHA256>.deriveKey(inputKeyMaterial: sharedSecret, salt: salt,
                                         info: keyInfo, outputByteCount: 32)
        let nonce = AES.GCM.Nonce()
        let box = try AES.GCM.seal(plaintext, using: key, nonce: nonce, authenticating: aad)
        var combined = Data(box.ciphertext)
        combined.append(box.tag)
        return NeaRerRecipientCiphertextV1(
            ephemeralPublicKeyDER: ephemeral.publicKey.derRepresentation,
            nonce: Data(nonce), ciphertextAndTag: combined
        )
    }

    public static func decrypt(recipientPrivateKey: P256.KeyAgreement.PrivateKey,
                               originID: String, destinationID: String, packetID: String,
                               createdAtMilliseconds: Int64, expiresAtMilliseconds: Int64,
                               hopLimit: UInt8, payload: NeaRerRecipientCiphertextV1) throws -> Data {
        try validate(originID: originID, destinationID: destinationID, packetID: packetID,
                     created: createdAtMilliseconds, expires: expiresAtMilliseconds,
                     hopLimit: hopLimit, plaintextCount: nil)
        guard payload.nonce.count == 12, payload.ciphertextAndTag.count >= 16,
              payload.ciphertextAndTag.count <= 24_576 else {
            throw NeaRerWireError.invalidField("recipient ciphertext")
        }
        let ephemeral = try P256.KeyAgreement.PublicKey(derRepresentation: payload.ephemeralPublicKeyDER)
        let sharedSecret = try recipientPrivateKey.sharedSecretFromKeyAgreement(with: ephemeral)
        let aad = associatedData(originID: originID, destinationID: destinationID, packetID: packetID,
                                 created: createdAtMilliseconds, expires: expiresAtMilliseconds,
                                 hopLimit: hopLimit)
        let key = HKDF<SHA256>.deriveKey(inputKeyMaterial: sharedSecret,
                                         salt: Data(SHA256.hash(data: aad)),
                                         info: keyInfo, outputByteCount: 32)
        let split = payload.ciphertextAndTag.count - 16
        let nonce = try AES.GCM.Nonce(data: payload.nonce)
        let box = try AES.GCM.SealedBox(nonce: nonce,
                                        ciphertext: payload.ciphertextAndTag.prefix(split),
                                        tag: payload.ciphertextAndTag.suffix(16))
        return try AES.GCM.open(box, using: key, authenticating: aad)
    }

    private static func validate(originID: String, destinationID: String, packetID: String,
                                 created: Int64, expires: Int64, hopLimit: UInt8,
                                 plaintextCount: Int?) throws {
        guard NeaRerIdentity.normalize(originID) != nil,
              NeaRerIdentity.normalize(destinationID) != nil,
              originID != destinationID else { throw NeaRerWireError.invalidPeerID }
        guard !packetID.isEmpty, packetID.utf8.count <= 80,
              packetID.range(of: "^[A-Za-z0-9._:-]{1,80}$", options: .regularExpression) != nil else {
            throw NeaRerWireError.invalidField("packet ID")
        }
        guard created >= 0, expires > created,
              expires - created <= maximumLifetimeMilliseconds,
              (1...4).contains(hopLimit) else { throw NeaRerWireError.invalidField("recipient header") }
        if let count = plaintextCount, !(1...16_000).contains(count) {
            throw NeaRerWireError.invalidField("recipient plaintext size")
        }
    }

    private static func associatedData(originID: String, destinationID: String, packetID: String,
                                       created: Int64, expires: Int64, hopLimit: UInt8) -> Data {
        var output = Data("NeaRer-recipient-message-v1".utf8)
        appendLengthPrefixed(Data(originID.utf8), to: &output)
        appendLengthPrefixed(Data(destinationID.utf8), to: &output)
        appendLengthPrefixed(Data(packetID.utf8), to: &output)
        appendUInt64(UInt64(bitPattern: created), to: &output)
        appendUInt64(UInt64(bitPattern: expires), to: &output)
        output.append(1) // Android RecipientMessageCrypto.VERSION
        output.append(hopLimit)
        return output
    }

    private static func appendLengthPrefixed(_ bytes: Data, to output: inout Data) {
        let length = UInt16(bytes.count)
        output.append(UInt8((length >> 8) & 0xff))
        output.append(UInt8(length & 0xff))
        output.append(bytes)
    }

    private static func appendUInt64(_ value: UInt64, to output: inout Data) {
        for shift in stride(from: 56, through: 0, by: -8) {
            output.append(UInt8((value >> UInt64(shift)) & 0xff))
        }
    }
}
#else
import Foundation
public struct NeaRerRecipientCiphertextV1: Equatable {
    public let ephemeralPublicKeyDER: Data
    public let nonce: Data
    public let ciphertextAndTag: Data
}
#endif
