package com.swiperf.app.data.remote

import com.swiperf.app.data.model.Cluster

/**
 * Abstraction for fetching session data from a remote endpoint.
 *
 * To enable: set [endpoint] to your server URL. The endpoint should return
 * a JSON body in SwiPerf session format:
 * { "version": 1, "clusters": [...], "activeClusterId": "..." }
 *
 * Or raw trace data (JSON array / TSV / CSV) which will be parsed as a new cluster.
 */
object RemoteSource {

    /** Set this to your endpoint URL to enable remote sync. null = disabled. */
    var endpoint: String? = null

    /** Whether remote sync is available. */
    val isEnabled: Boolean get() = !endpoint.isNullOrBlank()

    /**
     * Fetch session data from [endpoint].
     * Returns the raw response body as a String, or null on failure.
     * Caller is responsible for parsing (session JSON or raw trace data).
     */
    suspend fun fetch(): Result<String> {
        val url = endpoint ?: return Result.failure(Exception("No endpoint configured"))
        return try {
            // Simple HttpURLConnection — no library dependency needed
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")

            val code = conn.responseCode
            if (code !in 200..299) {
                return Result.failure(Exception("HTTP $code"))
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            Result.success(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
