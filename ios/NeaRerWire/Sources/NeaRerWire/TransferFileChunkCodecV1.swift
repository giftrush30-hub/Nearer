import Foundation

public enum NeaRerTransferFileChunkErrorV1: Error, Equatable {
    case invalidMagic
    case unsupportedVersion
    case invalidLength
    case invalidField
    case trailingBytes
}

/// Bounded file-data record carried inside the existing four-byte TCP frame codec.
/// Each chunk is independent and offset-addressed; no whole file is buffered.
public struct NeaRerTransferFileChunkV1: Equatable {
    public let payloadID: UInt64
    public let transferID: String
    public let itemID: String
    public let offsetBytes: UInt64
    public let data: Data
    public init(payloadID: UInt64, transferID: String, itemID: String,
                offsetBytes: UInt64, data: Data) {
        self.payloadID = payloadID
        self.transferID = transferID
        self.itemID = itemID
        self.offsetBytes = offsetBytes
        self.data = data
    }
}

public struct NeaRerTransferFileAckV1: Equatable {
    public let payloadID: UInt64
    public let transferID: String
    public let itemID: String
    public let committedOffsetBytes: UInt64
    public let prefixSHA256: String
    public init(payloadID: UInt64, transferID: String, itemID: String,
                committedOffsetBytes: UInt64, prefixSHA256: String) {
        self.payloadID = payloadID
        self.transferID = transferID
        self.itemID = itemID
        self.committedOffsetBytes = committedOffsetBytes
        self.prefixSHA256 = prefixSHA256.lowercased()
    }
}

public struct NeaRerTransferFileEndV1: Equatable {
    public let payloadID: UInt64
    public let transferID: String
    public let itemID: String
    public let totalSizeBytes: UInt64
    public let wholeFileSHA256: String
    public init(payloadID: UInt64, transferID: String, itemID: String,
                totalSizeBytes: UInt64, wholeFileSHA256: String) {
        self.payloadID = payloadID
        self.transferID = transferID
        self.itemID = itemID
        self.totalSizeBytes = totalSizeBytes
        self.wholeFileSHA256 = wholeFileSHA256.lowercased()
    }
}

public typealias NeaRerTransferAckWaiterV1 = (NeaRerTransferFileAckV1, @escaping (Result<NeaRerTransferFileAckV1, Error>) -> Void) -> Void

public enum NeaRerTransferFileChunkCodecV1 {
    public static let maximumChunkBytes = 512 * 1024
    public static let maximumRecordBytes = 1_048_576

    private static let chunkMagic: UInt32 = 0x4E524643 // NRFC
    private static let ackMagic: UInt32 = 0x4E524641   // NRFA
    private static let endMagic: UInt32 = 0x4E524645   // NRFE

    public static func encodeChunk(_ value: NeaRerTransferFileChunkV1) throws -> Data {
        try validateIDs(value.payloadID, value.transferID, value.itemID)
        guard value.offsetBytes <= UInt64(Int64.max),
              !value.data.isEmpty, value.data.count <= maximumChunkBytes,
              value.offsetBytes <= UInt64(Int64.max) - UInt64(value.data.count) else {
            throw NeaRerTransferFileChunkErrorV1.invalidLength
        }
        var writer = FileChunkWriter()
        writer.u32(chunkMagic); writer.byte(1)
        writer.u64(value.payloadID)
        try writer.string16(value.transferID, maxBytes: 128)
        try writer.string16(value.itemID, maxBytes: 128)
        writer.u64(value.offsetBytes)
        writer.u32(UInt32(value.data.count))
        writer.data.append(value.data)
        guard writer.data.count <= maximumRecordBytes else {
            throw NeaRerTransferFileChunkErrorV1.invalidLength
        }
        return writer.data
    }

    public static func decodeChunk(_ data: Data) throws -> NeaRerTransferFileChunkV1 {
        guard data.count <= maximumRecordBytes else { throw NeaRerTransferFileChunkErrorV1.invalidLength }
        var reader = FileChunkReader(data: data)
        try reader.expectMagic(chunkMagic)
        try reader.expectVersion()
        let payloadID = try reader.u64()
        let transferID = try reader.string16(maxBytes: 128)
        let itemID = try reader.string16(maxBytes: 128)
        let offset = try reader.u64()
        let length = Int(try reader.u32())
        guard length > 0, length <= maximumChunkBytes, length == reader.remaining,
              offset <= UInt64(Int64.max) - UInt64(length) else {
            throw NeaRerTransferFileChunkErrorV1.invalidLength
        }
        let bytes = try reader.readBytes(length)
        try validateIDs(payloadID, transferID, itemID)
        return NeaRerTransferFileChunkV1(payloadID: payloadID, transferID: transferID,
                                         itemID: itemID, offsetBytes: offset, data: bytes)
    }

    public static func encodeAck(_ value: NeaRerTransferFileAckV1) throws -> Data {
        try validateIDs(value.payloadID, value.transferID, value.itemID)
        guard value.committedOffsetBytes <= UInt64(Int64.max),
              isSHA256(value.prefixSHA256) else { throw NeaRerTransferFileChunkErrorV1.invalidField }
        var writer = FileChunkWriter()
        writer.u32(ackMagic); writer.byte(1); writer.u64(value.payloadID)
        try writer.string16(value.transferID, maxBytes: 128)
        try writer.string16(value.itemID, maxBytes: 128)
        writer.u64(value.committedOffsetBytes)
        try writer.string16(value.prefixSHA256, maxBytes: 64)
        return writer.data
    }

