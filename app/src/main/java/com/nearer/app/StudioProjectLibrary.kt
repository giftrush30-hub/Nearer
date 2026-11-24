package com.nearer.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** A named local movie project. payloadJson contains the versioned editor state, not media bytes. */
internal data class StudioProjectRecord(
    val id: String,
    val title: String,
    val kind: String,
    val updatedAtMs: Long,
    val payloadJson: String,
    val archived: Boolean = false
)

internal data class StudioProjectLibrary(val projects: List<StudioProjectRecord>)

/**
 * Versioned project catalogue codec. A bad catalogue or bad individual record must never crash Studio.
 * Media files are deliberately not copied into this catalogue; projects keep references to source media.
 */
internal object StudioProjectLibraryCodec {
    private const val VERSION = 1
    private const val MAX_PROJECTS = 2_000
    private const val MAX_TITLE_LENGTH = 80
    private const val MAX_ID_LENGTH = 128

    fun encode(library: StudioProjectLibrary): String {
        val items = JSONArray()
        library.projects
            .asSequence()
            .filter { isValid(it) }
            .distinctBy { it.id }
            .take(MAX_PROJECTS)
            .forEach { project ->
                items.put(
                    JSONObject()
                        .put("id", project.id)
                        .put("title", project.title.trim().take(MAX_TITLE_LENGTH))
                        .put("kind", project.kind)
                        .put("updatedAtMs", project.updatedAtMs.coerceAtLeast(0L))
                        .put("payloadJson", project.payloadJson)
                        .put("archived", project.archived)
                )
            }
        return JSONObject()
            .put("version", VERSION)
            .put("projects", items)
            .toString()
    }

    fun decode(raw: String?): StudioProjectLibrary {
        if (raw.isNullOrBlank()) return StudioProjectLibrary(emptyList())
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) return StudioProjectLibrary(emptyList())
            val items = root.optJSONArray("projects") ?: JSONArray()
            val projects = buildList {
                for (index in 0 until minOf(items.length(), MAX_PROJECTS)) {
                    val item = items.optJSONObject(index) ?: continue
                    val project = StudioProjectRecord(
                        id = item.optString("id", "").trim(),
                        title = item.optString("title", "").trim(),
                        kind = item.optString("kind", "VIDEO").trim().ifBlank { "VIDEO" },
                        updatedAtMs = item.optLong("updatedAtMs", 0L).coerceAtLeast(0L),
                        payloadJson = item.optString("payloadJson", ""),
                        archived = item.optBoolean("archived", false)
                    )
                    if (isValid(project)) add(project.copy(title = project.title.take(MAX_TITLE_LENGTH)))
                }
            }.distinctBy { it.id }
            StudioProjectLibrary(projects)
        }.getOrDefault(StudioProjectLibrary(emptyList()))
    }

    private fun isValid(project: StudioProjectRecord): Boolean =
        project.id.isNotBlank() &&
            project.id.length <= MAX_ID_LENGTH &&
            project.title.isNotBlank() &&
            project.kind.isNotBlank() &&
            project.payloadJson.isNotBlank()
}

/**
 * Small local catalogue backed by Android AtomicFile. The editor writes its project payload separately;
 * catalogue writes are quick and do not decode or render any video.
 */
internal class StudioProjectLibraryStore(context: Context) {
    private val file = AtomicFile(File(File(context.filesDir, "studio-projects").apply { mkdirs() }, "library.json"))

    companion object {
        // All store instances point at the same catalogue; serialize across instances as well.
        private val processLock = Any()
    }

    fun read(): StudioProjectLibrary = synchronized(processLock) {
        runCatching {
            file.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                StudioProjectLibraryCodec.decode(reader.readText())
            }
        }.getOrDefault(StudioProjectLibrary(emptyList()))
    }

    fun write(library: StudioProjectLibrary) = synchronized(processLock) {
        var output: FileOutputStream? = null
        try {
            val stream = file.startWrite()
            output = stream
            stream.write(StudioProjectLibraryCodec.encode(library).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            output?.let(file::failWrite)
            throw failure
        }
    }

    fun upsert(project: StudioProjectRecord) = synchronized(processLock) {
        require(project.id.isNotBlank()) { "Project ID cannot be blank" }
        val current = read().projects.filterNot { it.id == project.id }
        write(StudioProjectLibrary((current + project).sortedByDescending { it.updatedAtMs }))
    }

    fun touch(projectId: String, updatedAtMs: Long = System.currentTimeMillis()) = synchronized(processLock) {
        read().projects.firstOrNull { it.id == projectId }?.let { project ->
            upsert(project.copy(updatedAtMs = updatedAtMs.coerceAtLeast(0L)))
        }
    }

    fun remove(projectId: String) = synchronized(processLock) {
        write(StudioProjectLibrary(read().projects.filterNot { it.id == projectId }))
    }
}
