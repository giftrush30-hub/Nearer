import XCTest
@testable import NeaRerWire

final class PeerHandshakeTranscriptV3Tests: XCTestCase {
    func testMatchesAndroidV3TranscriptGoldenVector() throws {
        let first = try NeaRerHandshakeParticipantV3(
            peerID: "AAAAAA",
            nonce: Data(repeating: 0, count: 32),
            ephemeralPublicKeyDER: Data([1, 2]),
            recipientEncryptionPublicKeyDER: Data([3])
        )
        let second = try NeaRerHandshakeParticipantV3(
            peerID: "BBBBBB",
            nonce: Data(repeating: 1, count: 32),
            ephemeralPublicKeyDER: Data([4, 5]),
            recipientEncryptionPublicKeyDER: Data([6])
        )
        let expected = "4e65615265722d4e65617262792d506565722d48616e647368616b652d7633000641414141414120000000000000000000000000000000000000000000000000000000000000000000020102000103000642424242424220010101010101010101010101010101010101010101010101010101010101010100020405000106"
        XCTAssertEqual(try PeerHandshakeTranscriptV3.encode(first, second).map { String(format: "%02x", $0) }.joined(), expected)
    }

    func testTranscriptIsIndependentOfLocalPeerOrder() throws {
        let first = try NeaRerHandshakeParticipantV3(
            peerID: "AAAAAA", nonce: Data(repeating: 0, count: 32),
            ephemeralPublicKeyDER: Data([1]), recipientEncryptionPublicKeyDER: Data([2])
        )
        let second = try NeaRerHandshakeParticipantV3(
            peerID: "BBBBBB", nonce: Data(repeating: 1, count: 32),
            ephemeralPublicKeyDER: Data([3]), recipientEncryptionPublicKeyDER: Data([4])
        )
        XCTAssertEqual(try PeerHandshakeTranscriptV3.encode(first, second),
                       try PeerHandshakeTranscriptV3.encode(second, first))
    }

    func testRejectsSamePeerAndMalformedNonce() throws {
        let valid = try NeaRerHandshakeParticipantV3(
            peerID: "AAAAAA", nonce: Data(repeating: 0, count: 32),
            ephemeralPublicKeyDER: Data([1]), recipientEncryptionPublicKeyDER: Data([2])
        )
        XCTAssertThrowsError(try PeerHandshakeTranscriptV3.encode(valid, valid))
        XCTAssertThrowsError(try NeaRerHandshakeParticipantV3(
            peerID: "BBBBBB", nonce: Data(repeating: 0, count: 31),
            ephemeralPublicKeyDER: Data([1]), recipientEncryptionPublicKeyDER: Data([2])
        ))
    }
}
