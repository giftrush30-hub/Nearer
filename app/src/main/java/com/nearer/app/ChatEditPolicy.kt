package com.nearer.app

/** Versioned edits authored by the original sender of a chat message. */
internal object ChatEditPolicy {
    const val MAX_BODY_LENGTH = 3500
    const val MAX_REVISION = 10000

    data class Revision(
        val version: Int,
        val body: String,
        val editedAt: Long
    )

    enum class ApplyResult { APPLIED, ALREADY_APPLIED, STALE, REJECTED }

    fun create(currentBody: String, currentVersion: Int, newBody: String, now: Long): Revision? {
        val body = newBody.trim()
        if (body.isEmpty() || body.length > MAX_BODY_LENGTH ||
            currentVersion < 0 || currentVersion >= MAX_REVISION ||
            body == currentBody || now <= 0L) return null
        return Revision(currentVersion + 1, body, now)
    }

    fun apply(
        currentBody: String,
        currentVersion: Int,
        currentEditedAt: Long,
        incoming: Revision
    ): ApplyResult {
        if (incoming.version <= 0 || incoming.version > MAX_REVISION ||
            incoming.body.isBlank() || incoming.body.length > MAX_BODY_LENGTH ||
            incoming.body != incoming.body.trim() || incoming.editedAt <= 0L ||
            currentVersion < 0) return ApplyResult.REJECTED
        if (incoming.version < currentVersion) return ApplyResult.STALE
        if (incoming.version == currentVersion) {
            return if (incoming.body == currentBody && incoming.editedAt == currentEditedAt)
                ApplyResult.ALREADY_APPLIED else ApplyResult.REJECTED
        }
        if (incoming.version != currentVersion + 1) return ApplyResult.REJECTED
        return ApplyResult.APPLIED
    }
}
