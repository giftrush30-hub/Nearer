#if canImport(CryptoKit)
import Foundation
import CryptoKit

public enum NeaRerTransferFileStreamErrorV1: Error, Equatable {
    case sourceChanged
    case unexpectedEOF
    case wrongTransfer
    case outOfOrderChunk
    case fileTooLarge
    case digestMismatch
    case destinationExists
    case unsafeDestination
    case alreadyFinished
    case invalidAcknowledgement
}

public final class NeaRerTransferFileStreamWriterV1 {
    public let metadata: NeaRerTransferStreamMetadataV1
    public let chunkSize: Int
    private let handle: FileHandle
    private var hasher = SHA256()
    private var offset: UInt64 = 0
    private var finished = false

    public init(fileURL: URL, metadata: NeaRerTransferStreamMetadataV1,
                chunkSize: Int = NeaRerTransferFileChunkCodecV1.maximumChunkBytes) throws {
        guard chunkSize > 0, chunkSize <= NeaRerTransferFileChunkCodecV1.maximumChunkBytes,
              let values = try? fileURL.resourceValues(forKeys: [.fileSizeKey]),
              let fileSize = values.fileSize, fileSize >= 0,
              UInt64(fileSize) == metadata.item.sizeBytes else {
            throw NeaRerTransferFileStreamErrorV1.sourceChanged
        }
        self.metadata = metadata
        self.chunkSize = chunkSize
        self.handle = try FileHandle(forReadingFrom: fileURL)
    }

    public var bytesRead: UInt64 { offset }

    public static func initialAck(for metadata: NeaRerTransferStreamMetadataV1) -> NeaRerTransferFileAckV1 {
        let emptyDigest = SHA256.hash(data: Data()).map { String(format: "%02x", $0) }.joined()
        return NeaRerTransferFileAckV1(
            payloadID: metadata.payloadID, transferID: metadata.transferID,
            itemID: metadata.item.id, committedOffsetBytes: 0, prefixSHA256: emptyDigest
        )
    }

    public func currentAck() -> NeaRerTransferFileAckV1 {
        let checkpointHasher = hasher
        let digest = checkpointHasher.finalize().map { String(format: "%02x", $0) }.joined()
        return NeaRerTransferFileAckV1(
            payloadID: metadata.payloadID, transferID: metadata.transferID,
            itemID: metadata.item.id, committedOffsetBytes: offset, prefixSHA256: digest
        )
    }

    /// Reads one bounded chunk. Returns nil at the declared end of the file.
    public func nextChunk() throws -> NeaRerTransferFileChunkV1? {
        guard !finished else { throw NeaRerTransferFileStreamErrorV1.alreadyFinished }
        if offset == metadata.item.sizeBytes { return nil }
        let remaining = metadata.item.sizeBytes - offset
        let count = Int(min(UInt64(chunkSize), remaining))
        guard let bytes = try handle.read(upToCount: count), !bytes.isEmpty else {
            throw NeaRerTransferFileStreamErrorV1.unexpectedEOF
        }
        guard UInt64(bytes.count) <= remaining else {
            throw NeaRerTransferFileStreamErrorV1.sourceChanged
        }
        hasher.update(data: bytes)
        let chunk = NeaRerTransferFileChunkV1(
            payloadID: metadata.payloadID, transferID: metadata.transferID,
            itemID: metadata.item.id, offsetBytes: offset, data: bytes
        )
        offset += UInt64(bytes.count)
        return chunk
    }

    /// Verifies the source did not change during reading and emits the terminal record.
    public func finish() throws -> NeaRerTransferFileEndV1 {
        guard !finished else { throw NeaRerTransferFileStreamErrorV1.alreadyFinished }
        guard offset == metadata.item.sizeBytes else {
            throw NeaRerTransferFileStreamErrorV1.unexpectedEOF
        }
        let extra = try handle.read(upToCount: 1) ?? Data()
        guard extra.isEmpty else { throw NeaRerTransferFileStreamErrorV1.sourceChanged }
        let digest = hasher.finalize().map { String(format: "%02x", $0) }.joined()
        guard digest == metadata.wholeFileSHA256 else {
            throw NeaRerTransferFileStreamErrorV1.digestMismatch
        }
        finished = true
        try handle.close()
        return NeaRerTransferFileEndV1(
            payloadID: metadata.payloadID, transferID: metadata.transferID,
            itemID: metadata.item.id, totalSizeBytes: offset,
            wholeFileSHA256: digest
        )
    }

    public func close() {
        if !finished { try? handle.close(); finished = true }
    }

    deinit { close() }
}

public final class NeaRerTransferFileStreamReceiverV1 {
    public struct FinalizedFile {
        public let url: URL
        public let finalAck: NeaRerTransferFileAckV1
    }

    public static let checkpointIntervalBytes: UInt64 = 4 * 1024 * 1024

    public let metadata: NeaRerTransferStreamMetadataV1
    private let fileManager: FileManager
    private let rootDirectory: URL
    private let stagingURL: URL
    private let destinationURL: URL
    private var handle: FileHandle
    private var hasher = SHA256()
    private var bytesWritten: UInt64 = 0
    private var finished = false

