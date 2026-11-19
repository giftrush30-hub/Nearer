import Foundation

public enum TransferResumeWireError: Error, Equatable {
    case invalidLength
    case invalidMagic
    case unsupportedVersion
    case invalidField(String)
    case trailingBytes
}

public enum NeaRerResumeReasonV1: String, CaseIterable {
    case resumeAccepted = "RESUME_ACCEPTED"
    case peerUnsupported = "PEER_UNSUPPORTED"
    case noValidCheckpoint = "NO_VALID_CHECKPOINT"
    case sourceChanged = "SOURCE_CHANGED"
    case offsetInvalid = "OFFSET_INVALID"
    case alreadyComplete = "ALREADY_COMPLETE"
}

public struct NeaRerResumeProposalV1: Equatable {
    public let transferID: String
    public let itemID: String
    public let relativePath: String
    public let expectedSizeBytes: Int64
    public let sourceModifiedAtMilliseconds: Int64?
    public let committedOffsetBytes: Int64
    public let prefixSHA256: String
    public init(transferID: String, itemID: String, relativePath: String,
                expectedSizeBytes: Int64, sourceModifiedAtMilliseconds: Int64?,
                committedOffsetBytes: Int64, prefixSHA256: String) {
        self.transferID = transferID; self.itemID = itemID; self.relativePath = relativePath
        self.expectedSizeBytes = expectedSizeBytes
        self.sourceModifiedAtMilliseconds = sourceModifiedAtMilliseconds
        self.committedOffsetBytes = committedOffsetBytes; self.prefixSHA256 = prefixSHA256
    }
}

public struct NeaRerResumeDecisionV1: Equatable {
    public let acceptedOffsetBytes: Int64
    public let reason: NeaRerResumeReasonV1
    public init(acceptedOffsetBytes: Int64, reason: NeaRerResumeReasonV1) {
        self.acceptedOffsetBytes = acceptedOffsetBytes; self.reason = reason
    }
}

public enum NeaRerTransferResumeCodecV1 {
    public static let checkpointGranularityBytes: Int64 = 4 * 1024 * 1024
    private static let proposalMagic: UInt32 = 0x4E525253
    private static let decisionMagic: UInt32 = 0x4E525244
    private static let noResumeMagic: UInt32 = 0x4E52524E
    private static let maximumBytes = 4_096

    public static func encodeProposal(payloadID: UInt64, proposal: NeaRerResumeProposalV1) throws -> Data {
        try validateProposal(proposal)
        var writer = ResumeWriter()
        writer.u32(proposalMagic); writer.byte(1); writer.u64(payloadID)
        try writer.string16(proposal.transferID, maxBytes: 128)
        try writer.string16(proposal.itemID, maxBytes: 128)
        try writer.string16(proposal.relativePath, maxBytes: 4_096)
        writer.i64(proposal.expectedSizeBytes)
        writer.byte(proposal.sourceModifiedAtMilliseconds == nil ? 0 : 1)
        if let modified = proposal.sourceModifiedAtMilliseconds { writer.i64(modified) }
        writer.i64(proposal.committedOffsetBytes)
        try writer.string16(proposal.prefixSHA256.lowercased(), maxBytes: 64)
        guard writer.data.count <= maximumBytes else { throw TransferResumeWireError.invalidLength }
        return writer.data
    }

    public static func decodeProposal(_ data: Data) throws -> (payloadID: UInt64, proposal: NeaRerResumeProposalV1) {
        guard data.count >= 1 + 4 + 1 + 8 + 2 * 4 + 8 + 1 + 8 + 2 + 64,
              data.count <= maximumBytes else { throw TransferResumeWireError.invalidLength }
        var reader = ResumeReader(data: data)
        try reader.magic(proposalMagic)
        try reader.version()
        let payloadID = try reader.u64()
        let transferID = try reader.string16(maxBytes: 128)
        let itemID = try reader.string16(maxBytes: 128)
        let path = try reader.string16(maxBytes: 4_096)
        let size = try reader.i64()
        let hasModified = try reader.byte()
        guard hasModified == 0 || hasModified == 1 else { throw TransferResumeWireError.invalidField("modified flag") }
        let modified = hasModified == 1 ? try reader.i64() : nil
        let offset = try reader.i64()
        let digest = try reader.string16(maxBytes: 64)
        guard reader.remaining == 0 else { throw TransferResumeWireError.trailingBytes }
        let proposal = NeaRerResumeProposalV1(
            transferID: transferID, itemID: itemID, relativePath: path,
            expectedSizeBytes: size, sourceModifiedAtMilliseconds: modified,
            committedOffsetBytes: offset, prefixSHA256: digest
        )
        try validateProposal(proposal)
        return (payloadID, proposal)
    }

    public static func encodeNoResume(payloadID: UInt64) -> Data {
        var writer = ResumeWriter()
        writer.u32(noResumeMagic); writer.byte(1); writer.u64(payloadID)
        return writer.data
    }

    public static func decodeNoResume(_ data: Data) throws -> UInt64 {
        guard data.count == 13 else { throw TransferResumeWireError.invalidLength }
        var reader = ResumeReader(data: data)
        try reader.magic(noResumeMagic); try reader.version()
        let payloadID = try reader.u64()
        guard reader.remaining == 0 else { throw TransferResumeWireError.trailingBytes }
        return payloadID
    }

