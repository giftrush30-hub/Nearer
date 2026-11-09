package com.nearer.app

/**
 * Pure HTTP representation metadata for a known-length file response.
 * Keeping this separate from sockets and Android providers makes status and
 * range headers independently testable.
 */
internal data class FileHttpResponse(
    val statusCode: Int,
    val statusText: String,
    val range: ResolvedByteRange?,
    val contentLength: Long,
    val contentRange: String?
)

internal object FileHttpResponses {
    fun resolve(rangeHeader: String?, fileLength: Long): FileHttpResponse {
        require(fileLength >= 0L) { "File length must be known and non-negative" }
        val range = ByteRange.resolve(rangeHeader, fileLength)
            ?: return FileHttpResponse(
                statusCode = 416,
                statusText = "Range Not Satisfiable",
                range = null,
                contentLength = 0L,
                contentRange = "bytes */$fileLength"
            )
        val isPartial = range.partial
        val contentLength = if (fileLength == 0L) 0L else range.endInclusive - range.start + 1L
        val contentRange = if (isPartial) {
            "bytes ${range.start}-${range.endInclusive}/$fileLength"
        } else null
        return FileHttpResponse(
            statusCode = if (isPartial) 206 else 200,
            statusText = if (isPartial) "Partial Content" else "OK",
            range = range,
            contentLength = contentLength,
            contentRange = contentRange
        )
    }
}
