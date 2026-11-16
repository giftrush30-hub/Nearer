package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class BusinessDraftCodecTest {
    private fun draft(id: Long, title: String = "Handmade bag") = BusinessDraft(
        id = id,
        title = title,
        price = "₦15,000",
        kind = "PRODUCT",
        itemCategory = "Fashion",
        description = "Locally made",
        imageUri = "content://media/external/images/1",
        availability = "AVAILABLE"
    )

    @Test fun roundTripsVersionedCatalogue() {
        val encoded = BusinessDraftCodec.encode(listOf(draft(1), draft(2, "Tailoring")))
        val decoded = BusinessDraftCodec.decode(encoded)
        assertEquals(2, decoded.size)
        assertEquals("Fashion", decoded.first().itemCategory)
        assertEquals("content://media/external/images/1", decoded.first().imageUri)
    }

    @Test fun readsLegacyUnversionedCatalogue() {
        val legacy = JSONArray().put(
            JSONObject().put("id", 8).put("title", "Shoes").put("category", "PRODUCT")
                .put("price", "₦9,000").put("description", "Leather shoes")
                .put("image", "content://media/external/images/8")
        ).toString()
        val decoded = BusinessDraftCodec.decode(legacy)
        assertEquals(1, decoded.size)
        assertEquals("PRODUCT", decoded.single().kind)
        assertEquals("General", decoded.single().itemCategory)
        assertEquals("AVAILABLE", decoded.single().availability)
    }

    @Test fun clampsFieldsAndRejectsNonContentImageUris() {
        val input = draft(3, "  " + "A".repeat(120)).copy(
            price = "P".repeat(100),
            itemCategory = "C".repeat(80),
            description = "D".repeat(2_000),
            imageUri = "file:///private/photo.jpg",
            kind = "UNSUPPORTED",
            availability = "UNKNOWN"
        )
        val normalized = BusinessDraftCodec.normalize(input)
        assertEquals(BusinessDraftCodec.MAX_TITLE_LENGTH, normalized.title.length)
        assertEquals(BusinessDraftCodec.MAX_PRICE_LENGTH, normalized.price.length)
        assertEquals(BusinessDraftCodec.MAX_CATEGORY_LENGTH, normalized.itemCategory.length)
        assertEquals(BusinessDraftCodec.MAX_DESCRIPTION_LENGTH, normalized.description.length)
        assertEquals("", normalized.imageUri)
        assertEquals("PRODUCT", normalized.kind)
        assertEquals("AVAILABLE", normalized.availability)
    }

    @Test fun skipsMalformedRowsBlankTitlesAndDuplicateIds() {
        val rows = JSONArray()
            .put(JSONObject().put("id", 1).put("title", "First"))
            .put(JSONObject().put("id", 1).put("title", "Duplicate"))
            .put(JSONObject().put("id", 2).put("title", "   "))
            .put("not an object")
            .put(JSONObject().put("id", -1).put("title", "Bad id"))
        val decoded = BusinessDraftCodec.decode(JSONObject().put("version", 2).put("items", rows).toString())
        assertEquals(1, decoded.size)
        assertEquals("First", decoded.single().title)
    }

    @Test fun enforcesMaximumDraftCount() {
        val drafts = (1..(BusinessDraftCodec.MAX_DRAFTS + 20)).map { draft(it.toLong(), "Item $it") }
        val decoded = BusinessDraftCodec.decode(BusinessDraftCodec.encode(drafts))
        assertEquals(BusinessDraftCodec.MAX_DRAFTS, decoded.size)
    }

    @Test fun unknownVersionAndMalformedJsonAreNotMarkedSafeToOverwrite() {
        val unknown = """{"version":999,"items":[]}"""
        assertTrue(!BusinessDraftCodec.canDecode(unknown))
        assertTrue(!BusinessDraftCodec.canDecode("{broken"))
        assertTrue(BusinessDraftCodec.canDecode(null))
        assertTrue(BusinessDraftCodec.decode(unknown).isEmpty())
        assertTrue(BusinessDraftCodec.decode("{broken").isEmpty())
    }
}