    public static func encodeDecision(payloadID: UInt64, decision: NeaRerResumeDecisionV1) throws -> Data {
        try validateDecision(decision)
        var writer = ResumeWriter()
        writer.u32(decisionMagic); writer.byte(1); writer.u64(payloadID)
        writer.i64(decision.acceptedOffsetBytes)
        try writer.ascii8(decision.reason.rawValue, maxBytes: 32)
        guard writer.data.count <= maximumBytes else { throw TransferResumeWireError.invalidLength }
        return writer.data
    }

    public static func decodeDecision(_ data: Data) throws -> (payloadID: UInt64, decision: NeaRerResumeDecisionV1) {
        guard data.count >= 22, data.count <= maximumBytes else { throw TransferResumeWireError.invalidLength }
        var reader = ResumeReader(data: data)
        try reader.magic(decisionMagic); try reader.version()
        let payloadID = try reader.u64()
        let offset = try reader.i64()
        let reasonText = try reader.ascii8(maxBytes: 32)
        guard reader.remaining == 0, let reason = NeaRerResumeReasonV1(rawValue: reasonText) else {
            throw TransferResumeWireError.invalidField("decision reason")
        }
        let decision = NeaRerResumeDecisionV1(acceptedOffsetBytes: offset, reason: reason)
        try validateDecision(decision)
        return (payloadID, decision)
    }

    private static func validateProposal(_ p: NeaRerResumeProposalV1) throws {
        guard !p.transferID.isEmpty, p.transferID.utf8.count <= 128,
              !p.itemID.isEmpty, p.itemID.utf8.count <= 128,
              !p.relativePath.isEmpty, p.relativePath.utf8.count <= 4_096,
              p.expectedSizeBytes >= 0,
              p.sourceModifiedAtMilliseconds.map({ $0 >= 0 }) ?? true,
              p.committedOffsetBytes > 0, p.committedOffsetBytes < p.expectedSizeBytes,
              p.committedOffsetBytes % checkpointGranularityBytes == 0,
              p.prefixSHA256.range(of: "^[a-fA-F0-9]{64}$", options: .regularExpression) != nil else {
            throw TransferResumeWireError.invalidField("resume proposal")
        }
    }

    private static func validateDecision(_ d: NeaRerResumeDecisionV1) throws {
        if d.reason == .resumeAccepted {
            guard d.acceptedOffsetBytes > 0,
                  d.acceptedOffsetBytes % checkpointGranularityBytes == 0 else {
                throw TransferResumeWireError.invalidField("accepted resume offset")
            }
        } else if d.acceptedOffsetBytes != 0 {
            throw TransferResumeWireError.invalidField("rejected resume offset")
        }
    }
}

private struct ResumeWriter {
    var data = Data()
    mutating func byte(_ value: UInt8) { data.append(value) }
    mutating func u32(_ value: UInt32) {
        for shift in stride(from: 24, through: 0, by: -8) { byte(UInt8((value >> UInt32(shift)) & 0xff)) }
    }
    mutating func u64(_ value: UInt64) {
        for shift in stride(from: 56, through: 0, by: -8) { byte(UInt8((value >> UInt64(shift)) & 0xff)) }
    }
    mutating func i64(_ value: Int64) { u64(UInt64(bitPattern: value)) }
    mutating func string16(_ value: String, maxBytes: Int) throws {
        let bytes = Data(value.utf8)
        guard !bytes.isEmpty, bytes.count <= maxBytes, bytes.count <= Int(UInt16.max) else {
            throw TransferResumeWireError.invalidField("string")
        }
        byte(UInt8((bytes.count >> 8) & 0xff)); byte(UInt8(bytes.count & 0xff)); data.append(bytes)
    }
    mutating func ascii8(_ value: String, maxBytes: Int) throws {
        let bytes = Data(value.utf8)
        guard !bytes.isEmpty, bytes.count <= maxBytes else { throw TransferResumeWireError.invalidField("reason") }
        byte(UInt8(bytes.count)); data.append(bytes)
    }
}

private struct ResumeReader {
    let data: Data
    private(set) var offset = 0
    var remaining: Int { data.count - offset }
    mutating func byte() throws -> UInt8 {
        guard remaining >= 1 else { throw TransferResumeWireError.invalidLength }
        defer { offset += 1 }; return data[offset]
    }
    mutating func magic(_ expected: UInt32) throws {
        var actual: UInt32 = 0
        for _ in 0..<4 { actual = (actual << 8) | UInt32(try byte()) }
        guard actual == expected else { throw TransferResumeWireError.invalidMagic }
    }
    mutating func version() throws {
        guard try byte() == 1 else { throw TransferResumeWireError.unsupportedVersion }
    }
    mutating func u64() throws -> UInt64 {
        var value: UInt64 = 0
        for _ in 0..<8 { value = (value << 8) | UInt64(try byte()) }
        return value
    }
    mutating func i64() throws -> Int64 { Int64(bitPattern: try u64()) }
    mutating func string16(maxBytes: Int) throws -> String {
        let length = (Int(try byte()) << 8) | Int(try byte())
        guard length > 0, length <= maxBytes, length <= remaining else { throw TransferResumeWireError.invalidLength }
        let bytes = data.subdata(in: offset..<(offset + length)); offset += length
        guard let value = String(data: bytes, encoding: .utf8) else { throw TransferResumeWireError.invalidField("UTF-8") }
        return value
    }
    mutating func ascii8(maxBytes: Int) throws -> String {
        let length = Int(try byte())
        guard length > 0, length <= maxBytes, length <= remaining else { throw TransferResumeWireError.invalidLength }
        let bytes = data.subdata(in: offset..<(offset + length)); offset += length
        guard let value = String(data: bytes, encoding: .ascii) else { throw TransferResumeWireError.invalidField("ASCII") }
        return value
    }
}
