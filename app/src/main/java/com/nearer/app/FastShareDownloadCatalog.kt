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
    val fileLength: Long,
    val lastModifiedMillis: Long? = null
)

/** Converts provider metadata into a useful, non-empty receiver-facing file name. */
internal object FastShareDisplayName {
    fun resolve(providerName: String?, fallbackPath: String?): String {
        providerName?.takeIf { it.isNotBlank() }?.let { return it }
        return fallbackPath
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "Shared file"
    }
}

/** A bounded, zero-based view of the files offered by one Fast Share session. */
internal class FastShareDownloadCatalog(items: List<FastShareDownloadItem>) {
    val items: List<FastShareDownloadItem> = items.toList()

    init {
        require(this.items.all { it.fileLength >= -1L }) {
            "File lengths must be non-negative or -1 when unknown."
        }
        require(this.items.isNotEmpty()) { "Fast Share needs at least one file." }
        require(this.items.size <= MAX_FILES) { "Too many files in one Fast Share session." }
        require(this.items.map { it.index } == this.items.indices.toList()) {
            "Fast Share item indexes must be contiguous and zero-based."
        }
    }

    fun itemAt(index: Int): FastShareDownloadItem? = items.getOrNull(index)

    fun receiverPage(accessKey: String, scriptNonce: String = accessKey): String {
        val rows = items.joinToString("") { item ->
            val name = escapeHtml(item.fileName)
            val size = if (item.fileLength >= 0L) formatSize(item.fileLength) else "Size unavailable"
            """<li class="file"><div class="name">$name</div><div class="meta">$size</div><a class="download" href="/download/${item.index}?key=$accessKey">Download</a></li>"""
        }
        val heading = if (items.size == 1) "Your file is ready" else "${items.size} files are ready"
        val downloadAll = if (items.size > 1) """<a class="download-all" href="/download-all?key=$accessKey">Download all files · ZIP</a>""" else ""
        val uploadPanel = """<section class="upload-panel"><h2>Send files to this phone</h2><p>Choose files on this device and send them back to the NeaRer phone over this private Wi-Fi connection.</p><input id="upload-files" type="file" multiple><button id="upload-button" type="button">Send selected files</button><progress id="upload-progress" max="100" value="0"></progress><p id="upload-status" role="status">No files selected yet.</p></section>"""
        val uploadScript = """<script nonce="$scriptNonce">const accessKey="$accessKey";const picker=document.getElementById("upload-files");const button=document.getElementById("upload-button");const progress=document.getElementById("upload-progress");const status=document.getElementById("upload-status");button.addEventListener("click",async()=>{const files=Array.from(picker.files||[]);if(!files.length){status.textContent="Choose at least one file first.";return;}button.disabled=true;let done=0;progress.value=0;try{for(const file of files){status.textContent="Preparing "+file.name+"…";await new Promise((resolve,reject)=>{const xhr=new XMLHttpRequest();xhr.open("PUT","/upload?key="+accessKey+"&name="+encodeURIComponent(file.name));xhr.setRequestHeader("Content-Type",file.type||"application/octet-stream");xhr.upload.onprogress=event=>{if(event.lengthComputable){progress.value=((done+event.loaded/event.total)/files.length)*100;status.textContent="Receiving "+file.name+" · "+Math.round(event.loaded/event.total*100)+"%";}};xhr.onload=()=>{if(xhr.status===201)resolve();else reject(new Error(xhr.responseText||("Upload failed ("+xhr.status+")")));};xhr.onerror=()=>reject(new Error("Connection interrupted. Keep both devices on the same Wi-Fi and retry."));xhr.onabort=()=>reject(new Error("Upload cancelled."));xhr.send(file);});done++;progress.value=(done/files.length)*100;status.textContent="Received "+done+" of "+files.length+" files.";}}catch(error){status.textContent=error.message||"Upload failed. Try again.";}finally{button.disabled=false;}});</script>"""
        return """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#101a2b"><title>NeaRer Fast Share</title><style>*{box-sizing:border-box}body{font-family:Arial,sans-serif;background:radial-gradient(circle at 85% 5%,#29466a 0,transparent 36%),#101a2b;color:#f6f8fc;padding:24px 16px;margin:0;min-height:100vh;display:grid;place-items:center}.card{width:100%;max-width:520px;background:#1a2940;border:1px solid #344963;border-radius:26px;padding:26px;box-shadow:0 18px 50px #07101f88}.brand{color:#6ea8ff;font-size:13px;font-weight:800;letter-spacing:2px}.mark{width:52px;height:52px;border-radius:16px;background:#ffd12d;color:#101a2b;display:grid;place-items:center;font-size:28px;font-weight:900;margin:18px 0}.eyebrow{color:#ffd12d;font-size:11px;font-weight:800;letter-spacing:1.5px}h1{font-size:25px;margin:8px 0 12px}.intro{color:#a8b6c9;font-size:13px;line-height:1.6}.files{list-style:none;padding:0;margin:22px 0 0}.file{padding:16px 0;border-top:1px solid #344963;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:7px 12px}.name{font-weight:700;overflow-wrap:anywhere}.meta{color:#a8b6c9;font-size:12px} .download{grid-column:1/-1;display:block;background:#ffd12d;color:#101a2b;text-decoration:none;font-weight:800;text-align:center;padding:14px;border-radius:12px;margin-top:5px;transition:transform .16s ease,filter .16s ease}.download:hover{filter:brightness(1.06);transform:translateY(-1px)}.download:focus-visible,.download-all:focus-visible{outline:3px solid #6ea8ff;outline-offset:3px}.download-all{display:block;background:#2563eb;color:#fff;text-decoration:none;font-weight:800;text-align:center;padding:14px;border-radius:12px;margin-top:14px}.upload-panel{margin-top:22px;padding:16px;background:#101a2b;border:1px solid #344963;border-radius:16px}.upload-panel h2{font-size:16px;margin:0 0 8px}.upload-panel p{color:#a8b6c9;font-size:12px;line-height:1.5}.upload-panel input{display:block;width:100%;color:#cbd6e5;margin:14px 0}.upload-panel button{width:100%;border:0;border-radius:12px;padding:14px;background:#6ea8ff;color:#101a2b;font-weight:800}.upload-panel button:disabled{opacity:.55}.upload-panel progress{width:100%;height:8px;margin-top:12px;accent-color:#ffd12d}.steps{margin:20px 0 8px;padding:16px;background:#101a2b;border-radius:16px;color:#cbd6e5;font-size:13px;line-height:1.7}.foot{color:#a8b6c9;font-size:11px;text-align:center;margin:18px 0 0}</style></head><body><main class="card"><div class="brand">NEARER</div><div class="mark">N</div><div class="eyebrow">PRIVATE LOCAL TRANSFER</div><h1>$heading</h1><p class="intro">On a phone or computer, connect to the sender's NeaRer Wi-Fi and open this page in a browser. Download individual files below or save the complete selection as one ZIP archive. You can also send files from this device back to the NeaRer phone.</p>$uploadPanel$uploadScript$downloadAll<ul class="files">$rows</ul><div class="steps"><strong>Keep this page open</strong><br>Stay connected to the sender's Wi-Fi while downloading. This local transfer does not use mobile data.</div><p class="foot">NeaRer · Simple sharing, even without mobile data.</p></main></body></html>"""
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
