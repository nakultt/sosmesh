package com.meshsos.data.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private const val TAG = "RetryInterceptor"

/**
 * OkHttp interceptor that retries failed requests with exponential backoff.
 * Only retries on IOExceptions (network failures) and 5xx server errors.
 * Does NOT retry 4xx client errors — those won't be fixed by retrying.
 */
class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null

        for (attempt in 0 until maxRetries) {
            try {
                response?.close()
                response = chain.proceed(request)

                if (response.isSuccessful) return response

                // Don't retry client errors
                if (response.code in 400..499) return response

                // Server error — retry after backoff
                Log.w(TAG, "Server error ${response.code}, attempt ${attempt + 1}/$maxRetries")
                response.close()
                sleep(backoffMs(attempt))

            } catch (e: IOException) {
                Log.w(TAG, "Network error on attempt ${attempt + 1}/$maxRetries: ${e.message}")
                lastException = e
                sleep(backoffMs(attempt))
            }
        }

        // All retries exhausted
        if (lastException != null) throw lastException
        return response ?: throw IOException("No response after $maxRetries attempts")
    }

    /**
     * Exponential backoff: 1s, 2s, 4s
     */
    private fun backoffMs(attempt: Int): Long = (1000L * (1 shl attempt)).coerceAtMost(30_000L)

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
    }
}
