#if canImport(CryptoKit)
import XCTest
import CryptoKit
@testable import NeaRerWire

final class RecipientMessageCryptoV1Tests: XCTestCase {
    func testRecipientAddressedEncryptionRoundTripsAndBindsHeader() throws {
        let recipient = P256.KeyAgreement.PrivateKey()
        let encrypted = try NeaRerRecipientMessageCryptoV1.encrypt(
            originID: "AAAAAA", destinationID: "BBBBBB", packetID: "mesh-123",
            createdAtMilliseconds: 1_000, expiresAtMilliseconds: 86_401_000,
            hopLimit: 4, recipientPublicKeyDER: recipient.publicKey.derRepresentation,
            plaintext: Data("offline mesh message".utf8)
        )
        let clear = try NeaRerRecipientMessageCryptoV1.decrypt(
            recipientPrivateKey: recipient, originID: "AAAAAA", destinationID: "BBBBBB",
            packetID: "mesh-123", createdAtMilliseconds: 1_000, expiresAtMilliseconds: 86_401_000,
            hopLimit: 4, payload: encrypted
        )
        XCTAssertEqual(clear, Data("offline mesh message".utf8))
        XCTAssertThrowsError(try NeaRerRecipientMessageCryptoV1.decrypt(
            recipientPrivateKey: recipient, originID: "AAAAAA", destinationID: "CCCCCC",
            packetID: "mesh-123", createdAtMilliseconds: 1_000, expiresAtMilliseconds: 86_401_000,
            hopLimit: 4, payload: encrypted
        ))
    }

    func testRejectsInvalidLifetimeAndHopLimit() throws {
        let recipient = P256.KeyAgreement.PrivateKey()
        XCTAssertThrowsError(try NeaRerRecipientMessageCryptoV1.encrypt(
            originID: "AAAAAA", destinationID: "BBBBBB", packetID: "x",
            createdAtMilliseconds: 1_000, expiresAtMilliseconds: 1_000,
            hopLimit: 1, recipientPublicKeyDER: recipient.publicKey.derRepresentation,
            plaintext: Data([1])
        ))
        XCTAssertThrowsError(try NeaRerRecipientMessageCryptoV1.encrypt(
            originID: "AAAAAA", destinationID: "BBBBBB", packetID: "x",
            createdAtMilliseconds: 1_000, expiresAtMilliseconds: 2_000,
            hopLimit: 5, recipientPublicKeyDER: recipient.publicKey.derRepresentation,
            plaintext: Data([1])
        ))
    }
}
#endif
