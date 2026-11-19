import XCTest
@testable import NeaRerWire

final class TransferResumeCodecV1Tests: XCTestCase {
    private let digest = String(repeating: "0", count: 64)

    func testAndroidProposalGoldenVectorAndRoundTrip() throws {
        let proposal = NeaRerResumeProposalV1(
            transferID: "x", itemID: "i", relativePath: "a",
            expectedSizeBytes: 8_388_608, sourceModifiedAtMilliseconds: nil,
            committedOffsetBytes: 4 * 1024 * 1024, prefixSHA256: digest
        )
        let encoded = try NeaRerTransferResumeCodecV1.encodeProposal(payloadID: 7, proposal: proposal)
        let expected = "4e52525301000000000000000700017800016900016100000000008000000000000000004000000040" + String(repeating: "30", count: 64)
        XCTAssertEqual(encoded.map { String(format: "%02x", $0) }.joined(), expected)
        let decoded = try NeaRerTransferResumeCodecV1.decodeProposal(encoded)
        XCTAssertEqual(decoded.payloadID, 7)
        XCTAssertEqual(decoded.proposal, proposal)
    }

    func testDecisionAndNoResumeGoldenVectors() throws {
        let decision = NeaRerResumeDecisionV1(
            acceptedOffsetBytes: 4 * 1024 * 1024, reason: .resumeAccepted
        )
        let bytes = try NeaRerTransferResumeCodecV1.encodeDecision(payloadID: 7, decision: decision)
        let expected = "4e52524401000000000000000700000000004000000f524553554d455f4143434550544544"
        XCTAssertEqual(bytes.map { String(format: "%02x", $0) }.joined(), expected)
        XCTAssertEqual(try NeaRerTransferResumeCodecV1.decodeDecision(bytes).decision, decision)

        let noResume = NeaRerTransferResumeCodecV1.encodeNoResume(payloadID: 7)
        XCTAssertEqual(noResume.map { String(format: "%02x", $0) }.joined(),
                       "4e52524e010000000000000007")
        XCTAssertEqual(try NeaRerTransferResumeCodecV1.decodeNoResume(noResume), 7)
    }

    func testRejectsUnalignedResumeOffsetAndTrailingBytes() throws {
        let invalid = NeaRerResumeProposalV1(
            transferID: "x", itemID: "i", relativePath: "a",
            expectedSizeBytes: 10_000_000, sourceModifiedAtMilliseconds: nil,
            committedOffsetBytes: 123, prefixSHA256: digest
        )
        XCTAssertThrowsError(try NeaRerTransferResumeCodecV1.encodeProposal(payloadID: 1, proposal: invalid))
        let valid = NeaRerResumeProposalV1(
            transferID: "x", itemID: "i", relativePath: "a",
            expectedSizeBytes: 10_000_000, sourceModifiedAtMilliseconds: nil,
            committedOffsetBytes: 4 * 1024 * 1024, prefixSHA256: digest
        )
        var bytes = try NeaRerTransferResumeCodecV1.encodeProposal(payloadID: 1, proposal: valid)
        bytes.append(0)
        XCTAssertThrowsError(try NeaRerTransferResumeCodecV1.decodeProposal(bytes))
    }
}
