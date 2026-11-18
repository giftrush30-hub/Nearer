import XCTest
@testable import NeaRerWire

final class TransferBundleManifestV1Tests: XCTestCase {
    func testManifestRoundTripAndStableNRB1Header() throws {
        let manifest = try NeaRerTransferManifestV1(transferID: "transfer-1", items: [
            NeaRerTransferItemV1(id: "photo-1", relativePath: "Photos/IMG_001.jpg",
                                 displayName: "IMG_001.jpg", sizeBytes: 123456,
                                 mimeType: "image/jpeg"),
            NeaRerTransferItemV1(id: "folder-1", relativePath: "Documents",
                                 displayName: "Documents", sizeBytes: 0, kind: .directory)
        ])
        let bytes = try manifest.encode()
        XCTAssertEqual(Array(bytes.prefix(8)), [0x4e, 0x52, 0x42, 0x31, 0, 0, 0, 1])
        XCTAssertEqual(try NeaRerTransferManifestV1.decode(bytes), manifest)
    }

    func testAndroidKotlinGoldenVector() throws {
        let manifest = try NeaRerTransferManifestV1(transferID: "x", items: [
            NeaRerTransferItemV1(id: "i", relativePath: "a", displayName: "a", sizeBytes: 1)
        ])
        let actual = try manifest.encode().map { String(format: "%02x", $0) }.joined()
        XCTAssertEqual(actual, "4e5242310000000100000001780000000100000001690000000161000000016100000000000000010000")
    }

    func testManifestRejectsTraversalAndDuplicatePaths() throws {
        XCTAssertThrowsError(try NeaRerTransferManifestV1(transferID: "x", items: [
            NeaRerTransferItemV1(id: "1", relativePath: "../escape", displayName: "escape", sizeBytes: 1)
        ]))
        XCTAssertThrowsError(try NeaRerTransferManifestV1(transferID: "x", items: [
            NeaRerTransferItemV1(id: "1", relativePath: "same.txt", displayName: "same.txt", sizeBytes: 1),
            NeaRerTransferItemV1(id: "2", relativePath: "same.txt", displayName: "same.txt", sizeBytes: 2)
        ]))
    }

    func testManifestRejectsFileAsParentDirectory() throws {
        XCTAssertThrowsError(try NeaRerTransferManifestV1(transferID: "x", items: [
            NeaRerTransferItemV1(id: "1", relativePath: "Photos", displayName: "Photos", sizeBytes: 12),
            NeaRerTransferItemV1(id: "2", relativePath: "Photos/a.jpg", displayName: "a.jpg", sizeBytes: 1)
        ]))
    }

    func testManifestRejectsTruncatedAndTrailingBytes() throws {
        let manifest = try NeaRerTransferManifestV1(transferID: "x", items: [
            NeaRerTransferItemV1(id: "1", relativePath: "a.txt", displayName: "a.txt", sizeBytes: 1)
        ])
        let bytes = try manifest.encode()
        XCTAssertThrowsError(try NeaRerTransferManifestV1.decode(bytes.dropLast()))
        var withTrailing = bytes
        withTrailing.append(0)
        XCTAssertThrowsError(try NeaRerTransferManifestV1.decode(withTrailing))
    }
}
