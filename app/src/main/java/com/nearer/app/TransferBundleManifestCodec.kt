package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded JSON wire codec for the metadata header of a bundle transfer.
 * File contents are never embedded in this message.
 */
internal object TransferBundleManifestCodec {
    const val MAX_ENCODED_BYTES = 1_048_576

    fun encode(manifest: TransferBundleManifest): ByteArray {
        val errors = manifest.validationErrors()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        val items = JSONArray()
        manifest.items.forEach { item ->
            val json = JSONObject()
                .put("id", item.id)
                .put("path", item.relativePath)
                .put("name", item.displayName)
                .put("size", item.sizeBytes)
                .put("kind", item.kind.name)
            if (item.mimeType == null) json.put("mime", JSONObject.NULL) else json.put("mime", item.mimeType)
            items.put(json)
        }
        val bytes = JSONObject()
            .put("version", manifest.protocolVersion)
            .put("transferId", manifest.transferId)
            .put("items", items)
            .toString()
            .toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_ENCODED_BYTES) { "Transfer manifest is too large" }
        return bytes
    }

    fun decode(bytes: ByteArray): TransferBundleManifest {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ENCODED_BYTES) {
            "Transfer manifest size is invalid"
        }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val array = root.getJSONArray("items")
        require(array.length() in 1..TransferBundleManifest.MAX_ITEMS) {
            "Transfer bundle item count is invalid"
        }
        val items = ArrayList<TransferBundleItem>(array.length())
        for (index in 0 until array.length()) {
            val json = array.getJSONObject(index)
            val kind = TransferBundleItem.Kind.valueOf(json.getString("kind"))
            val mimeValue = json.opt("mime")
            val mime = if (mimeValue == null || mimeValue === JSONObject.NULL) null else mimeValue as? String
                ?: throw IllegalArgumentException("Invalid MIME metadata")
            items += TransferBundleItem(
                id = json.getString("id"),
                relativePath = json.getString("path"),
                displayName = json.getString("name"),
                sizeBytes = json.getLong("size"),
                mimeType = mime,
                kind = kind
            )
        }
        val manifest = TransferBundleManifest(
            transferId = root.getString("transferId"),
            items = items,
            protocolVersion = root.getInt("version")
        )
        val errors = manifest.validationErrors()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return manifest
    }
}
