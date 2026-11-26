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
        let digest = SHA256.hash(data: transcript)
        let value = (UInt32(digest[digest.startIndex]) << 24)
            | (UInt32(digest[digest.startIndex + 1]) << 16)
            | (UInt32(digest[digest.startIndex + 2]) << 8)
            | UInt32(digest[digest.startIndex + 3])
        let code = String(value % 1_000_000)
        return String(repeating: "0", count: 6 - code.count) + code
        #else
        throw NeaRerWireError.invalidPublicKey
        #endif
    }
}
