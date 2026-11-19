import Foundation
import NeaRerWire
import NeaRerTransport

/// iOS-side transfer router. The caller must invoke approveIncomingTransfer only
/// after the user accepts the incoming file request; until then no initial ACK
/// is sent and the sender remains blocked before transmitting file bytes.
@MainActor
final class NeaRerFileTransferCoordinatorV1 {
    typealias IncomingRequestHandler = (String, NeaRerTransferStreamMetadataV1) -> Void
    typealias FileReceivedHandler = (String, URL) -> Void
    typealias ErrorHandler = (String, String) -> Void

    private struct TransferKey: Hashable {
        let endpointID: String
        let payloadID: UInt64
    }
    private struct AckKey: Hashable {
        let endpointID: String
        let payloadID: UInt64
        let transferID: String
        let itemID: String
    }
    private struct PendingAck {
        let expected: NeaRerTransferFileAckV1
        let completion: (Result<NeaRerTransferFileAckV1, Error>) -> Void
        let timeout: DispatchWorkItem
    }

    private let transport: NetworkFrameworkPeerTransport
    private let destinationDirectory: URL
    private var authenticatedEndpoints = Set<String>()
    private var pendingMetadata: [TransferKey: NeaRerTransferStreamMetadataV1] = [:]
    private var receivers: [TransferKey: NeaRerTransferFileStreamReceiverV1] = [:]
    private var pendingAcks: [AckKey: PendingAck] = [:]
    private var queuedAcks: [AckKey: [NeaRerTransferFileAckV1]] = [:]

    var onIncomingRequest: IncomingRequestHandler?
    var onFileReceived: FileReceivedHandler?
    var onError: ErrorHandler?

    init(transport: NetworkFrameworkPeerTransport, destinationDirectory: URL) {
        self.transport = transport
        self.destinationDirectory = destinationDirectory
    }

    func setPeerAuthenticated(_ authenticated: Bool, endpointID: String) {
        if authenticated { authenticatedEndpoints.insert(endpointID) }
        else {
            authenticatedEndpoints.remove(endpointID)
            peerDisconnected(endpointID)
        }
        transport.setPeerAuthenticated(authenticated, endpointID: endpointID)
    }

    /// Handles a decoded transport payload. Returns false for non-file protocol frames.
    @discardableResult
    func receiveFrame(_ frame: Data, from endpointID: String) -> Bool {
        guard authenticatedEndpoints.contains(endpointID), frame.count >= 4 else { return false }
        let magic = (UInt32(frame[0]) << 24) | (UInt32(frame[1]) << 16) |
                    (UInt32(frame[2]) << 8) | UInt32(frame[3])
        do {
            switch magic {
            case 0x4E52534D: return try receiveMetadata(frame, endpointID: endpointID)
            case 0x4E524643: return try receiveChunk(frame, endpointID: endpointID)
            case 0x4E524645: return try receiveEnd(frame, endpointID: endpointID)
            case 0x4E524641: return try receiveAck(frame, endpointID: endpointID)
            default: return false
            }
        } catch {
            onError?(endpointID, error.localizedDescription)
            return false
        }
    }

    func approveIncomingTransfer(endpointID: String, payloadID: UInt64) throws {
        let key = TransferKey(endpointID: endpointID, payloadID: payloadID)
        guard authenticatedEndpoints.contains(endpointID),
              let metadata = pendingMetadata.removeValue(forKey: key) else {
            throw NeaRerTransferFileStreamErrorV1.wrongTransfer
        }
        let receiver = try NeaRerTransferFileStreamReceiverV1(
            metadata: metadata, destinationDirectory: destinationDirectory
        )
        receivers[key] = receiver
        transport.send(try NeaRerTransferFileChunkCodecV1.encodeAck(receiver.initialAck()), to: endpointID)
    }

    func rejectIncomingTransfer(endpointID: String, payloadID: UInt64) {
        pendingMetadata.removeValue(forKey: TransferKey(endpointID: endpointID, payloadID: payloadID))
    }

    func sendFile(to endpointID: String, fileURL: URL,
                  metadata: NeaRerTransferStreamMetadataV1,
                  completion: @escaping (Result<NeaRerTransferFileEndV1, Error>) -> Void) {
        guard authenticatedEndpoints.contains(endpointID) else {
            completion(.failure(NearbyTransportSecurityError.peerNotAuthenticated))
            return
        }
        transport.sendFileStream(
            to: endpointID, fileURL: fileURL, metadata: metadata,
            awaitAck: { [weak self] expected, callback in
                Task { @MainActor in
                    guard let self else {
                        callback(.failure(NearbyTransportSecurityError.connectionUnavailable))
                        return
                    }
                    self.awaitAck(endpointID: endpointID, expected: expected, completion: callback)
                }
            },
            completion: { result in
                Task { @MainActor in completion(result) }
            }
        )
    }

