#if canImport(CryptoKit)
import XCTest
import CryptoKit
@testable import NeaRerWire

final class PeerVerificationCodeV1Tests: XCTestCase {
    func testMatchesAndroidShortAuthenticationStringGoldenVector() throws {
        let aliceEphemeral = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200045ecbe4d1a6330a44c8f7ef951d4bf165e6c6b721efada985fb41661bc6e7fd6c8734640c4998ff7e374b06ce1a64a2ecd82ab036384fb83d9a79b127a27d5032")
        let bobEphemeral = hex("3059301306072a8648ce3d020106082a8648ce3d03010703420004e2534a3532d08fbba02dde659ee62bd0031fe2db785596ef509302446b030852e0f1575a4c633cc719dfee5fda862d764efc96c3f30ee0055c42c23f184ed8c6")
        let aliceRecipient = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1")
        let bobRecipient = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9e162bce33576b315ececbb6406837bf51f5")
        let first = try NeaRerHandshakeParticipantV3(
            peerID: "AAAAAB", nonce: Data(repeating: 0x11, count: 32),
            ephemeralPublicKeyDER: aliceEphemeral,
            recipientEncryptionPublicKeyDER: aliceRecipient
        )
        let second = try NeaRerHandshakeParticipantV3(
            peerID: "BBBBBB", nonce: Data(repeating: 0x22, count: 32),
            ephemeralPublicKeyDER: bobEphemeral,
            recipientEncryptionPublicKeyDER: bobRecipient
        )
        let transcript = try PeerHandshakeTranscriptV3.encode(first, second)
        XCTAssertEqual(try NeaRerPeerVerificationCodeV1.fromTranscript(transcript), "129961")
    }

    private func hex(_ value: String) -> Data {
        Data(stride(from: 0, to: value.count, by: 2).map { index in
            let start = value.index(value.startIndex, offsetBy: index)
            let end = value.index(start, offsetBy: 2)
            return UInt8(value[start..<end], radix: 16)!
        })
    }
}
#endif
