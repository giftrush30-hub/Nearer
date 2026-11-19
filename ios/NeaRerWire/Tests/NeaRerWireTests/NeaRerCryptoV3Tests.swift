#if canImport(CryptoKit)
import XCTest
import CryptoKit
@testable import NeaRerWire

final class NeaRerCryptoV3Tests: XCTestCase {
    func testP256ECDHProducesMatchingDirectionalKeysAndAESGCMRoundTrip() throws {
        let alice = P256.KeyAgreement.PrivateKey()
        let bob = P256.KeyAgreement.PrivateKey()
        let aliceParticipant = try NeaRerHandshakeParticipantV3(
            peerID: "AAAAAA", nonce: Data(repeating: 0x11, count: 32),
            ephemeralPublicKeyDER: alice.publicKey.derRepresentation,
            recipientEncryptionPublicKeyDER: alice.publicKey.derRepresentation
        )
        let bobParticipant = try NeaRerHandshakeParticipantV3(
            peerID: "BBBBBB", nonce: Data(repeating: 0x22, count: 32),
            ephemeralPublicKeyDER: bob.publicKey.derRepresentation,
            recipientEncryptionPublicKeyDER: bob.publicKey.derRepresentation
        )
        let transcript = try PeerHandshakeTranscriptV3.encode(aliceParticipant, bobParticipant)
        let aliceKeys = try NeaRerCryptoV3.deriveSession(
            localID: "AAAAAA", remoteID: "BBBBBB", localPrivateKey: alice,
            remotePublicKeyDER: bob.publicKey.derRepresentation, transcript: transcript
        )
        let bobKeys = try NeaRerCryptoV3.deriveSession(
            localID: "BBBBBB", remoteID: "AAAAAA", localPrivateKey: bob,
            remotePublicKeyDER: alice.publicKey.derRepresentation, transcript: transcript
        )
        XCTAssertEqual(aliceKeys.sendKey, bobKeys.receiveKey)
        XCTAssertEqual(aliceKeys.receiveKey, bobKeys.sendKey)

        let packet = try NeaRerCryptoV3.seal(
            session: aliceKeys, senderID: "AAAAAA", recipientID: "BBBBBB",
            messageID: "cross-platform-message", plaintext: Data("NeaRer iPhone".utf8)
        )
        XCTAssertEqual(try NeaRerCryptoV3.open(
            session: bobKeys, recipientID: "BBBBBB", senderID: "AAAAAA", packet: packet
        ), Data("NeaRer iPhone".utf8))
        XCTAssertThrowsError(try NeaRerCryptoV3.open(
            session: bobKeys, recipientID: "AAAAAA", senderID: "BBBBBB", packet: packet
        ))
    }

    func testAndroidCompatiblePeerProofDomainRoundTrips() throws {
        let key = P256.Signing.PrivateKey()
        let transcript = Data("NeaRer handshake transcript".utf8)
        let signature = try NeaRerCryptoV3.signPeerProof(
            peerID: "AAAAAA", transcript: transcript, using: key
        )
        XCTAssertTrue(NeaRerCryptoV3.verifyPeerProof(
            peerID: "AAAAAA", publicKeyDER: key.publicKey.derRepresentation,
            transcript: transcript, signatureDER: signature
        ))
        XCTAssertFalse(NeaRerCryptoV3.verifyPeerProof(
            peerID: "BBBBBB", publicKeyDER: key.publicKey.derRepresentation,
            transcript: transcript, signatureDER: signature
        ))
        XCTAssertFalse(NeaRerCryptoV3.verifyPeerProof(
            peerID: "AAAAAA", publicKeyDER: key.publicKey.derRepresentation,
            transcript: Data("changed".utf8), signatureDER: signature
        ))
    }

    func testP256DetachedSignatureRoundTrips() throws {
        let key = P256.Signing.PrivateKey()
        let transcript = Data("NeaRer cross-platform proof".utf8)
        let signature = try NeaRerCryptoV3.signDetached(transcript, using: key)
        XCTAssertTrue(NeaRerCryptoV3.verifyDetached(
            publicKeyDER: key.publicKey.derRepresentation, transcript: transcript, signatureDER: signature
        ))
        XCTAssertFalse(NeaRerCryptoV3.verifyDetached(
            publicKeyDER: key.publicKey.derRepresentation, transcript: Data("changed".utf8), signatureDER: signature
        ))
    }
}
#endif
