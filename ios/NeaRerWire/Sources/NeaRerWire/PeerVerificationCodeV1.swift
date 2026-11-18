import Foundation
#if canImport(CryptoKit)
import CryptoKit
#endif

/// Six-digit short-authentication string shared with Android Nearby pairing.
/// It is derived from the authenticated, canonical v3 handshake transcript.
public enum NeaRerPeerVerificationCodeV1 {
    public static func fromTranscript(_ transcript: Data) throws -> String {
        guard !transcript.isEmpty else { throw NeaRerWireError.invalidField("empty verification transcript") }
        #if canImport(CryptoKit)
        let digest = Array(SHA256.hash(data: transcript))
        let first = UInt32(digest[0])
        let second = UInt32(digest[1])
        let third = UInt32(digest[2])
        let fourth = UInt32(digest[3])
        let highWord = (first << 24) | (second << 16)
        let lowWord = (third << 8) | fourth
        let value = highWord | lowWord
        let code = String(value % 1_000_000)
        return String(repeating: "0", count: 6 - code.count) + code
        #else
        throw NeaRerWireError.invalidPublicKey
        #endif
    }
}
