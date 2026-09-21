package com.subscriptiontracker.platform.quota

import com.subscriptiontracker.domain.provider.QuotaHttp
import com.subscriptiontracker.domain.provider.QuotaHttpResponse
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UrlConnectionQuotaHttp : QuotaHttp {
    override suspend fun get(url: String, bearerToken: String): QuotaHttpResponse = withContext(Dispatchers.IO) {
        val parsed = URI(url)
        if (parsed.scheme != "https") throw IOException("Usage requests must use HTTPS")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { reader ->
                val text = reader.readText()
                if (text.length > MAX_BODY_CHARS) throw IOException("Usage response is too large")
                text
            }.orEmpty()
            QuotaHttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val MAX_BODY_CHARS = 1_000_000
    }
}
