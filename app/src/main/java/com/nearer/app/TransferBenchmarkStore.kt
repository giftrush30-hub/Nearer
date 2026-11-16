package com.nearer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small bounded history of measured HyperTransfer runs. These are application-
 * level end-to-end measurements, not advertised Wi-Fi PHY rates.
 */
internal class TransferBenchmarkStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "nearer_transfer_benchmarks", Context.MODE_PRIVATE
    )
    private val lock = Any()

    fun record(run: TransferBenchmarkRun) = synchronized(lock) {
        val history = readLocked().toMutableList()
        history.add(run)
        while (history.size > MAX_RUNS) history.removeAt(0)
        val array = JSONArray()
        history.forEach {
            array.put(JSONObject()
                .put("bytes", it.bytesTransferred)
                .put("elapsedMs", it.elapsedMillis)
                .put("firstByteMs", it.firstByteMillis)
                .put("succeeded", it.succeeded)
                .put("peerId", it.peerId))
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    fun recentRuns(): List<TransferBenchmarkRun> = synchronized(lock) { readLocked() }

    fun summary(peerId: String? = null): TransferBenchmarkSummary? = synchronized(lock) {
        val history = readLocked().let { runs ->
            if (peerId == null) runs else runs.filter { it.peerId == peerId }
        }
        runCatching { TransferBenchmarkMetrics.summarize(history) }.getOrNull()
    }

    fun clear() = synchronized(lock) {
        preferences.edit().remove(KEY).apply()
    }

    private fun readLocked(): List<TransferBenchmarkRun> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val run = runCatching {
                        TransferBenchmarkRun(
                            bytesTransferred = item.getLong("bytes"),
                            elapsedMillis = item.getLong("elapsedMs"),
                            firstByteMillis = item.getLong("firstByteMs"),
                            succeeded = item.getBoolean("succeeded"),
                            peerId = item.optString("peerId", "")
                        )
                    }.getOrNull() ?: continue
                    add(run)
                }
            }.takeLast(MAX_RUNS)
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "verified_hypertransfer_runs_v1"
        const val MAX_RUNS = 60
    }
}
