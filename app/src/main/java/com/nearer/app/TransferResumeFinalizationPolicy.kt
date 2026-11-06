package com.nearer.app

/**
 * Final integrity gate for a receiver-owned resumed partial.
 *
 * A stream writer finishing at the declared byte count is not sufficient to
 * publish a file: the sender-provided full-file digest must also match. This
 * pure policy is deliberately separate from filesystem promotion so callers
 * cannot accidentally confuse a completed stream with a verified file.
 */
internal object TransferResumeFinalizationPolicy {
    enum class Rejection {
        NOT_A_FILE,
        SIZE_MISMATCH,
        MISSING_EXPECTED_DIGEST,
        MALFORMED_EXPECTED_DIGEST,
        MALFORMED_ACTUAL_DIGEST,
        DIGEST_MISMATCH
    }

    data class Decision(
        val accepted: Boolean,
        val rejection: Rejection? = null
    )

    fun evaluate(
        item: TransferBundleItem,
        bytesWritten: Long,
        actualSha256: String,
        expectedSha256: String?
    ): Decision {
        if (item.kind != TransferBundleItem.Kind.FILE) {
            return Decision(false, Rejection.NOT_A_FILE)
        }
        if (bytesWritten != item.sizeBytes || bytesWritten < 0L) {
            return Decision(false, Rejection.SIZE_MISMATCH)
        }
        if (expectedSha256.isNullOrBlank()) {
            return Decision(false, Rejection.MISSING_EXPECTED_DIGEST)
        }
        if (!SHA256.matches(expectedSha256)) {
            return Decision(false, Rejection.MALFORMED_EXPECTED_DIGEST)
        }
        if (!SHA256.matches(actualSha256)) {
            return Decision(false, Rejection.MALFORMED_ACTUAL_DIGEST)
        }
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            return Decision(false, Rejection.DIGEST_MISMATCH)
        }
        return Decision(true)
    }

    private val SHA256 = Regex("^[a-fA-F0-9]{64}$")
}
