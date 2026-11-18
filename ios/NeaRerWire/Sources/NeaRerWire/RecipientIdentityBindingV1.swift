import Foundation
#if canImport(CryptoKit)
import CryptoKit
#endif

public struct NeaRerRecipientIdentityBindingV1: Equatable {
    public let deviceID: String
    public let signingKeyFingerprint: Data
    public let recipientEncryptionPublicKeyDER: Data
    public let sequence: UInt64
    public let issuedAtMilliseconds: Int64
    public let expiresAtMilliseconds: Int64
    public let signatureDER: Data

    public init(deviceID: String, signingKeyFingerprint: Data,
                recipientEncryptionPublicKeyDER: Data, sequence: UInt64,
                issuedAtMilliseconds: Int64, expiresAtMilliseconds: Int64,
                signatureDER: Data) {
        self.deviceID = deviceID
        self.signingKeyFingerprint = signingKeyFingerprint
        self.recipientEncryptionPublicKeyDER = recipientEncryptionPublicKeyDER
        self.sequence = sequence
        self.issuedAtMilliseconds = issuedAtMilliseconds
        self.expiresAtMilliseconds = expiresAtMilliseconds
        self.signatureDER = signatureDER
    }

    /// Exact Android RecipientIdentityBinding.signedBytes() layout.
    public func signedBytes() throws -> Data {
        guard NeaRerIdentity.normalize(deviceID) == deviceID,
              signingKeyFingerprint.count == 32, sequence <= UInt64(Int64.max),
              (64...512).contains(recipientEncryptionPublicKeyDER.count),
              issuedAtMilliseconds >= 0, expiresAtMilliseconds > issuedAtMilliseconds,
              expiresAtMilliseconds - issuedAtMilliseconds <= Self.maximumLifetimeMilliseconds else {
            throw NeaRerWireError.invalidField("recipient identity binding")
        }
        var output = Data("NeaRer-recipient-identity-binding-v1".utf8)
        let id = Data(deviceID.utf8)
        appendUInt16(UInt16(id.count), to: &output)
        output.append(id)
        appendUInt32(UInt32(signingKeyFingerprint.count), to: &output)
        output.append(signingKeyFingerprint)
        appendUInt32(UInt32(recipientEncryptionPublicKeyDER.count), to: &output)
        output.append(recipientEncryptionPublicKeyDER)
        appendUInt64(sequence, to: &output)
        appendUInt64(UInt64(issuedAtMilliseconds), to: &output)
        appendUInt64(UInt64(expiresAtMilliseconds), to: &output)
        return output
    }

    public func verify(pinnedSigningPublicKeyDER: Data, nowMilliseconds: Int64) -> Bool {
        guard nowMilliseconds >= 0,
              signatureDER.count >= 8, signatureDER.count <= 128,
              (64...512).contains(pinnedSigningPublicKeyDER.count),
              issuedAtMilliseconds <= nowMilliseconds ||
                issuedAtMilliseconds - nowMilliseconds <= 300_000,
              expiresAtMilliseconds > nowMilliseconds,
              let expectedFingerprint = Self.sha256(pinnedSigningPublicKeyDER),
              expectedFingerprint == signingKeyFingerprint,
              Self.isP256(pinnedSigningPublicKeyDER),
              Self.isP256(recipientEncryptionPublicKeyDER),
              let bytes = try? signedBytes() else { return false }
        #if canImport(CryptoKit)
        return NeaRerCryptoV3.verifyDetached(
            publicKeyDER: pinnedSigningPublicKeyDER, transcript: bytes, signatureDER: signatureDER
        )
        #else
        return false
        #endif
    }

    public func encode() throws -> Data {
        _ = try signedBytes()
        guard signatureDER.count >= 8, signatureDER.count <= 128 else {
            throw NeaRerWireError.invalidField("binding signature")
        }
        return try JSONSerialization.data(withJSONObject: [
            "kind": "mesh_identity_binding_v1",
            "deviceId": deviceID,
            "signingKeyFingerprint": signingKeyFingerprint.base64EncodedString(),
            "recipientEncryptionPublicKey": recipientEncryptionPublicKeyDER.base64EncodedString(),
            "sequence": NSNumber(value: sequence),
            "issuedAtMs": NSNumber(value: issuedAtMilliseconds),
            "expiresAtMs": NSNumber(value: expiresAtMilliseconds),
            "signature": signatureDER.base64EncodedString()
        ], options: [.sortedKeys])
    }

