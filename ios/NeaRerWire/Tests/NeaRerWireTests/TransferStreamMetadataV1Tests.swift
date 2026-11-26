import XCTest
@testable import NeaRerWire

final class TransferStreamMetadataV1Tests: XCTestCase {
    func testAndroidNRSMGoldenVectorAndRoundTrip() throws {
        let metadata = try NeaRerTransferStreamMetadataV1(
            payloadID: 7,
            transferID: "x",
            item: NeaRerTransferItemV1(id: "i", relativePath: "a", displayName: "a", sizeBytes: 10),
            sourceModifiedAtMilliseconds: nil,
            wholeFileSHA256: String(repeating: "0", count: 64)
        )
        let bytes = try NeaRerTransferStreamMetadataCodecV1.encode(metadata)
        let expected = "4e52534d010000000000000007000178000169000161000161000000000000000a00000040" + String(repeating: "30", count: 64)
        XCTAssertEqual(bytes.map { String(format: "%02x", $0) }.joined(), expected)
        XCTAssertEqual(try NeaRerTransferStreamMetadataCodecV1.decode(bytes), metadata)
    }

    func testRejectsDirectoryAndInvalidDigest() throws {
        XCTAssertThrowsError(try NeaRerTransferStreamMetadataV1(
            payloadID: 1, transferID: "x",
            item: NeaRerTransferItemV1(id: "d", relativePath: "d", displayName: "d",
                                      sizeBytes: 0, kind: .directory),
            sourceModifiedAtMilliseconds: nil, wholeFileSHA256: String(repeating: "0", count: 64)
        ))
        XCTAssertThrowsError(try NeaRerTransferStreamMetadataV1(
            payloadID: 1, transferID: "x",
            item: NeaRerTransferItemV1(id: "i", relativePath: "a", displayName: "a", sizeBytes: 1),
            sourceModifiedAtMilliseconds: nil, wholeFileSHA256: "bad"
        ))
    }
}
