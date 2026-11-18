#if canImport(CryptoKit)
import XCTest
import CryptoKit
@testable import NeaRerWire

final class RecipientIdentityBindingV1Tests: XCTestCase {
    private let signingPublic = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
    private let recipientPublic = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1")
    private let signature = hex("304502204866dd74709a64c0a51db024ce144a272a85a0ded6fd0a562bb74f882bbb61c6022100f97e8913480a92c1ff14dbd1b97311c060229bdcd6c08cab2fbeb6e5c98e48fb")

    func testAndroidSignedBytesGoldenVectorAndSignature() throws {
        let binding = NeaRerRecipientIdentityBindingV1(
            deviceID: "AAAAAB",
            signingKeyFingerprint: hex("5cd252fb0ce8932436faf8ccd1040981b89ee4ad6b9fe9e2a2b7e71aacb27cd3"),
            recipientEncryptionPublicKeyDER: recipientPublic,
            sequence: 1, issuedAtMilliseconds: 1_000_000,
            expiresAtMilliseconds: 87_400_000, signatureDER: signature
        )
        let expected = "4e65615265722d726563697069656e742d6964656e746974792d62696e64696e672d76310006414141414142000000205cd252fb0ce8932436faf8ccd1040981b89ee4ad6b9fe9e2a2b7e71aacb27cd30000005b" +
            "3059301306072a8648ce3d020106082a8648ce3d030107034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1" +
            "000000000000000100000000000f42400000000005359e40"
        XCTAssertEqual(binding.signedBytes().hex, expected)
        XCTAssertTrue(binding.verify(pinnedSigningPublicKeyDER: signingPublic, nowMilliseconds: 1_000_001))
        XCTAssertFalse(binding.verify(pinnedSigningPublicKeyDER: signingPublic, nowMilliseconds: 87_400_000))
    }

    func testAndroidIdentityBindingJSONEnvelopeRoundTrips() throws {
        let binding = NeaRerRecipientIdentityBindingV1(
            deviceID: "AAAAAB",
            signingKeyFingerprint: hex("5cd252fb0ce8932436faf8ccd1040981b89ee4ad6b9fe9e2a2b7e71aacb27cd3"),
            recipientEncryptionPublicKeyDER: recipientPublic,
            sequence: 1, issuedAtMilliseconds: 1_000_000,
            expiresAtMilliseconds: 87_400_000, signatureDER: signature
        )
        let encoded = try binding.encode()
        XCTAssertEqual(try NeaRerRecipientIdentityBindingV1.decode(encoded), binding)
    }

    private func hex(_ value: String) -> Data {
        Data(stride(from: 0, to: value.count, by: 2).map { index in
            let start = value.index(value.startIndex, offsetBy: index)
            let end = value.index(start, offsetBy: 2)
            return UInt8(value[start..<end], radix: 16)!
        })
    }
}
private extension Data {
    var hex: String { map { String(format: "%02x", $0) }.joined() }
}
#endif
