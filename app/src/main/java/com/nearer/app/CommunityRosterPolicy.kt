package com.nearer.app

/**
 * Identity checks for local community rosters.
 *
 * The first pinned peer that establishes a roster becomes its coordinator (TOFU);
 * later roster updates must come from that same stable identity. Display names are
 * presentation only and never authorize message delivery.
 */
internal object CommunityRosterPolicy {
    fun resolveOwner(existingOwnerPeerId: String, senderPeerId: String): String? {
        val sender = NeaRerIdentity.normalize(senderPeerId) ?: return null
        if (sender != senderPeerId) return null
        if (existingOwnerPeerId.isBlank()) return sender
        val existing = NeaRerIdentity.normalize(existingOwnerPeerId) ?: return null
        if (existing != existingOwnerPeerId) return null
        return sender.takeIf { existing == sender }
    }

    fun validateRoster(
        ownerPeerId: String,
        senderPeerId: String,
        memberPeerIds: List<String>,
        localPeerId: String
    ): List<String>? {
        val owner = NeaRerIdentity.normalize(ownerPeerId) ?: return null
        val sender = NeaRerIdentity.normalize(senderPeerId) ?: return null
        val local = NeaRerIdentity.normalize(localPeerId) ?: return null
        if (owner != sender || memberPeerIds.isEmpty() || memberPeerIds.size > 100) return null

        val normalized = memberPeerIds.map { peerId ->
            val clean = NeaRerIdentity.normalize(peerId) ?: return null
            if (clean != peerId) return null
            clean
        }.distinct()
        if (owner !in normalized || local !in normalized) return null
        return normalized
    }

    fun mayAcceptGroupMessage(senderPeerId: String, memberPeerIds: List<String>): Boolean {
        val sender = NeaRerIdentity.normalize(senderPeerId) ?: return false
        return sender == senderPeerId && sender in memberPeerIds
    }
}
