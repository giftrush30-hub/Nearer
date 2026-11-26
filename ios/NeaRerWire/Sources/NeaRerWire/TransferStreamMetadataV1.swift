import Foundation

public struct NeaRerTransferStreamMetadataV1: Equatable {
    public let payloadID: UInt64
    public let transferID: String
    public let item: NeaRerTransferItemV1
    public let sourceModifiedAtMilliseconds: Int64?
    public let wholeFileSHA256: String

    public init(payloadID: UInt64, transferID: String, item: NeaRerTransferItemV1,
                sourceModifiedAtMilliseconds: Int64?, wholeFileSHA256: String) throws {
        self.payloadID = payloadID
        self.transferID = transferID
        self.item = item
        self.sourceModifiedAtMilliseconds = sourceModifiedAtMilliseconds
        self.wholeFileSHA256 = wholeFileSHA256.lowercased()
        try NeaRerTransferStreamMetadataCodecV1.validate(self)
    }
}

/// Byte-compatible port of Android TransferResumeStreamMetadataCodec (NRSM v1).
/// This envelope binds a stream to one file and its whole-file digest; file bytes
/// travel separately and must be streamed to a temporary destination.
public enum NeaRerTransferStreamMetadataCodecV1 {
    private static let magic: UInt32 = 0x4E52534D
    private static let maximumBytes = 4_096

    public static func encode(_ metadata: NeaRerTransferStreamMetadataV1) throws -> Data {
        try validate(metadata)
        var writer = StreamMetadataWriter()
        writer.u32(magic)
        writer.byte(1)
        writer.u64(metadata.payloadID)
        try writer.string16(metadata.transferID, maxBytes: 128)
        try writer.string16(metadata.item.id, maxBytes: 128)
        try writer.string16(metadata.item.relativePath, maxBytes: 4_096)
        try writer.string16(metadata.item.displayName, maxBytes: 1_020)
        writer.u64(metadata.item.sizeBytes)
        writer.byte(metadata.item.mimeType == nil ? 0 : 1)
        if let mime = metadata.item.mimeType { try writer.string16(mime, maxBytes: 255) }
        writer.byte(metadata.sourceModifiedAtMilliseconds == nil ? 0 : 1)
        if let modified = metadata.sourceModifiedAtMilliseconds {
            writer.u64(UInt64(bitPattern: modified))
        }
        try writer.string16(metadata.wholeFileSHA256, maxBytes: 64)
        guard writer.data.count <= maximumBytes else { throw TransferResumeWireError.invalidLength }
        return writer.data
    }

    public static func decode(_ data: Data) throws -> NeaRerTransferStreamMetadataV1 {
        guard data.count >= 101, data.count <= maximumBytes else {
            throw TransferResumeWireError.invalidLength
        }
        var reader = StreamMetadataReader(data: data)
        guard try reader.u32() == magic else { throw TransferResumeWireError.invalidMagic }
        guard try reader.byte() == 1 else { throw TransferResumeWireError.unsupportedVersion }
        let payloadID = try reader.u64()
        guard payloadID <= UInt64(Int64.max) else {
            throw TransferResumeWireError.invalidField("payload ID")
        }
        let transferID = try reader.string16(maxBytes: 128)
        let itemID = try reader.string16(maxBytes: 128)
        let path = try reader.string16(maxBytes: 4_096)
        let name = try reader.string16(maxBytes: 1_020)
        let size = try reader.u64()
        let hasMime = try reader.byte()
        guard hasMime == 0 || hasMime == 1 else {
            throw TransferResumeWireError.invalidField("MIME flag")
        }
        let mime = hasMime == 1 ? try reader.string16(maxBytes: 255) : nil
        let hasModified = try reader.byte()
        guard hasModified == 0 || hasModified == 1 else {
            throw TransferResumeWireError.invalidField("modified flag")
        }
        let modified = hasModified == 1 ? Int64(bitPattern: try reader.u64()) : nil
        let digest = try reader.string16(maxBytes: 64)
        guard reader.remaining == 0 else { throw TransferResumeWireError.trailingBytes }
        let item = NeaRerTransferItemV1(id: itemID, relativePath: path, displayName: name,
                                       sizeBytes: size, mimeType: mime, kind: .file)
        return try NeaRerTransferStreamMetadataV1(
            payloadID: payloadID, transferID: transferID, item: item,
            sourceModifiedAtMilliseconds: modified, wholeFileSHA256: digest
        )
    }

    static func validate(_ value: NeaRerTransferStreamMetadataV1) throws {
        guard value.payloadID <= UInt64(Int64.max),
              value.sourceModifiedAtMilliseconds.map({ $0 >= 0 }) ?? true,
              value.wholeFileSHA256.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil,
              value.item.kind == .file else {
            throw TransferResumeWireError.invalidField("stream metadata")
        }
        _ = try NeaRerTransferManifestV1(transferID: value.transferID, items: [value.item])
        guard value.transferID.utf8.count <= 128,
              value.item.id.utf8.count <= 128,
              value.item.relativePath.utf8.count <= 4_096,
              value.item.displayName.utf8.count <= 1_020,
              value.item.mimeType.map({ $0.utf8.count <= 255 }) ?? true else {
            throw TransferResumeWireError.invalidField("stream metadata bounds")
        }
    }
}

private struct StreamMetadataWriter {
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
        guard !bytes.isEmpty, bytes.count <= maxBytes, bytes.count <= Int(UInt16.max) else {
            throw TransferResumeWireError.invalidField("metadata string")
        }
        byte(UInt8((bytes.count >> 8) & 0xff))
        byte(UInt8(bytes.count & 0xff))
        data.append(bytes)
    }
}

private struct StreamMetadataReader {
    let data: Data
    private(set) var offset = 0
    var remaining: Int { data.count - offset }
    mutating func byte() throws -> UInt8 {
        guard remaining >= 1 else { throw TransferResumeWireError.invalidLength }
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
    mutating func string16(maxBytes: Int) throws -> String {
        let length = (Int(try byte()) << 8) | Int(try byte())
        guard length > 0, length <= maxBytes, length <= remaining else {
            throw TransferResumeWireError.invalidLength
        }
        let bytes = data.subdata(in: offset..<(offset + length))
        offset += length
        guard let value = String(data: bytes, encoding: .utf8) else {
            throw TransferResumeWireError.invalidField("UTF-8")
        }
        return value
    }
}
