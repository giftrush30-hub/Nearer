package com.nearer.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A metadata-only warm index for the Share grid.
 *
 * File contents are never copied into this cache. Contact rows and SAF rows are
 * excluded because their visibility depends on separate, revocable grants.
 */
internal object FileCentreIndexCache {
    private const val FILE_NAME = "nearer_file_catalog_index.json"
    private const val MAX_ENTRIES = 20000
    private val cachedCategories = setOf(
        FileCentreCategory.APPS,
        FileCentreCategory.PICTURES,
        FileCentreCategory.VIDEOS,
        FileCentreCategory.MUSIC
    )

    fun read(context: Context): List<FileCentreItem> = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile || file.length() <= 0L || file.length() > 16L * 1024L * 1024L) {
            return@runCatching emptyList()
        }
        val array = JSONArray(file.readText(Charsets.UTF_8))
        buildList {
            val count = minOf(array.length(), MAX_ENTRIES)
            for (index in 0 until count) {
                val item = array.optJSONObject(index) ?: continue
                val category = runCatching {
                    FileCentreCategory.valueOf(item.optString("category"))
                }.getOrNull() ?: continue
                if (category !in cachedCategories) continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: "Unnamed file"
                val rawUri = item.optString("uri").takeIf { it.isNotBlank() }
                add(
                    FileCentreItem(
                        id = id,
                        name = name,
                        category = category,
                        uri = rawUri?.let(Uri::parse),
                        sizeBytes = item.optLong("sizeBytes", 0L).coerceAtLeast(0L),
                        modifiedSeconds = item.optLong("modifiedSeconds", 0L).coerceAtLeast(0L),
                        mimeType = item.optString("mimeType").takeIf { it.isNotBlank() },
                        packageName = item.optString("packageName").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun write(context: Context, items: List<FileCentreItem>) {
        runCatching {
            val array = JSONArray()
            items.asSequence()
                .filter { it.category in cachedCategories }
                .sortedWith(compareByDescending<FileCentreItem> { it.category == FileCentreCategory.APPS }.thenByDescending { it.modifiedSeconds })
                .take(MAX_ENTRIES)
                .forEach { item ->
                    array.put(JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("category", item.category.name)
                        put("uri", item.uri?.toString())
                        put("sizeBytes", item.sizeBytes.coerceAtLeast(0L))
                        put("modifiedSeconds", item.modifiedSeconds.coerceAtLeast(0L))
                        put("mimeType", item.mimeType)
                        put("packageName", item.packageName)
                    })
                }
            val target = File(context.filesDir, FILE_NAME)
            val temporary = File(context.filesDir, "$FILE_NAME.tmp")
            temporary.writeText(array.toString(), Charsets.UTF_8)
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
                temporary.delete()
            }
        }
    }
}
