package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class BusinessDraft(
    val id: Long,
    val title: String,
    val price: String,
    /** PRODUCT or SERVICE. */
    val kind: String,
    val itemCategory: String,
    val description: String,
    val imageUri: String,
    /** AVAILABLE, MADE_TO_ORDER or OUT_OF_STOCK. */
    val availability: String
)

internal object BusinessDraftCodec {
    const val VERSION = 2
    const val MAX_DRAFTS = 100
    const val MAX_TITLE_LENGTH = 80
    const val MAX_PRICE_LENGTH = 40
    const val MAX_CATEGORY_LENGTH = 40
    const val MAX_DESCRIPTION_LENGTH = 1_200
    const val MAX_IMAGE_URI_LENGTH = 2_048

    private val validKinds = setOf("PRODUCT", "SERVICE")
    private val validAvailability = setOf("AVAILABLE", "MADE_TO_ORDER", "OUT_OF_STOCK")

    fun normalize(draft: BusinessDraft): BusinessDraft {
        val title = draft.title.trim().take(MAX_TITLE_LENGTH)
        val kind = draft.kind.uppercase(Locale.ROOT).takeIf { it in validKinds } ?: "PRODUCT"
        val image = draft.imageUri.trim().take(MAX_IMAGE_URI_LENGTH)
            .takeIf { it.isEmpty() || it.startsWith("content://", ignoreCase = true) }.orEmpty()
        return BusinessDraft(
            id = draft.id.coerceAtLeast(1L),
            title = title,
            price = draft.price.trim().take(MAX_PRICE_LENGTH),
            kind = kind,
            itemCategory = draft.itemCategory.trim().take(MAX_CATEGORY_LENGTH),
            description = draft.description.trim().take(MAX_DESCRIPTION_LENGTH),
            imageUri = image,
            availability = draft.availability.uppercase(Locale.ROOT).takeIf { it in validAvailability } ?: "AVAILABLE"
        )
    }

    fun encode(drafts: List<BusinessDraft>): String {
        val items = JSONArray()
        val seen = HashSet<Long>()
        drafts.asSequence().take(MAX_DRAFTS).map(::normalize)
            .filter { it.title.isNotBlank() && seen.add(it.id) }
            .forEach { draft ->
                items.put(
                    JSONObject()
                        .put("id", draft.id)
                        .put("title", draft.title)
                        .put("price", draft.price)
                        .put("kind", draft.kind)
                        .put("item_category", draft.itemCategory)
                        .put("description", draft.description)
                        .put("image_uri", draft.imageUri)
                        .put("availability", draft.availability)
                )
            }
        return JSONObject().put("version", VERSION).put("items", items).toString()
    }

    fun canDecode(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return true
        return runCatching {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                if (root.optInt("version", -1) != VERSION || root.optJSONArray("items") == null) return false
            }
            true
        }.getOrDefault(false)
    }

    fun decode(raw: String?): List<BusinessDraft> {
        if (raw.isNullOrBlank() || !canDecode(raw)) return emptyList()
        return runCatching {
            val trimmed = raw.trim()
            val array = if (trimmed.startsWith("[")) {
                // Legacy unversioned catalogue used category for PRODUCT/SERVICE.
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                if (root.optInt("version", -1) != VERSION) return emptyList()
                root.optJSONArray("items") ?: JSONArray()
            }
            val result = ArrayList<BusinessDraft>(minOf(array.length(), MAX_DRAFTS))
            val seen = HashSet<Long>()
            for (index in 0 until minOf(array.length(), MAX_DRAFTS)) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optLong("id", 0L)
                if (id <= 0L || !seen.add(id)) continue
                val title = item.optString("title").trim().take(MAX_TITLE_LENGTH)
                if (title.isBlank()) continue
                val legacyKind = item.optString("category", "PRODUCT")
                val draft = normalize(
                    BusinessDraft(
                        id = id,
                        title = title,
                        price = item.optString("price"),
                        kind = item.optString("kind", legacyKind),
                        itemCategory = item.optString("item_category", "General"),
                        description = item.optString("description"),
                        imageUri = item.optString("image_uri", item.optString("image")),
                        availability = item.optString("availability", "AVAILABLE")
                    )
                )
                result += draft
            }
            result
        }.getOrDefault(emptyList())
    }
}
