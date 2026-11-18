#if canImport(CryptoKit)
import XCTest
import CryptoKit
@testable import NeaRerWire

final class TransferFileStreamV1Tests: XCTestCase {
    func testStreamingWriterAndReceiverFinalizeVerifiedFile() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let sourceDir = root.appendingPathComponent("source")
        let destinationDir = root.appendingPathComponent("destination")
        try FileManager.default.createDirectory(at: sourceDir, withIntermediateDirectories: true)
        let source = sourceDir.appendingPathComponent("sample.bin")
        let bytes = Data((0..<(4 * 1024 * 1024 + 123)).map { UInt8($0 % 251) })
        try bytes.write(to: source)
        let digest = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
        let item = NeaRerTransferItemV1(
            id: "item-1", relativePath: "Received/sample.bin",
            displayName: "sample.bin", sizeBytes: UInt64(bytes.count),
            mimeType: "application/octet-stream"
        )
        let metadata = try NeaRerTransferStreamMetadataV1(
            payloadID: 7, transferID: "transfer-1", item: item,
            sourceModifiedAtMilliseconds: nil, wholeFileSHA256: digest
        )
        let writer = try NeaRerTransferFileStreamWriterV1(
            fileURL: source, metadata: metadata, chunkSize: 128 * 1024
        )
        let receiver = try NeaRerTransferFileStreamReceiverV1(
            metadata: metadata, destinationDirectory: destinationDir
        )
        let initialAck = receiver.initialAck()
        XCTAssertEqual(initialAck.committedOffsetBytes, 0)
        var chunks = 0
        var checkpointAcks: [NeaRerTransferFileAckV1] = []
        while let chunk = try writer.nextChunk() {
            XCTAssertLessThanOrEqual(chunk.data.count, 128 * 1024)
            if let ack = try receiver.append(chunk) { checkpointAcks.append(ack) }
            chunks += 1
        }
        XCTAssertGreaterThan(chunks, 1)
        XCTAssertEqual(checkpointAcks.map(\.committedOffsetBytes), [4 * 1024 * 1024])
        let end = try writer.finish()
        let finalized = try receiver.finish(end)
        XCTAssertEqual(try Data(contentsOf: finalized.url), bytes)
        XCTAssertEqual(finalized.finalAck.committedOffsetBytes, UInt64(bytes.count))
        XCTAssertEqual(finalized.finalAck.prefixSHA256, digest)
    }

    func testReceiverRejectsOutOfOrderAndWrongTransferChunks() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let item = NeaRerTransferItemV1(id: "i", relativePath: "a.bin",
                                        displayName: "a.bin", sizeBytes: 5)
        let metadata = try NeaRerTransferStreamMetadataV1(
            payloadID: 1, transferID: "x", item: item,
            sourceModifiedAtMilliseconds: nil, wholeFileSHA256: String(repeating: "0", count: 64)
        )
        let receiver = try NeaRerTransferFileStreamReceiverV1(metadata: metadata, destinationDirectory: root)
        XCTAssertThrowsError(try receiver.append(
            NeaRerTransferFileChunkV1(payloadID: 1, transferID: "x", itemID: "i",
                                      offsetBytes: 1, data: Data([1]))
        ))
        XCTAssertThrowsError(try receiver.append(
            NeaRerTransferFileChunkV1(payloadID: 2, transferID: "x", itemID: "i",
                                      offsetBytes: 0, data: Data([1]))
        ))
        receiver.cancel()
    }

    func testWriterDetectsSourceFileChangedAfterMetadata() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let source = root.appendingPathComponent("file.bin")
        try Data("hello".utf8).write(to: source)
        let item = NeaRerTransferItemV1(id: "i", relativePath: "file.bin",
                                        displayName: "file.bin", sizeBytes: 5)
        let metadata = try NeaRerTransferStreamMetadataV1(
            payloadID: 1, transferID: "x", item: item,
            sourceModifiedAtMilliseconds: nil, wholeFileSHA256: String(repeating: "0", count: 64)
        )
        let writer = try NeaRerTransferFileStreamWriterV1(fileURL: source, metadata: metadata)
        _ = try writer.nextChunk()
        XCTAssertThrowsError(try writer.finish())
    }
}
#endif
