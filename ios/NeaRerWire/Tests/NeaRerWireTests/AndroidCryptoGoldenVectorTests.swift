#if canImport(CryptoKit)
import XCTest
import CryptoKit
@testable import NeaRerWire

private func fixtureData(_ value: String) -> Data {
    Data(stride(from: 0, to: value.count, by: 2).map { index in
        let start = value.index(value.startIndex, offsetBy: index)
        let end = value.index(start, offsetBy: 2)
        return UInt8(value[start..<end], radix: 16)!
    })
}

final class AndroidCryptoGoldenVectorTests: XCTestCase {
    private let alicePrivate = fixtureData("0000000000000000000000000000000000000000000000000000000000000001")
    private let bobPrivate = fixtureData("0000000000000000000000000000000000000000000000000000000000000002")
    private let alicePublicDER = fixtureData("3059301306072a8648ce3d020106082a8648ce3d030107034200046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
    private let bobPublicDER = fixtureData("3059301306072a8648ce3d020106082a8648ce3d030107034200047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1")
    private let transcript = Data("NeaRer cross-platform crypto fixture v1".utf8)

    func testAndroidP256ECDHHKDFAndAESGCMFixture() throws {
        let alice = try P256.KeyAgreement.PrivateKey(rawRepresentation: alicePrivate)
        let bob = try P256.KeyAgreement.PrivateKey(rawRepresentation: bobPrivate)
        XCTAssertEqual(alice.publicKey.derRepresentation, alicePublicDER)
        XCTAssertEqual(bob.publicKey.derRepresentation, bobPublicDER)

        let aliceKeys = try NeaRerCryptoV3.deriveSession(
            localID: "AAAAAB", remoteID: "BBBBBB", localPrivateKey: alice,
            remotePublicKeyDER: bobPublicDER, transcript: transcript
        )
        let bobKeys = try NeaRerCryptoV3.deriveSession(
            localID: "BBBBBB", remoteID: "AAAAAB", localPrivateKey: bob,
            remotePublicKeyDER: alicePublicDER, transcript: transcript
        )
        XCTAssertEqual(aliceKeys.sendKey.hex, "1decdff0942fedbe28650b20d380c348f2e677263e654cf23a8e812a2db07337")
        XCTAssertEqual(aliceKeys.sendKey, bobKeys.receiveKey)
        XCTAssertEqual(aliceKeys.receiveKey, bobKeys.sendKey)

        let packet = try SecurePacketV1(
            messageID: "crypto-vector-1",
            nonce: hex("000102030405060708090a0b"),
            ciphertext: hex("4f063a9107c822dcadedc9ea333bc6d0fd1e44f75698ac4d0a1df58ed0154beb7462d10871331867b9f3ee606b3f913edc6fd4b3")
        )
        XCTAssertEqual(
            try NeaRerCryptoV3.open(session: bobKeys, recipientID: "BBBBBB",
                                    senderID: "AAAAAB", packet: packet),
            Data("NeaRer Android iPhone secure message".utf8)
        )
        XCTAssertThrowsError(try NeaRerCryptoV3.open(
            session: bobKeys, recipientID: "AAAAAB", senderID: "BBBBBB", packet: packet
        ))
    }

    func testAndroidECDSASignatureFixtureVerifiesInCryptoKit() {
        let message = Data("NeaRer Android iPhone signature fixture".utf8)
        let signature = hex("304402204fb29e9815467b7903789c9acc1ce1fae06bf378c8620cf04225f70ef909137f022063b7ea072638f204eb2534d2abde8263739b3b84babac728a4807c7a78fa0982")
        XCTAssertTrue(NeaRerCryptoV3.verifyDetached(
            publicKeyDER: alicePublicDER, transcript: message, signatureDER: signature
        ))
        XCTAssertFalse(NeaRerCryptoV3.verifyDetached(
            publicKeyDER: alicePublicDER, transcript: Data("tampered".utf8), signatureDER: signature
        ))
    }

    func testAndroidPeerProofDomainAndFingerprintFixture() {
        let challenge = Data("NeaRer Android iPhone signature fixture".utf8)
        let signature = hex("304502207e31700985a269fe326027c9809ae2751f669b5897117096350d34952bf570ee0221009a0243bed67481475f3b2b45306c3d4a341523c864e5754e3f4b90bd4ac4a031")
        XCTAssertTrue(NeaRerCryptoV3.verifyChallenge(
            peerID: "AAAAAB", signingPublicKeyDER: alicePublicDER,
            transcript: challenge, signatureDER: signature
        ))
        XCTAssertFalse(NeaRerCryptoV3.verifyChallenge(
            peerID: "BBBBBB", signingPublicKeyDER: alicePublicDER,
            transcript: challenge, signatureDER: signature
        ))
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
