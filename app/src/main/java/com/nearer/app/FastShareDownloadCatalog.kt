package com.nearer.app

/**
 * Immutable metadata for one file in a Fast Share session.
 * The URI itself stays with the server/controller; this value is safe to use
 * when rendering the receiver's file list.
 */
internal data class FastShareDownloadItem(
    val index: Int,
    val fileName: String,
    val mimeType: String,
    val fileLength: Long
)

/** A bounded, zero-based view of the files offered by one Fast Share session. */
internal class FastShareDownloadCatalog(items: List<FastShareDownloadItem>) {
    val items: List<FastShareDownloadItem> = items.toList()

    init {
        require(this.items.isNotEmpty()) { "Fast Share needs at least one file." }
        require(this.items.size <= MAX_FILES) { "Too many files in one Fast Share session." }
        require(this.items.map { it.index } == this.items.indices.toList()) {
            "Fast Share item indexes must be contiguous and zero-based."
        }
    }

    fun itemAt(index: Int): FastShareDownloadItem? = items.getOrNull(index)

    fun receiverPage(accessKey: String): String {
        val rows = items.joinToString("") { item ->
            val name = escapeHtml(item.fileName)
            val size = if (item.fileLength >= 0L) formatSize(item.fileLength) else "Size unavailable"
            """<li class="file"><div class="name">$name</div><div class="meta">$size</div><a class="download" href="/download/${item.index}?key=$accessKey">Download</a></li>"""
        }
        val heading = if (items.size == 1) "Your file is ready" else "${items.size} files are ready"
        return """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#101a2b"><title>NeaRer Fast Share</title><style>*{box-sizing:border-box}body{font-family:Arial,sans-serif;background:radial-gradient(circle at 85% 5%,#29466a 0,transparent 36%),#101a2b;color:#f6f8fc;padding:24px 16px;margin:0;min-height:100vh;display:grid;place-items:center}.card{width:100%;max-width:520px;background:#1a2940;border:1px solid #344963;border-radius:26px;padding:26px;box-shadow:0 18px 50px #07101f88}.brand{color:#6ea8ff;font-size:13px;font-weight:800;letter-spacing:2px}.mark{width:52px;height:52px;border-radius:16px;background:#ffd12d;color:#101a2b;display:grid;place-items:center;font-size:28px;font-weight:900;margin:18px 0}.eyebrow{color:#ffd12d;font-size:11px;font-weight:800;letter-spacing:1.5px}h1{font-size:25px;margin:8px 0 12px}.intro{color:#a8b6c9;font-size:13px;line-height:1.6}.files{list-style:none;padding:0;margin:22px 0 0}.file{padding:16px 0;border-top:1px solid #344963;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:7px 12px}.name{font-weight:700;overflow-wrap:anywhere}.meta{color:#a8b6c9;font-size:12px} .download{grid-column:1/-1;display:block;background:#ffd12d;color:#101a2b;text-decoration:none;font-weight:800;text-align:center;padding:14px;border-radius:12px;margin-top:5px;transition:transform .16s ease,filter .16s ease}.download:hover{filter:brightness(1.06);transform:translateY(-1px)}.download:focus-visible{outline:3px solid #6ea8ff;outline-offset:3px}.steps{margin:20px 0 8px;padding:16px;background:#101a2b;border-radius:16px;color:#cbd6e5;font-size:13px;line-height:1.7}.foot{color:#a8b6c9;font-size:11px;text-align:center;margin:18px 0 0}</style></head><body><main class="card"><div class="brand">NEARER</div><div class="mark">N</div><div class="eyebrow">PRIVATE LOCAL TRANSFER</div><h1>$heading</h1><p class="intro">On a phone or computer, connect to the sender's NeaRer Wi-Fi and open this page in a browser. Download each item below. Large files are streamed individually; they are not bundled into a second copy.</p><ul class="files">$rows</ul><div class="steps"><strong>Keep this page open</strong><br>Stay connected to the sender's Wi-Fi while downloading. This local transfer does not use mobile data.</div><p class="foot">NeaRer · Simple sharing, even without mobile data.</p></main></body></html>"""
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        do {
            value /= 1024.0
            unit++
        } while (value >= 1024.0 && unit < units.lastIndex)
        return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
    }

    companion object {
        const val MAX_FILES = 100
    }
}
