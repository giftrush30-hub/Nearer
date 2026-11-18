import Foundation

public enum TransferManifestError: Error, Equatable {
    case invalidHeader
    case unsupportedVersion
    case invalidLength
    case invalidUTF8
    case invalidItemCount
    case invalidItem(String)
    case trailingBytes
}

/// Byte-compatible port of Android TransferBundleManifestCodec (NRB1, version 1).
/// File contents are never embedded in this metadata object.
public struct NeaRerTransferItemV1: Equatable {
    public enum Kind: UInt8 { case file = 0, directory = 1 }
    public let id: String
    public let relativePath: String
    public let displayName: String
    public let sizeBytes: UInt64
    public let mimeType: String?
    public let kind: Kind

    public init(id: String, relativePath: String, displayName: String,
                sizeBytes: UInt64, mimeType: String? = nil, kind: Kind = .file) {
        self.id = id
        self.relativePath = relativePath
        self.displayName = displayName
        self.sizeBytes = sizeBytes
        self.mimeType = mimeType
        self.kind = kind
    }
}

public struct NeaRerTransferManifestV1: Equatable {
    public static let maximumItems = 10_000
    public static let maximumEncodedBytes = 1_048_576
    public let transferID: String
    public let items: [NeaRerTransferItemV1]

    public init(transferID: String, items: [NeaRerTransferItemV1]) throws {
        self.transferID = transferID
        self.items = items
        try validate()
    }

    public func encode() throws -> Data {
        try validate()
        var writer = BinaryWriter()
        writer.write(UInt32(0x4E524231))
        writer.write(UInt32(1))
        try writer.writeString(transferID, maxBytes: 512)
        writer.write(UInt32(items.count))
        for item in items {
            try writer.writeString(item.id, maxBytes: 512)
            try writer.writeString(item.relativePath, maxBytes: 1_024)
            try writer.writeString(item.displayName, maxBytes: 1_024)
            writer.write(item.sizeBytes)
            writer.write(item.mimeType != nil)
            if let mime = item.mimeType { try writer.writeString(mime, maxBytes: 1_024) }
            writer.write(item.kind.rawValue)
            guard writer.data.count <= Self.maximumEncodedBytes else {
                throw TransferManifestError.invalidLength
            }
        }
        guard writer.data.count <= Self.maximumEncodedBytes else { throw TransferManifestError.invalidLength }
        return writer.data
    }

    public static func decode(_ data: Data) throws -> NeaRerTransferManifestV1 {
        guard !data.isEmpty, data.count <= maximumEncodedBytes else { throw TransferManifestError.invalidLength }
        var reader = BinaryReader(data: data)
        guard try reader.readUInt32() == 0x4E524231 else { throw TransferManifestError.invalidHeader }
        guard try reader.readUInt32() == 1 else { throw TransferManifestError.unsupportedVersion }
        let transferID = try reader.readString(maxBytes: 512)
        let count = Int(try reader.readUInt32())
        guard (1...maximumItems).contains(count) else { throw TransferManifestError.invalidItemCount }
        var items: [NeaRerTransferItemV1] = []
        items.reserveCapacity(count)
        for _ in 0..<count {
            let id = try reader.readString(maxBytes: 512)
            let path = try reader.readString(maxBytes: 1_024)
            let name = try reader.readString(maxBytes: 1_024)
            let size = try reader.readUInt64()
            let hasMime = try reader.readBool()
            let mime = hasMime ? try reader.readString(maxBytes: 1_024) : nil
            guard let kind = NeaRerTransferItemV1.Kind(rawValue: try reader.readByte()) else {
                throw TransferManifestError.invalidItem("unknown kind")
            }
            items.append(NeaRerTransferItemV1(id: id, relativePath: path, displayName: name,
                                              sizeBytes: size, mimeType: mime, kind: kind))
        }
        guard reader.remaining == 0 else { throw TransferManifestError.trailingBytes }
        return try NeaRerTransferManifestV1(transferID: transferID, items: items)
    }

