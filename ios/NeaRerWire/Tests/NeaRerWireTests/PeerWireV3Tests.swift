import XCTest
@testable import NeaRerWire

final class PeerWireV3Tests: XCTestCase {
    func testIdentityAcceptsCurrentAndLegacyIDs() {
        XCTAssertEqual(NeaRerIdentity.normalize(" abcd23efghjk "), "ABCD23EFGHJK")
        XCTAssertEqual(NeaRerIdentity.normalize("a1b2c3"), "A1B2C3")
        XCTAssertNil(NeaRerIdentity.normalize("12345Z"))
        XCTAssertNil(NeaRerIdentity.normalize("ABCD23EFGHI0"))
    }

    func testHelloRoundTripsAndroidV3FieldNamesAndDERKeys() throws {
        let hello = try PeerHelloV3(
            peerID: "ABCD23EFGHJK",
            signingPublicKeyDER: Data(repeating: 0x30, count: 91),
            nonce: Data(repeating: 0x5a, count: 32),
            ephemeralPublicKeyDER: Data(repeating: 0x31, count: 91),
            recipientEncryptionPublicKeyDER: Data(repeating: 0x32, count: 91)
        )
        let encoded = try hello.encode()
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        XCTAssertEqual(object["kind"] as? String, "peer_hello_v3")
        XCTAssertNotNil(object["peerId"])
        XCTAssertNotNil(object["recipientEncryptionPublicKey"])
        XCTAssertEqual(try PeerHelloV3.decode(encoded), hello)
    }

    func testHelloRejectsWrongNonceAndShortKey() throws {
        let signingDER = Data(repeating: 0x30, count: 91)
        let ephemeralDER = Data(repeating: 0x31, count: 91)
        XCTAssertThrowsError(try PeerHelloV3(
            peerID: "ABCD23EFGHJK", signingPublicKeyDER: signingDER,
            nonce: Data(repeating: 1, count: 31), ephemeralPublicKeyDER: ephemeralDER,
            recipientEncryptionPublicKeyDER: ephemeralDER
        ))
        XCTAssertThrowsError(try PeerHelloV3(
            peerID: "ABCD23EFGHJK", signingPublicKeyDER: Data([1, 2, 3]),
            nonce: Data(repeating: 1, count: 32), ephemeralPublicKeyDER: ephemeralDER,
            recipientEncryptionPublicKeyDER: ephemeralDER
        ))
    }

    func testProofRoundTrips() throws {
        let proof = try PeerProofV3(peerID: "A1B2C3", signatureDER: Data(repeating: 7, count: 70))
        XCTAssertEqual(try PeerProofV3.decode(proof.encode()), proof)
    }

    func testSecurePacketRoundTripsAndroidV1Envelope() throws {
        let packet = try SecurePacketV1(
            messageID: "message-123",
            nonce: Data(repeating: 0x11, count: 12),
            ciphertext: Data(repeating: 0x22, count: 32)
        )
        let encoded = try packet.encode()
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        XCTAssertEqual(object["kind"] as? String, "secure_packet_v1")
        XCTAssertEqual(object["version"] as? Int, 1)
        XCTAssertEqual(try SecurePacketV1.decode(encoded), packet)
    }

    func testSecurePacketRejectsBadNonceAndTruncatedCiphertext() {
        XCTAssertThrowsError(try SecurePacketV1(messageID: "x", nonce: Data(repeating: 0, count: 11), ciphertext: Data(repeating: 0, count: 16)))
        XCTAssertThrowsError(try SecurePacketV1(messageID: "x", nonce: Data(repeating: 0, count: 12), ciphertext: Data(repeating: 0, count: 15)))
    }
}