    public static func decodeAck(_ data: Data) throws -> NeaRerTransferFileAckV1 {
        var reader = FileChunkReader(data: data)
        try reader.expectMagic(ackMagic); try reader.expectVersion()
        let payloadID = try reader.u64()
        let transferID = try reader.string16(maxBytes: 128)
        let itemID = try reader.string16(maxBytes: 128)
        let offset = try reader.u64()
        let digest = try reader.string16(maxBytes: 64)
        guard reader.remaining == 0, offset <= UInt64(Int64.max), isSHA256(digest) else {
            throw NeaRerTransferFileChunkErrorV1.invalidField
        }
        try validateIDs(payloadID, transferID, itemID)
        return NeaRerTransferFileAckV1(payloadID: payloadID, transferID: transferID,
                                       itemID: itemID, committedOffsetBytes: offset,
                                       prefixSHA256: digest)
    }

    public static func encodeEnd(_ value: NeaRerTransferFileEndV1) throws -> Data {
        try validateIDs(value.payloadID, value.transferID, value.itemID)
        guard value.totalSizeBytes <= UInt64(Int64.max), isSHA256(value.wholeFileSHA256) else {
            throw NeaRerTransferFileChunkErrorV1.invalidField
        }
        var writer = FileChunkWriter()
        writer.u32(endMagic); writer.byte(1); writer.u64(value.payloadID)
        try writer.string16(value.transferID, maxBytes: 128)
        try writer.string16(value.itemID, maxBytes: 128)
        writer.u64(value.totalSizeBytes)
        try writer.string16(value.wholeFileSHA256, maxBytes: 64)
        return writer.data
    }

    public static func decodeEnd(_ data: Data) throws -> NeaRerTransferFileEndV1 {
        var reader = FileChunkReader(data: data)
        try reader.expectMagic(endMagic); try reader.expectVersion()
        let payloadID = try reader.u64()
        let transferID = try reader.string16(maxBytes: 128)
        let itemID = try reader.string16(maxBytes: 128)
        let size = try reader.u64()
        let digest = try reader.string16(maxBytes: 64)
        guard reader.remaining == 0, size <= UInt64(Int64.max), isSHA256(digest) else {
            throw NeaRerTransferFileChunkErrorV1.invalidField
        }
        try validateIDs(payloadID, transferID, itemID)
        return NeaRerTransferFileEndV1(payloadID: payloadID, transferID: transferID,
                                       itemID: itemID, totalSizeBytes: size,
                                       wholeFileSHA256: digest)
    }

    private static func validateIDs(_ payloadID: UInt64, _ transferID: String, _ itemID: String) throws {
        guard payloadID <= UInt64(Int64.max),
              !transferID.isEmpty, transferID.utf8.count <= 128,
              !itemID.isEmpty, itemID.utf8.count <= 128 else {
            throw NeaRerTransferFileChunkErrorV1.invalidField
        }
    }

    private static func isSHA256(_ value: String) -> Bool {
        value.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil
    }
}

private struct FileChunkWriter {
    var data = Data()
    mutating func byte(_ value: UInt8) { data.append(value) }
    mutating func u32(_ value: UInt32) {
        for shift in stride(from: 24, through: 0, by: -8) { byte(UInt8((value >> UInt32(shift)) & 0xff)) }
    }
    mutating func u64(_ value: UInt64) {
        for shift in stride(from: 56, through: 0, by: -8) { byte(UInt8((value >> UInt64(shift)) & 0xff)) }
    }
    mutating func string16(_ value: String, maxBytes: Int) throws {
        let bytes = Data(value.utf8)
        guard !bytes.isEmpty, bytes.count <= maxBytes else {
            throw NeaRerTransferFileChunkErrorV1.invalidField
        }
        byte(UInt8((bytes.count >> 8) & 0xff)); byte(UInt8(bytes.count & 0xff))
        data.append(bytes)
    }
}

private struct FileChunkReader {
    let data: Data
    private(set) var offset = 0
    var remaining: Int { data.count - offset }
    mutating func byte() throws -> UInt8 {
        guard remaining >= 1 else { throw NeaRerTransferFileChunkErrorV1.invalidLength }
        defer { offset += 1 }; return data[offset]
    }
    mutating func u32() throws -> UInt32 {
        var value: UInt32 = 0
        for _ in 0..<4 { value = (value << 8) | UInt32(try byte()) }
        return value
    }
    mutating func u64() throws -> UInt64 {
        var value: UInt64 = 0
        for _ in 0..<8 { value = (value << 8) | UInt64(try byte()) }
        return value
    }
    mutating func expectMagic(_ magic: UInt32) throws {
        guard try u32() == magic else { throw NeaRerTransferFileChunkErrorV1.invalidMagic }
    }
    mutating func expectVersion() throws {
        guard try byte() == 1 else { throw NeaRerTransferFileChunkErrorV1.unsupportedVersion }
    }
    mutating func string16(maxBytes: Int) throws -> String {
        let length = (Int(try byte()) << 8) | Int(try byte())
        guard length > 0, length <= maxBytes, length <= remaining else {
            throw NeaRerTransferFileChunkErrorV1.invalidLength
        }
        let bytes = data.subdata(in: offset..<(offset + length)); offset += length
        guard let value = String(data: bytes, encoding: .utf8) else {
            throw NeaRerTransferFileChunkErrorV1.invalidField
        }
        return value
    }
    mutating func readBytes(_ length: Int) throws -> Data {
        guard length >= 0, length <= remaining else { throw NeaRerTransferFileChunkErrorV1.invalidLength }
        let bytes = data.subdata(in: offset..<(offset + length)); offset += length
        return bytes
    }
}