    public init(metadata: NeaRerTransferStreamMetadataV1,
                destinationDirectory: URL,
                fileManager: FileManager = .default) throws {
        self.metadata = metadata
        self.fileManager = fileManager
        _ = try NeaRerTransferManifestV1(transferID: metadata.transferID, items: [metadata.item])
        try fileManager.createDirectory(at: destinationDirectory, withIntermediateDirectories: true)
        self.rootDirectory = destinationDirectory.resolvingSymlinksInPath().standardizedFileURL

        let stagingDirectory = rootDirectory.appendingPathComponent(".nearer-staging", isDirectory: true)
        try fileManager.createDirectory(at: stagingDirectory, withIntermediateDirectories: true)
        self.stagingURL = stagingDirectory.appendingPathComponent(UUID().uuidString + ".partial")
        let relative = metadata.item.relativePath
        let components = relative.split(separator: "/").map(String.init)
        let target = components.reduce(rootDirectory) { $0.appendingPathComponent($1) }
            .standardizedFileURL
        guard target.path.hasPrefix(rootDirectory.path + "/") else {
            throw NeaRerTransferFileStreamErrorV1.unsafeDestination
        }
        self.destinationURL = target
        guard !fileManager.fileExists(atPath: destinationURL.path) else {
            throw NeaRerTransferFileStreamErrorV1.destinationExists
        }
        guard fileManager.createFile(atPath: stagingURL.path, contents: Data()) else {
            throw CocoaError(.fileWriteUnknown)
        }
        self.handle = try FileHandle(forWritingTo: stagingURL)
    }

    /// Initial zero-offset ACK is sent only after the receiver has approved the transfer.
    public func initialAck() -> NeaRerTransferFileAckV1 { makeAck() }

    /// Writes sequentially to a private partial file. ACKs are emitted only after
    /// checkpoint synchronization, bounding receiver memory and recovery loss.
    public func append(_ chunk: NeaRerTransferFileChunkV1) throws -> NeaRerTransferFileAckV1? {
        guard !finished else { throw NeaRerTransferFileStreamErrorV1.alreadyFinished }
        guard chunk.payloadID == metadata.payloadID,
              chunk.transferID == metadata.transferID,
              chunk.itemID == metadata.item.id else {
            throw NeaRerTransferFileStreamErrorV1.wrongTransfer
        }
        guard chunk.offsetBytes == bytesWritten else {
            throw NeaRerTransferFileStreamErrorV1.outOfOrderChunk
        }
        guard !chunk.data.isEmpty,
              chunk.data.count <= NeaRerTransferFileChunkCodecV1.maximumChunkBytes,
              bytesWritten <= metadata.item.sizeBytes,
              UInt64(chunk.data.count) <= metadata.item.sizeBytes - bytesWritten else {
            throw NeaRerTransferFileStreamErrorV1.fileTooLarge
        }
        try handle.write(contentsOf: chunk.data)
        hasher.update(data: chunk.data)
        bytesWritten += UInt64(chunk.data.count)
        if bytesWritten < metadata.item.sizeBytes && bytesWritten % Self.checkpointIntervalBytes == 0 {
            try handle.synchronize()
            return makeAck()
        }
        return nil
    }

    public func finish(_ end: NeaRerTransferFileEndV1) throws -> FinalizedFile {
        guard !finished else { throw NeaRerTransferFileStreamErrorV1.alreadyFinished }
        guard end.payloadID == metadata.payloadID,
              end.transferID == metadata.transferID,
              end.itemID == metadata.item.id,
              end.totalSizeBytes == metadata.item.sizeBytes,
              bytesWritten == metadata.item.sizeBytes else {
            throw NeaRerTransferFileStreamErrorV1.wrongTransfer
        }
        let finalHasher = hasher
        let digest = finalHasher.finalize().map { String(format: "%02x", $0) }.joined()
        guard digest == metadata.wholeFileSHA256, digest == end.wholeFileSHA256 else {
            throw NeaRerTransferFileStreamErrorV1.digestMismatch
        }
        try handle.synchronize()
        try handle.close()

        let parent = destinationURL.deletingLastPathComponent()
        try fileManager.createDirectory(at: parent, withIntermediateDirectories: true)
        let resolvedParent = parent.resolvingSymlinksInPath().standardizedFileURL
        guard resolvedParent.path == rootDirectory.path ||
              resolvedParent.path.hasPrefix(rootDirectory.path + "/") else {
            throw NeaRerTransferFileStreamErrorV1.unsafeDestination
        }
        guard !fileManager.fileExists(atPath: destinationURL.path) else {
            throw NeaRerTransferFileStreamErrorV1.destinationExists
        }
        try fileManager.moveItem(at: stagingURL, to: destinationURL)
        finished = true
        let ack = NeaRerTransferFileAckV1(
            payloadID: metadata.payloadID, transferID: metadata.transferID,
            itemID: metadata.item.id, committedOffsetBytes: bytesWritten,
            prefixSHA256: digest
        )
        return FinalizedFile(url: destinationURL, finalAck: ack)
    }

    public func cancel() {
        if !finished {
            try? handle.close()
            try? fileManager.removeItem(at: stagingURL)
            finished = true
        }
    }

    private func makeAck() -> NeaRerTransferFileAckV1 {
        var checkpointHasher = hasher
        let digest = checkpointHasher.finalize().map { String(format: "%02x", $0) }.joined()
        return NeaRerTransferFileAckV1(
            payloadID: metadata.payloadID, transferID: metadata.transferID,
            itemID: metadata.item.id, committedOffsetBytes: bytesWritten,
            prefixSHA256: digest
        )
    }

    deinit { cancel() }
}
#endif