    private func validate() throws {
        guard !transferID.isEmpty, transferID.count <= 128,
              !transferID.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else {
            throw TransferManifestError.invalidItem("transfer ID")
        }
        guard !items.isEmpty, items.count <= Self.maximumItems else {
            throw TransferManifestError.invalidItemCount
        }
        var ids = Set<String>()
        var paths: [String: NeaRerTransferItemV1.Kind] = [:]
        for item in items {
            guard !item.id.isEmpty, item.id.count <= 128,
                  !item.id.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }),
                  ids.insert(item.id).inserted else { throw TransferManifestError.invalidItem("duplicate/invalid ID") }
            guard Self.isSafeRelativePath(item.relativePath),
                  paths[item.relativePath] == nil else { throw TransferManifestError.invalidItem("unsafe/duplicate path") }
            paths[item.relativePath] = item.kind
            guard !item.displayName.isEmpty, item.displayName.count <= 255,
                  !item.displayName.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }),
                  item.displayName == Self.safeLeafName(item.displayName) else {
                throw TransferManifestError.invalidItem("unsafe display name")
            }
            if item.kind == .directory && item.sizeBytes != 0 {
                throw TransferManifestError.invalidItem("directory size")
            }
            if let mime = item.mimeType,
               mime.count > 255 || mime.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) {
                throw TransferManifestError.invalidItem("MIME metadata")
            }
        }
        for path in paths.keys {
            let components = path.split(separator: "/").map(String.init)
            if components.count > 1 {
                for index in 1..<components.count {
                    let parent = components.prefix(index).joined(separator: "/")
                    if paths[parent] == .file { throw TransferManifestError.invalidItem("file used as parent") }
                }
            }
        }
    }

    private static func isSafeRelativePath(_ path: String) -> Bool {
        guard !path.isEmpty, path.count <= 1_024, !path.hasPrefix("/"),
              !path.hasPrefix("\\"), !path.contains("\\"),
              !path.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else { return false }
        return path.split(separator: "/", omittingEmptySubsequences: false).allSatisfy {
            let part = String($0)
            return !part.isEmpty && part != "." && part != ".." && part == safeLeafName(part)
        }
    }

    private static func safeLeafName(_ raw: String) -> String {
        let leaf = raw.split(whereSeparator: { $0 == "/" || $0 == "\\" }).last.map(String.init) ?? raw
        let invalid = CharacterSet(charactersIn: "/\\:*?\"<>|")
        let cleaned = leaf.unicodeScalars.map { invalid.contains($0) ? "_" : String($0) }.joined()
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "."))
        let limited = String(cleaned.prefix(120))
        return limited.isEmpty ? "shared-file" : limited
    }
}

private struct BinaryWriter {
    var data = Data()
    mutating func write(_ value: UInt8) { data.append(value) }
    mutating func write(_ value: Bool) { write(value ? UInt8(1) : UInt8(0)) }
    mutating func write(_ value: UInt32) {
        write(UInt8((value >> 24) & 0xff)); write(UInt8((value >> 16) & 0xff))
        write(UInt8((value >> 8) & 0xff)); write(UInt8(value & 0xff))
    }
    mutating func write(_ value: UInt64) {
        for shift in stride(from: 56, through: 0, by: -8) { write(UInt8((value >> UInt64(shift)) & 0xff)) }
    }
    mutating func writeString(_ value: String, maxBytes: Int) throws {
        let bytes = Data(value.utf8)
        guard bytes.count <= maxBytes else { throw TransferManifestError.invalidLength }
        write(UInt32(bytes.count)); data.append(bytes)
    }
}

private struct BinaryReader {
    let data: Data
    private(set) var offset = 0
    var remaining: Int { data.count - offset }
    mutating func readByte() throws -> UInt8 {
        guard remaining >= 1 else { throw TransferManifestError.invalidLength }
        defer { offset += 1 }; return data[offset]
    }
    mutating func readBool() throws -> Bool {
        let value = try readByte()
        guard value == 0 || value == 1 else { throw TransferManifestError.invalidLength }
        return value == 1
    }
    mutating func readUInt32() throws -> UInt32 {
        var result: UInt32 = 0
        for _ in 0..<4 { result = (result << 8) | UInt32(try readByte()) }
        return result
    }
    mutating func readUInt64() throws -> UInt64 {
        var result: UInt64 = 0
        for _ in 0..<8 { result = (result << 8) | UInt64(try readByte()) }
        return result
    }
    mutating func readString(maxBytes: Int) throws -> String {
        let length = Int(try readUInt32())
        guard length <= maxBytes, length <= remaining else { throw TransferManifestError.invalidLength }
        let bytes = data.subdata(in: offset..<(offset + length)); offset += length
        guard let value = String(data: bytes, encoding: .utf8) else { throw TransferManifestError.invalidUTF8 }
        return value
    }
}
