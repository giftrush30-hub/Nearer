#if canImport(Network)
import Foundation
import Network
import NeaRerWire

public enum NearbyTransportSecurityError: Error { case connectionUnavailable, peerNotAuthenticated }

/// TCP/Bonjour adapter for local Wi-Fi and Apple's iPhone-to-iPhone peer-to-peer Wi-Fi.
/// Android interoperation requires the separately specified Android TCP/Bonjour adapter.
public final class NetworkFrameworkPeerTransport: NearbyPeerTransport, @unchecked Sendable {
    public var onPeerFound: ((NearbyTransportPeer) -> Void)?
    public var onPeerLost: ((NearbyTransportPeer) -> Void)?
    public var onStateChanged: ((NearbyTransportState) -> Void)?
    public var onFrameReceived: ((String, Data) -> Void)?

    public static let serviceType = "_nearer-v1._tcp"
    private let peerID: String
    private let queue = DispatchQueue(label: "com.nearer.transport.network", qos: .userInitiated)
    private var listener: NWListener?
    private var browser: NWBrowser?
    private var discovered: [String: NWEndpoint] = [:]
    private var connections: [String: NWConnection] = [:]
    private var authenticatedEndpoints = Set<String>()
    private var decoders: [String: TransportFrameDecoder] = [:]

    public init(peerID: String) throws {
        guard NeaRerIdentity.normalize(peerID) == peerID else {
            throw TransportFrameError.emptyFrame
        }
        self.peerID = peerID
    }

    public func start() throws {
        queue.async { [weak self] in self?.startOnQueue() }
    }

    private func startOnQueue() {
        guard listener == nil, browser == nil else { return }
        do {
            let parameters = NWParameters.tcp
            // On Apple platforms this enables Apple's peer-to-peer Wi-Fi route.
            // That proprietary radio route is Apple-only; LAN remains interoperable.
            parameters.includePeerToPeer = true
            let newListener = try NWListener(using: parameters)
            newListener.service = NWListener.Service(name: peerID, type: Self.serviceType)
            newListener.newConnectionHandler = { [weak self] connection in
                self?.accept(connection)
            }
            newListener.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready: self?.onStateChanged?(.advertising)
                case .failed(let error): self?.onStateChanged?(.failed("Listener: \(error)"))
                case .cancelled: break
                default: break
                }
            }
            listener = newListener
            newListener.start(queue: queue)