    func peerDisconnected(_ endpointID: String) {
        pendingMetadata.keys.filter { $0.endpointID == endpointID }.forEach {
            pendingMetadata.removeValue(forKey: $0)
        }
        receivers.keys.filter { $0.endpointID == endpointID }.forEach {
            receivers.removeValue(forKey: $0)?.cancel()
        }
        let keys = pendingAcks.keys.filter { $0.endpointID == endpointID }
        for key in keys {
            guard let pending = pendingAcks.removeValue(forKey: key) else { continue }
            pending.timeout.cancel()
            pending.completion(.failure(NearbyTransportSecurityError.connectionUnavailable))
        }
        for key in queuedAcks.keys.filter({ $0.endpointID == endpointID }) { queuedAcks.removeValue(forKey: key) }
    }

    func close() {
        for receiver in receivers.values { receiver.cancel() }
        receivers.removeAll()
        pendingMetadata.removeAll()
        for pending in pendingAcks.values {
            pending.timeout.cancel()
            pending.completion(.failure(NearbyTransportSecurityError.connectionUnavailable))
        }
        pendingAcks.removeAll()
        queuedAcks.removeAll()
        for endpoint in authenticatedEndpoints {
            transport.setPeerAuthenticated(false, endpointID: endpoint)
        }
        authenticatedEndpoints.removeAll()
    }

    private func receiveMetadata(_ data: Data, endpointID: String) throws -> Bool {
        let metadata = try NeaRerTransferStreamMetadataCodecV1.decode(data)
        let key = TransferKey(endpointID: endpointID, payloadID: metadata.payloadID)
        guard pendingMetadata[key] == nil, receivers[key] == nil else {
            throw NeaRerTransferFileStreamErrorV1.wrongTransfer
        }
        pendingMetadata[key] = metadata
        onIncomingRequest?(endpointID, metadata)
        return true
    }

    private func receiveChunk(_ data: Data, endpointID: String) throws -> Bool {
        let chunk = try NeaRerTransferFileChunkCodecV1.decodeChunk(data)
        let key = TransferKey(endpointID: endpointID, payloadID: chunk.payloadID)
        guard let receiver = receivers[key] else {
            throw NeaRerTransferFileStreamErrorV1.wrongTransfer
        }
        if let ack = try receiver.append(chunk) {
            transport.send(try NeaRerTransferFileChunkCodecV1.encodeAck(ack), to: endpointID)
        }
        return true
    }

    private func receiveEnd(_ data: Data, endpointID: String) throws -> Bool {
        let end = try NeaRerTransferFileChunkCodecV1.decodeEnd(data)
        let key = TransferKey(endpointID: endpointID, payloadID: end.payloadID)
        guard let receiver = receivers[key] else {
            throw NeaRerTransferFileStreamErrorV1.wrongTransfer
        }
        let finalized = try receiver.finish(end)
        receivers.removeValue(forKey: key)
        transport.send(try NeaRerTransferFileChunkCodecV1.encodeAck(finalized.finalAck), to: endpointID)
        onFileReceived?(endpointID, finalized.url)
        return true
    }

    private func receiveAck(_ data: Data, endpointID: String) throws -> Bool {
        let ack = try NeaRerTransferFileChunkCodecV1.decodeAck(data)
        let key = AckKey(endpointID: endpointID, payloadID: ack.payloadID,
                         transferID: ack.transferID, itemID: ack.itemID)
        guard let pending = pendingAcks.removeValue(forKey: key) else {
            var queue = queuedAcks[key, default: []]
            if queue.count >= 8 { queue.removeFirst() }
            queue.append(ack)
            queuedAcks[key] = queue
            return true
        }
        pending.timeout.cancel()
        if ack == pending.expected { pending.completion(.success(ack)) }
        else { pending.completion(.failure(NeaRerTransferFileStreamErrorV1.invalidAcknowledgement)) }
        return true
    }

    private func awaitAck(endpointID: String, expected: NeaRerTransferFileAckV1,
                          completion: @escaping (Result<NeaRerTransferFileAckV1, Error>) -> Void) {
        let key = AckKey(endpointID: endpointID, payloadID: expected.payloadID,
                         transferID: expected.transferID, itemID: expected.itemID)
        if var queue = queuedAcks[key], !queue.isEmpty {
            let received = queue.removeFirst()
            queuedAcks[key] = queue.isEmpty ? nil : queue
            if received == expected { completion(.success(received)) }
            else { completion(.failure(NeaRerTransferFileStreamErrorV1.invalidAcknowledgement)) }
            return
        }
        guard pendingAcks[key] == nil else {
            completion(.failure(NeaRerTransferFileStreamErrorV1.invalidAcknowledgement))
            return
        }
        let timeout = DispatchWorkItem { [weak self] in
            Task { @MainActor in
                guard let pending = self?.pendingAcks.removeValue(forKey: key),
                      pending.expected == expected else { return }
                pending.completion(.failure(NearbyTransportSecurityError.connectionUnavailable))
            }
        }
        pendingAcks[key] = PendingAck(expected: expected, completion: completion, timeout: timeout)
        DispatchQueue.main.asyncAfter(deadline: .now() + 30, execute: timeout)
    }
}
