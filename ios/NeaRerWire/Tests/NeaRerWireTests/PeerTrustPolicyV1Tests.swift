#if canImport(CryptoKit)
import XCTest
@testable import NeaRerWire

private func fixtureData(_ value: String) -> Data {
    Data(stride(from: 0, to: value.count, by: 2).map { index in
        let start = value.index(value.startIndex, offsetBy: index)
        let end = value.index(start, offsetBy: 2)
        return UInt8(value[start..<end], radix: 16)!
    })
}

final class PeerTrustPolicyV1Tests: XCTestCase {
    private let signing = fixtureData("3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
    private let recipient = fixtureData("3059301306072a8648ce3d020106082a8648ce3d030107034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1")

    func testFirstContactRequiresExplicitApprovalAndPinsBothKeys() throws {
        XCTAssertEqual(try NeaRerPeerTrustPolicyV1.evaluate(
            peerID: "AAAAAB", signingPublicKeyDER: signing,
            recipientEncryptionPublicKeyDER: recipient, saved: nil
        ), .firstSeen)
        XCTAssertThrowsError(try NeaRerPeerTrustPolicyV1.approveFirstContact(
            peerID: "AAAAAB", signingPublicKeyDER: signing,
            recipientEncryptionPublicKeyDER: recipient, saved: nil, userApproved: false
        ))
        let saved = try NeaRerPeerTrustPolicyV1.approveFirstContact(
            peerID: "AAAAAB", signingPublicKeyDER: signing,
            recipientEncryptionPublicKeyDER: recipient, saved: nil, userApproved: true
        )
        XCTAssertEqual(try NeaRerPeerTrustPolicyV1.evaluate(
            peerID: "AAAAAB", signingPublicKeyDER: signing,
            recipientEncryptionPublicKeyDER: recipient, saved: saved
        ), .match)
    }

    func testSigningOrRecipientKeyChangeIsRejected() throws {
        let saved = NeaRerPeerTrustRecordV1(
            peerID: "AAAAAB", signingPublicKeyDER: signing,
            recipientEncryptionPublicKeyDER: recipient
        )
        XCTAssertEqual(try NeaRerPeerTrustPolicyV1.evaluate(
            peerID: "AAAAAB", signingPublicKeyDER: recipient,
            recipientEncryptionPublicKeyDER: recipient, saved: saved
        ), .keyChanged)
        XCTAssertThrowsError(try NeaRerPeerTrustPolicyV1.approveFirstContact(
            peerID: "AAAAAB", signingPublicKeyDER: recipient,
            recipientEncryptionPublicKeyDER: recipient, saved: saved, userApproved: true
        ))
    }

    func testBindingSequenceMustIncreaseMonotonically() throws {
        let saved = NeaRerPeerTrustRecordV1(
            peerID: "AAAAAB", signingPublicKeyDER: signing,
            recipientEncryptionPublicKeyDER: recipient, lastBindingSequence: 4
        )
        XCTAssertEqual(try NeaRerPeerTrustPolicyV1.acceptBindingSequence(5, record: saved).lastBindingSequence, 5)
        XCTAssertThrowsError(try NeaRerPeerTrustPolicyV1.acceptBindingSequence(4, record: saved))
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