            let browserParameters = NWParameters.tcp
            browserParameters.includePeerToPeer = true
            let newBrowser = NWBrowser(
                for: .bonjour(type: Self.serviceType, domain: nil),
                using: browserParameters
            )
            newBrowser.browseResultsChangedHandler = { [weak self] results, _ in
                guard let self else { return }
                let current = Set(results.map { String(describing: $0.endpoint) })
                let previous = Set(self.discovered.keys)
                for result in results {
                    if case let .service(name, _, _, _) = result.endpoint, name == self.peerID { continue }
                    let key = String(describing: result.endpoint)
                    self.discovered[key] = result.endpoint
                    self.onPeerFound?(NearbyTransportPeer(endpointID: key, transport: "network-framework"))
                }
                for key in previous.subtracting(current) {
                    self.discovered.removeValue(forKey: key)
                    self.onPeerLost?(NearbyTransportPeer(endpointID: key, transport: "network-framework"))
                }
            }
            newBrowser.stateUpdateHandler = { [weak self] state in
                if case .failed(let error) = state {
                    self?.onStateChanged?(.failed("Browser: \(error)"))
                }
            }
            browser = newBrowser
            newBrowser.start(queue: queue)
            onStateChanged?(.browsing)
        } catch {
            onStateChanged?(.failed("Could not start local peer networking: \(error)"))
        }
    }

    public func connect(to endpointID: String) {
        queue.async { [weak self] in
            guard let self, let endpoint = self.discovered[endpointID] else { return }
            self.open(NWConnection(to: endpoint, using: self.makeParameters()), endpointID: endpointID)
        }
    }

    public func send(_ frame: Data, to endpointID: String) {
        queue.async { [weak self] in
            guard let self, let connection = self.connections[endpointID] else { return }
            do {
                let packet = try TransportFrameCodec.encode(frame)
                connection.send(content: packet, completion: .contentProcessed { [weak self] error in
                    if let error { self?.onStateChanged?(.failed("Send failed: \(error)")) }
                })
            } catch {
                self.onStateChanged?(.failed("Frame rejected: \(error)"))
            }
        }
    }

    /// Called only after the v3 proof, signed recipient binding and explicit trust decision succeed.
    public func setPeerAuthenticated(_ authenticated: Bool, endpointID: String) {
        queue.async { [weak self] in
            guard let self else { return }
            if authenticated { self.authenticatedEndpoints.insert(endpointID) }
            else { self.authenticatedEndpoints.remove(endpointID) }
        }
    }

    /// Streams one bounded chunk at a time and waits for receiver approval,
    /// durable checkpoints and the final whole-file verification ACK.
    public func sendFileStream(to endpointID: String, fileURL: URL,
                               metadata: NeaRerTransferStreamMetadataV1,
                               awaitAck: @escaping NeaRerTransferAckWaiterV1,
                               completion: @escaping (Result<NeaRerTransferFileEndV1, Error>) -> Void) {
        queue.async { [weak self] in
            guard let self, let connection = self.connections[endpointID] else {
                completion(.failure(NearbyTransportSecurityError.connectionUnavailable))
                return
            }
            guard self.authenticatedEndpoints.contains(endpointID) else {
                completion(.failure(NearbyTransportSecurityError.peerNotAuthenticated))
                return
            }
            do {
                let writer = try NeaRerTransferFileStreamWriterV1(fileURL: fileURL, metadata: metadata)
                let metadataBytes = try NeaRerTransferStreamMetadataCodecV1.encode(metadata)
                let frame = try TransportFrameCodec.encode(metadataBytes)
                connection.send(content: frame, completion: .contentProcessed { [weak self] error in
                    guard let self else { writer.close(); return }
                    if let error {
                        writer.close()
                        completion(.failure(error))
                    } else {
                        self.waitForTransferAck(NeaRerTransferFileStreamWriterV1.initialAck(for: metadata),
                                                awaitAck: awaitAck) { result in
                            switch result {
                            case .success:
                                self.sendNextFileChunk(writer, connection: connection,
                                                       awaitAck: awaitAck, completion: completion)
                            case .failure(let error):
                                writer.close()
                                completion(.failure(error))
                            }
                        }
                    }
                })
            } catch {
                completion(.failure(error))
            }
        }
    }

    private func waitForTransferAck(
        _ expected: NeaRerTransferFileAckV1,
        awaitAck: @escaping NeaRerTransferAckWaiterV1,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        awaitAck(expected) { result in
            switch result {
            case .success(let received) where received == expected:
                completion(.success(()))
            case .success:
                completion(.failure(NeaRerTransferFileStreamErrorV1.invalidAcknowledgement))
            case .failure(let error):
                completion(.failure(error))
            }
        }
    }

    private func sendNextFileChunk(
        _ writer: NeaRerTransferFileStreamWriterV1,
        connection: NWConnection,
        awaitAck: @escaping NeaRerTransferAckWaiterV1,
        completion: @escaping (Result<NeaRerTransferFileEndV1, Error>) -> Void
    ) {
        do {
            if let chunk = try writer.nextChunk() {
                let chunkBytes = try NeaRerTransferFileChunkCodecV1.encodeChunk(chunk)
                let frame = try TransportFrameCodec.encode(chunkBytes)
                connection.send(content: frame, completion: .contentProcessed { [weak self] error in
                    guard let self else { writer.close(); return }
                    if let error {
                        writer.close()
                        self.onStateChanged?(.failed("File chunk send failed: \(error)"))
                        completion(.failure(error))
                    } else if writer.bytesRead < writer.metadata.item.sizeBytes &&
                                writer.bytesRead % NeaRerTransferFileStreamReceiverV1.checkpointIntervalBytes == 0 {
                        self.waitForTransferAck(writer.currentAck(), awaitAck: awaitAck) { result in
                            switch result {
                            case .success:
                                self.sendNextFileChunk(writer, connection: connection,
                                                       awaitAck: awaitAck, completion: completion)
                            case .failure(let error):
                                writer.close()
                                completion(.failure(error))
                            }
                        }
                    } else {
                        self.sendNextFileChunk(writer, connection: connection,
                                               awaitAck: awaitAck, completion: completion)
                    }
                })
            } else {
                let end = try writer.finish()
                let endBytes = try NeaRerTransferFileChunkCodecV1.encodeEnd(end)
                let frame = try TransportFrameCodec.encode(endBytes)
                connection.send(content: frame, completion: .contentProcessed { [weak self] error in
                    if let error {
                        writer.close()
                        self?.onStateChanged?(.failed("File end record send failed: \(error)"))
                        completion(.failure(error))
                    } else {
                        let finalAck = NeaRerTransferFileAckV1(
                            payloadID: end.payloadID, transferID: end.transferID,
                            itemID: end.itemID, committedOffsetBytes: end.totalSizeBytes,
                            prefixSHA256: end.wholeFileSHA256
                        )
                        self?.waitForTransferAck(finalAck, awaitAck: awaitAck) { result in
                            switch result {
                            case .success: completion(.success(end))
                            case .failure(let error): completion(.failure(error))
                            }
                        }
                    }
                })
            }
        } catch {
            writer.close()
            completion(.failure(error))
        }
    }

    public func disconnect(from endpointID: String) {
        queue.async { [weak self] in self?.removeConnection(endpointID, cancel: true) }
    }

    public func stop() {
        queue.async { [weak self] in
            guard let self else { return }
            self.browser?.cancel()
            self.listener?.cancel()
            self.browser = nil
            self.listener = nil
            for key in Array(self.connections.keys) { self.removeConnection(key, cancel: true) }
            self.discovered.removeAll()
            self.authenticatedEndpoints.removeAll()
            self.onStateChanged?(.idle)
        }
    }

    private func makeParameters() -> NWParameters {
        let parameters = NWParameters.tcp
        parameters.includePeerToPeer = true
        return parameters
    }

    private func accept(_ connection: NWConnection) {
        let endpointID = "inbound-" + UUID().uuidString
        open(connection, endpointID: endpointID)
    }

    private func open(_ connection: NWConnection, endpointID: String) {
        removeConnection(endpointID, cancel: true)
        connections[endpointID] = connection
        decoders[endpointID] = TransportFrameDecoder()
        connection.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            switch state {
            case .ready:
                self.onStateChanged?(.connected(endpointID))
                self.receiveNext(from: connection, endpointID: endpointID)
            case .failed(let error):
                self.onStateChanged?(.failed("Connection failed: \(error)"))
                self.removeConnection(endpointID, cancel: false)
            case .cancelled:
                self.removeConnection(endpointID, cancel: false)
            default: break
            }
        }
        connection.start(queue: queue)
    }

    private func receiveNext(from connection: NWConnection, endpointID: String) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, complete, error in
            guard let self else { return }
            if let data, !data.isEmpty {
                do {
                    var decoder = self.decoders[endpointID] ?? TransportFrameDecoder()
                    let frames = try decoder.append(data)
                    self.decoders[endpointID] = decoder
                    for frame in frames { self.onFrameReceived?(endpointID, frame) }
                } catch {
                    self.onStateChanged?(.failed("Malformed peer frame: \(error)"))
                    self.removeConnection(endpointID, cancel: true)
                    return
                }
            }
            if error != nil || complete {
                self.removeConnection(endpointID, cancel: true)
            } else {
                self.receiveNext(from: connection, endpointID: endpointID)
            }
        }
    }

    private func removeConnection(_ endpointID: String, cancel: Bool) {
        authenticatedEndpoints.remove(endpointID)
        if let connection = connections.removeValue(forKey: endpointID), cancel {
            connection.cancel()
        }
        decoders.removeValue(forKey: endpointID)
    }
}
#else
import Foundation
import NeaRerWire

/// The transport is implemented with Apple's Network framework and is unavailable on Linux.
public final class NetworkFrameworkPeerTransport: NearbyPeerTransport {
    public var onPeerFound: ((NearbyTransportPeer) -> Void)?
    public var onPeerLost: ((NearbyTransportPeer) -> Void)?
    public var onStateChanged: ((NearbyTransportState) -> Void)?
    public var onFrameReceived: ((String, Data) -> Void)?
    public init(peerID: String) throws {}
    public func start() throws { onStateChanged?(.failed("Apple Network framework unavailable on this platform")) }
    public func connect(to endpointID: String) {}
    public func send(_ frame: Data, to endpointID: String) {}
    public func setPeerAuthenticated(_ authenticated: Bool, endpointID: String) {}
    public func sendFileStream(to endpointID: String, fileURL: URL,
                               metadata: NeaRerTransferStreamMetadataV1,
                               awaitAck: @escaping NeaRerTransferAckWaiterV1,
                               completion: @escaping (Result<NeaRerTransferFileEndV1, Error>) -> Void) {
        completion(.failure(TransportFrameError.emptyFrame))
    }
    public func disconnect(from endpointID: String) {}
    public func stop() { onStateChanged?(.idle) }
}
#endif