    public static func decode(_ data: Data) throws -> NeaRerRecipientIdentityBindingV1 {
        guard data.count <= 4_096,
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              object["kind"] as? String == "mesh_identity_binding_v1",
              let deviceID = object["deviceId"] as? String,
              let fingerprintText = object["signingKeyFingerprint"] as? String,
              let fingerprint = Data(base64Encoded: fingerprintText),
              let recipientText = object["recipientEncryptionPublicKey"] as? String,
              let recipient = Data(base64Encoded: recipientText),
              let signatureText = object["signature"] as? String,
              let signature = Data(base64Encoded: signatureText),
              let sequenceNumber = object["sequence"] as? NSNumber,
              let issuedNumber = object["issuedAtMs"] as? NSNumber,
              let expiresNumber = object["expiresAtMs"] as? NSNumber else {
            throw NeaRerWireError.malformedJSON
        }
        let sequence = sequenceNumber.uint64Value
        guard sequenceNumber.int64Value >= 0,
              issuedNumber.int64Value >= 0, expiresNumber.int64Value >= 0 else {
            throw NeaRerWireError.invalidField("binding counters")
        }
        let value = NeaRerRecipientIdentityBindingV1(
            deviceID: deviceID, signingKeyFingerprint: fingerprint,
            recipientEncryptionPublicKeyDER: recipient, sequence: sequence,
            issuedAtMilliseconds: issuedNumber.int64Value,
            expiresAtMilliseconds: expiresNumber.int64Value, signatureDER: signature
        )
        _ = try value.signedBytes()
        guard signature.count >= 8, signature.count <= 128 else {
            throw NeaRerWireError.invalidField("binding signature")
        }
        return value
    }

    public static func create(deviceID: String, signingPublicKeyDER: Data,
                              recipientEncryptionPublicKeyDER: Data, sequence: UInt64,
                              issuedAtMilliseconds: Int64, expiresAtMilliseconds: Int64,
                              sign: (Data) throws -> Data) throws -> NeaRerRecipientIdentityBindingV1 {
        guard isP256(signingPublicKeyDER), isP256(recipientEncryptionPublicKeyDER),
              let fingerprint = sha256(signingPublicKeyDER) else {
            throw NeaRerWireError.invalidPublicKey
        }
        let unsigned = NeaRerRecipientIdentityBindingV1(
            deviceID: deviceID, signingKeyFingerprint: fingerprint,
            recipientEncryptionPublicKeyDER: recipientEncryptionPublicKeyDER,
            sequence: sequence, issuedAtMilliseconds: issuedAtMilliseconds,
            expiresAtMilliseconds: expiresAtMilliseconds, signatureDER: Data()
        )
        let bytes = try unsigned.signedBytes()
        let signature = try sign(bytes)
        return NeaRerRecipientIdentityBindingV1(
            deviceID: deviceID, signingKeyFingerprint: fingerprint,
            recipientEncryptionPublicKeyDER: recipientEncryptionPublicKeyDER,
            sequence: sequence, issuedAtMilliseconds: issuedAtMilliseconds,
            expiresAtMilliseconds: expiresAtMilliseconds, signatureDER: signature
        )
    }

    private static let maximumLifetimeMilliseconds: Int64 = 24 * 60 * 60 * 1_000

    private static func sha256(_ data: Data) -> Data? {
        #if canImport(CryptoKit)
        return Data(SHA256.hash(data: data))
        #else
        return nil
        #endif
    }

    private static func isP256(_ der: Data) -> Bool {
        #if canImport(CryptoKit)
        return (try? P256.Signing.PublicKey(derRepresentation: der)) != nil
        #else
        return false
        #endif
    }

    private func appendUInt16(_ value: UInt16, to data: inout Data) {
        data.append(UInt8((value >> 8) & 0xff))
        data.append(UInt8(value & 0xff))
    }

    private func appendUInt32(_ value: UInt32, to data: inout Data) {
        for shift in stride(from: 24, through: 0, by: -8) {
            data.append(UInt8((value >> UInt32(shift)) & 0xff))
        }
    }

    private func appendUInt64(_ value: UInt64, to data: inout Data) {
        for shift in stride(from: 56, through: 0, by: -8) {
            data.append(UInt8((value >> UInt64(shift)) & 0xff))
        }
    }
}
