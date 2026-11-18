package com.nearer.app

/**
 * Portable CSV export for controlled HyperTransfer/Xender evidence.
 *
 * Keep devicePairKey pseudonymous (for example, "phone-A__phone-B"); never
 * put names, phone numbers, Android IDs, or account identifiers in a report.
 * Failed runs are retained so the median cannot hide instability.
 */
internal object TransferBenchmarkReport {
    data class Evidence(
        val app: String,
        val devicePairKey: String,
        val direction: HyperTransferTransportPolicy.Direction,
        val fileSha256: String,
        val fileBytes: Long,
        val conditionsKey: String,
        val runs: List<TransferBenchmarkRun>
    ) {
        init {
            require(app.isNotBlank())
            require(devicePairKey.isNotBlank())
            require(fileSha256.matches(Regex("^[a-fA-F0-9]{64}$")))
            require(fileBytes > 0L)
            require(conditionsKey.isNotBlank())
            require(runs.isNotEmpty())
            require(runs.all { !it.succeeded || it.bytesTransferred == fileBytes }) {
                "Successful benchmark runs must transfer the exact comparison file size"
            }
        }
    }

    private val header = listOf(
        "app", "device_pair_key", "direction", "file_sha256", "file_bytes",
        "conditions_key", "run_index", "succeeded", "bytes_transferred",
        "elapsed_ms", "first_byte_ms", "throughput_MB_s", "throughput_MiB_s"
    )

    /** Emits one row per attempt, including failures; rates are blank for failed/zero-time runs. */
    fun toCsv(evidence: List<Evidence>): String {
        require(evidence.isNotEmpty()) { "At least one app evidence set is required" }
        val rows = mutableListOf(header)
        evidence.forEach { item ->
            item.runs.forEachIndexed { index, run ->
                val validRate = run.succeeded && run.elapsedMillis > 0L
                val seconds = run.elapsedMillis / 1000.0
                val mbPerSecond = if (validRate) run.bytesTransferred / seconds / 1_000_000.0 else null
                val mibPerSecond = if (validRate) run.bytesTransferred / seconds / 1_048_576.0 else null
                rows += listOf(
                    item.app,
                    item.devicePairKey,
                    item.direction.name,
                    item.fileSha256.lowercase(),
                    item.fileBytes.toString(),
                    item.conditionsKey,
                    (index + 1).toString(),
                    run.succeeded.toString(),
                    run.bytesTransferred.toString(),
                    run.elapsedMillis.toString(),
                    run.firstByteMillis.toString(),
                    mbPerSecond?.let { "%.6f".format(java.util.Locale.US, it) }.orEmpty(),
                    mibPerSecond?.let { "%.6f".format(java.util.Locale.US, it) }.orEmpty()
                )
            }
        }
        return rows.joinToString(separator = "\n") { row ->
            row.joinToString(separator = ",") { escape(it) }
        } + "\n"
    }

    private fun escape(value: String): String {
        // CSV quoting alone does not stop spreadsheet formula execution.
        val safe = if (value.firstOrNull()?.let { it in setOf('=', '+', '-', '@', '\t', '\r') } == true) {
            "'$value"
        } else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else safe
    }
}
