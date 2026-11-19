import XCTest
@testable import NeaRerWire

final class TransportFrameCodecTests: XCTestCase {
    func testFrameCanArriveInArbitraryTCPFragments() throws {
        let encoded = try TransportFrameCodec.encode(Data("hello".utf8))
        var decoder = TransportFrameDecoder()
        XCTAssertEqual(try decoder.append(encoded.prefix(2)), [])
        XCTAssertEqual(try decoder.append(encoded.dropFirst(2).prefix(3)), [])
        XCTAssertEqual(try decoder.append(encoded.dropFirst(5)), [Data("hello".utf8)])
        XCTAssertEqual(decoder.bufferedByteCount, 0)
    }

    func testDecoderReturnsMultipleCoalescedFrames() throws {
        let first = try TransportFrameCodec.encode(Data("one".utf8))
        let second = try TransportFrameCodec.encode(Data("two".utf8))
        var decoder = TransportFrameDecoder()
        XCTAssertEqual(try decoder.append(first + second), [Data("one".utf8), Data("two".utf8)])
    }

    func testDecoderRejectsZeroAndOversizedLengths() {
        var zero = TransportFrameDecoder()
        XCTAssertThrowsError(try zero.append(Data([0, 0, 0, 0])))
        var oversized = TransportFrameDecoder()
        XCTAssertThrowsError(try oversized.append(Data([0, 0x10, 0, 1])))
    }
}
