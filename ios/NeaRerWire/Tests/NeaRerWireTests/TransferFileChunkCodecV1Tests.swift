import XCTest
@testable import NeaRerWire

final class TransferFileChunkCodecV1Tests: XCTestCase {
    private let digest = String(repeating: "0", count: 64)

    func testChunkMatchesAndroidGoldenVector() throws {
        let value = NeaRerTransferFileChunkV1(
            payloadID: 7, transferID: "x", itemID: "i", offsetBytes: 0,
            data: Data("hello".utf8)
        )
        let encoded = try NeaRerTransferFileChunkCodecV1.encodeChunk(value)
        XCTAssertEqual(encoded.hex, "4e52464301000000000000000700017800016900000000000000000000000568656c6c6f")
        XCTAssertEqual(try NeaRerTransferFileChunkCodecV1.decodeChunk(encoded), value)
    }

    func testAckAndEndMatchAndroidGoldenVectors() throws {
        let ack = NeaRerTransferFileAckV1(
            payloadID: 7, transferID: "x", itemID: "i",
            committedOffsetBytes: 5, prefixSHA256: digest
        )
        let expected = "4e52464101000000000000000700017800016900000000000000050040" + String(repeating: "30", count: 64)
        let ackBytes = try NeaRerTransferFileChunkCodecV1.encodeAck(ack)
        XCTAssertEqual(ackBytes.hex, expected)
        XCTAssertEqual(try NeaRerTransferFileChunkCodecV1.decodeAck(ackBytes), ack)

        let end = NeaRerTransferFileEndV1(
            payloadID: 7, transferID: "x", itemID: "i",
            totalSizeBytes: 5, wholeFileSHA256: digest
        )
        let endBytes = try NeaRerTransferFileChunkCodecV1.encodeEnd(end)
        XCTAssertEqual(endBytes.hex, "4e52464501000000000000000700017800016900000000000000050040" + String(repeating: "30", count: 64))
        XCTAssertEqual(try NeaRerTransferFileChunkCodecV1.decodeEnd(endBytes), end)
    }

    func testRejectsOversizedEmptyAndOffsetOverflowChunks() {
        XCTAssertThrowsError(try NeaRerTransferFileChunkCodecV1.encodeChunk(
            NeaRerTransferFileChunkV1(payloadID: 1, transferID: "x", itemID: "i", offsetBytes: 0, data: Data())
        ))
        XCTAssertThrowsError(try NeaRerTransferFileChunkCodecV1.encodeChunk(
            NeaRerTransferFileChunkV1(payloadID: 1, transferID: "x", itemID: "i", offsetBytes: 0,
                                      data: Data(repeating: 1, count: 512 * 1024 + 1))
        ))
        XCTAssertThrowsError(try NeaRerTransferFileChunkCodecV1.encodeChunk(
            NeaRerTransferFileChunkV1(payloadID: 1, transferID: "x", itemID: "i",
                                      offsetBytes: UInt64(Int64.max), data: Data([1]))
        ))
    }

    private var unused: Void { () }
}
private extension Data {
    var hex: String { map { String(format: "%02x", $0) }.joined() }
}
