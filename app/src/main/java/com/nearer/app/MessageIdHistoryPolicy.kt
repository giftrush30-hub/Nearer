package com.nearer.app

/** Maintains a bounded insertion-ordered set of recently handled message IDs. */
internal object MessageIdHistoryPolicy {
    const val MAX_IDS = 5000

    fun trimOldest(ids: MutableSet<String>, limit: Int = MAX_IDS) {
        require(limit >= 0) { "Message ID history limit cannot be negative" }
        while (ids.size > limit) {
            val oldest = ids.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
    }
}
