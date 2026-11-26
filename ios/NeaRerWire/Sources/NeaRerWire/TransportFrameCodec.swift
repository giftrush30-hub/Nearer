import Foundation

public enum TransportFrameError: Error, Equatable {
    case emptyFrame
    case frameTooLarge
    case bufferTooLarge
}

/// Four-byte big-endian length-prefixed frame codec for chat/control messages.
/// Large file bytes must use a dedicated streaming transfer path, not this buffer.
public enum TransportFrameCodec {
    public static let maximumFrameBytes = 1_048_576
    public static let maximumBufferedBytes = maximumFrameBytes * 2 + 8

    public static func encode(_ payload: Data) throws -> Data {
        guard !payload.isEmpty else { throw TransportFrameError.emptyFrame }
        guard payload.count <= maximumFrameBytes else { throw TransportFrameError.frameTooLarge }
        var output = Data([
            UInt8((payload.count >> 24) & 0xff),
            UInt8((payload.count >> 16) & 0xff),
            UInt8((payload.count >> 8) & 0xff),
            UInt8(payload.count & 0xff)
        ])
        output.append(payload)
        return output
    }
}

/// Incremental decoder: safely handles arbitrary TCP segmentation and coalescing.
public struct TransportFrameDecoder {
    private var buffer = Data()
    public init() {}
    public var bufferedByteCount: Int { buffer.count }

    public mutating func append(_ bytes: Data) throws -> [Data] {
        guard buffer.count + bytes.count <= TransportFrameCodec.maximumBufferedBytes else {
            throw TransportFrameError.bufferTooLarge
        }
        buffer.append(bytes)
        var frames: [Data] = []
        while buffer.count >= 4 {
            let length = (Int(buffer[0]) << 24) | (Int(buffer[1]) << 16) |
                         (Int(buffer[2]) << 8) | Int(buffer[3])
            guard length > 0 else { throw TransportFrameError.emptyFrame }
            guard length <= TransportFrameCodec.maximumFrameBytes else {
                throw TransportFrameError.frameTooLarge
            }
            guard buffer.count >= 4 + length else { break }
            frames.append(buffer.subdata(in: 4..<(4 + length)))
            buffer.removeSubrange(0..<(4 + length))
        }
        return frames
    }
}
