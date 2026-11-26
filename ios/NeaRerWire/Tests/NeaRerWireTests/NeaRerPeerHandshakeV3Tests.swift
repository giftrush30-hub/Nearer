#if canImport(CryptoKit)
import XCTest
import CryptoKit
@testable import NeaRerWire

final class NeaRerPeerHandshakeV3Tests: XCTestCase {
    func testTwoPeersCreateAndVerifyProofsAndDeriveMatchingSessionKeys() throws {
        let aliceSigning = try P256.Signing.PrivateKey(rawRepresentation: hex("0000000000000000000000000000000000000000000000000000000000000001"))
        let bobSigning = try P256.Signing.PrivateKey(rawRepresentation: hex("0000000000000000000000000000000000000000000000000000000000000002"))
        let aliceRecipient = try P256.KeyAgreement.PrivateKey(rawRepresentation: hex("0000000000000000000000000000000000000000000000000000000000000002"))
        let bobRecipient = try P256.KeyAgreement.PrivateKey(rawRepresentation: hex("0000000000000000000000000000000000000000000000000000000000000001"))
        let aliceEphemeral = try P256.KeyAgreement.PrivateKey(rawRepresentation: hex("0000000000000000000000000000000000000000000000000000000000000003"))
        let bobEphemeral = try P256.KeyAgreement.PrivateKey(rawRepresentation: hex("0000000000000000000000000000000000000000000000000000000000000004"))

        let alice = try NeaRerPeerHandshakeV3(
            localPeerID: "AAAAAB", signingPrivateKey: aliceSigning,
            recipientEncryptionPrivateKey: aliceRecipient, ephemeralPrivateKey: aliceEphemeral,
            nonce: Data(repeating: 0x11, count: 32)
        )
        let bob = try NeaRerPeerHandshakeV3(
            localPeerID: "BBBBBB", signingPrivateKey: bobSigning,
            recipientEncryptionPrivateKey: bobRecipient, ephemeralPrivateKey: bobEphemeral,
            nonce: Data(repeating: 0x22, count: 32)
        )
        let aliceHello = try alice.makeHello()
        let bobHello = try bob.makeHello()
        let aliceProof = try alice.makeProof(for: bobHello)
        let bobProof = try bob.makeProof(for: aliceHello)

        XCTAssertTrue(alice.verify(bobProof, from: bobHello))
        XCTAssertTrue(bob.verify(aliceProof, from: aliceHello))
        XCTAssertFalse(alice.verify(bobProof, from: aliceHello))
        XCTAssertEqual(try alice.verificationCode(with: bobHello), "129961")
        XCTAssertEqual(try bob.verificationCode(with: aliceHello), "129961")

        let aliceKeys = try alice.deriveSessionKeys(with: bobHello)
        let bobKeys = try bob.deriveSessionKeys(with: aliceHello)
        XCTAssertEqual(aliceKeys.sendKey, bobKeys.receiveKey)
        XCTAssertEqual(aliceKeys.receiveKey, bobKeys.sendKey)
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
