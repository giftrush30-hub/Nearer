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

    fun receiverPage(accessKey: String, nonce: String = FastSharePageNonce.create()): String {
        val rows = items.joinToString("") { item ->
            val name = escapeHtml(item.fileName)
            val size = if (item.fileLength >= 0L) formatSize(item.fileLength) else "Size unavailable"
            """<li class="file"><div class="name">$name</div><div class="meta">$size</div><a class="download" href="/download/${item.index}?key=$accessKey">Download</a></li>"""
        }
        val heading = if (items.size == 1) "Your file is ready" else "${items.size} files are ready"
        return """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#101a2b"><title>NeaRer Fast Share</title><style nonce="$nonce">*{box-sizing:border-box}body{font-family:Arial,sans-serif;background:radial-gradient(circle at 85% 5%,#29466a 0,transparent 36%),#101a2b;color:#f6f8fc;padding:24px 16px;margin:0;min-height:100vh;display:grid;place-items:center}.card{width:100%;max-width:520px;background:#1a2940;border:1px solid #344963;border-radius:26px;padding:26px;box-shadow:0 18px 50px #07101f88}.brand{color:#6ea8ff;font-size:13px;font-weight:800;letter-spacing:2px}.mark{width:52px;height:52px;border-radius:16px;background:#ffd12d;color:#101a2b;display:grid;place-items:center;font-size:28px;font-weight:900;margin:18px 0}.eyebrow{color:#ffd12d;font-size:11px;font-weight:800;letter-spacing:1.5px}h1{font-size:25px;margin:8px 0 12px}.intro{color:#a8b6c9;font-size:13px;line-height:1.6}.files{list-style:none;padding:0;margin:22px 0 0}.file{padding:16px 0;border-top:1px solid #344963;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:7px 12px}.name{font-weight:700;overflow-wrap:anywhere}.meta{color:#a8b6c9;font-size:12px} .download{grid-column:1/-1;display:block;background:#ffd12d;color:#101a2b;text-decoration:none;font-weight:800;text-align:center;padding:14px;border-radius:12px;margin-top:5px;transition:transform .16s ease,filter .16s ease}.download:hover{filter:brightness(1.06);transform:translateY(-1px)}.download:focus-visible{outline:3px solid #6ea8ff;outline-offset:3px}.upload{margin:22px 0 8px;padding:18px;background:#101a2b;border:1px solid #344963;border-radius:16px}.upload h2{font-size:18px;margin:7px 0}.upload input{display:block;width:100%;margin:14px 0;color:#cbd6e5}.upload button{width:100%;border:0;background:#6ea8ff;color:#101a2b;font-weight:800;padding:14px;border-radius:12px}.upload progress{width:100%;margin-top:14px;accent-color:#ffd12d}.steps{margin:20px 0 8px;padding:16px;background:#101a2b;border-radius:16px;color:#cbd6e5;font-size:13px;line-height:1.7}.foot{color:#a8b6c9;font-size:11px;text-align:center;margin:18px 0 0}</style></head><body><main class="card"><div class="brand">NEARER</div><div class="mark">N</div><div class="eyebrow">PRIVATE LOCAL TRANSFER</div><h1>$heading</h1><p class="intro">On a phone or computer, connect to the sender's NeaRer Wi-Fi and open this page in a browser. Download each item below. Large files are streamed individually; they are not bundled into a second copy.</p><ul class="files">$rows</ul><section class="upload"><div class="eyebrow">SEND TO THIS PHONE</div><h2>Upload from this device</h2><p class="intro">Choose files on this phone or computer. They are saved privately in NeaRer and appear in its Downloads collection.</p><input id="uploads" type="file" multiple><button id="send" type="button">Send files to NeaRer</button><button id="cancel" type="button" hidden>Cancel upload</button><progress id="progress" max="100" value="0" hidden></progress><p id="uploadStatus" class="meta" role="status" aria-live="polite"></p></section><div class="steps"><strong>Keep this page open</strong><br>Stay connected to the sender's Wi-Fi while downloading. This local transfer does not use mobile data.</div><p class="foot">NeaRer · Simple sharing, even without mobile data.</p></main><script nonce="$nonce">const picker=document.getElementById("uploads"),send=document.getElementById("send"),cancel=document.getElementById("cancel"),status=document.getElementById("uploadStatus"),progress=document.getElementById("progress");let activeRequest=null,cancelled=false;const sizeLabel=value=>{if(value<1024)return value+" B";const units=["KB","MB","GB","TB"];let n=value,u=-1;do{n/=1024;u++;}while(n>=1024&&u<units.length-1);return n.toFixed(1)+" "+units[u];};const upload=(file,onProgress)=>new Promise((resolve,reject)=>{const xhr=new XMLHttpRequest();activeRequest=xhr;xhr.open("POST","/upload?key=$accessKey");xhr.setRequestHeader("X-Filename",encodeURIComponent(file.name));xhr.setRequestHeader("Content-Type",file.type||"application/octet-stream");xhr.upload.onprogress=event=>{if(event.lengthComputable)onProgress(event.loaded);};xhr.onload=()=>{if(xhr.status!==201){const message=xhr.status===408?"connection became idle; reconnect and retry":xhr.status===429?"NeaRer is busy; wait briefly and retry":xhr.status===507?"not enough free storage on the receiving phone":("server returned "+xhr.status);reject(new Error(file.name+": "+message));return;}const received=Number(xhr.getResponseHeader("X-Received-Bytes"));const digest=xhr.getResponseHeader("X-Content-SHA256")||"";if(received!==file.size||! /^[0-9a-f]{64}$/.test(digest)){reject(new Error(file.name+": transfer receipt could not be verified"));return;}resolve();};xhr.onerror=()=>reject(new Error(file.name+": connection lost"));xhr.onabort=()=>reject(new Error("Upload cancelled"));xhr.send(file);});send.addEventListener("click",async()=>{const files=Array.from(picker.files||[]);if(!files.length){status.textContent="Choose files first.";return;}cancelled=false;send.disabled=true;send.textContent="Sending…";cancel.disabled=false;cancel.hidden=false;progress.hidden=false;progress.value=0;const total=files.reduce((sum,file)=>sum+file.size,0);let completedBytes=0,done=0,nextIndex=0;const retryFiles=[];for(;nextIndex<files.length;nextIndex++){if(cancelled)break;const file=files[nextIndex];status.textContent="Sending "+file.name+" ("+(nextIndex+1)+" of "+files.length+")";try{await upload(file,loaded=>{progress.value=total?Math.min(100,(completedBytes+loaded)*100/total):0;status.textContent="Sending "+file.name+" · "+sizeLabel(loaded)+" of "+sizeLabel(file.size);});completedBytes+=file.size;done++;progress.value=total?completedBytes*100/total:done*100/files.length;}catch(error){if(cancelled)break;retryFiles.push(file);status.textContent="Could not send "+file.name+". Trying the next file…";}}if(cancelled)retryFiles.push(...files.slice(nextIndex));try{const selection=new DataTransfer();retryFiles.forEach(file=>selection.items.add(file));picker.files=selection.files;}catch(_){picker.value="";}if(cancelled){status.textContent="Cancelled after "+done+" of "+files.length+" file(s) received. Remaining files are ready to retry.";}else if(retryFiles.length){status.textContent="Received "+done+" of "+files.length+". "+retryFiles.length+" file(s) need retry: "+retryFiles.map(file=>file.name).join(", ")+". Tap Retry to send them again.";}else{status.textContent=done+" file(s) received. Find them in NeaRer Downloads.";}activeRequest=null;send.disabled=false;send.textContent=retryFiles.length?"Retry "+retryFiles.length+" file(s)":"Send files to NeaRer";cancel.disabled=false;cancel.hidden=true;});cancel.addEventListener("click",()=>{cancelled=true;cancel.disabled=true;activeRequest?.abort();status.textContent="Cancelling upload…";});</script></body></html>"""
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
