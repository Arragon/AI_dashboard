package com.subscriptiontracker.domain.provider

import java.io.IOException

class OnlineQuotaService(
    private val http: QuotaHttp,
) {
    suspend fun fetch(providerId: String, apiKey: String): OnlineFetchOutcome {
        val key = apiKey.trim()
        if (key.isEmpty()) return failure(OnlineFailureKind.AUTH, "API key was rejected")
        if (OnlineProviders.find(providerId) == null) {
            return failure(OnlineFailureKind.UNSUPPORTED, "Online provider is not supported")
        }
        return try {
            when (providerId) {
                "openrouter" -> fetchOpenRouter(key)
                "deepseek" -> fetchParsed("https://api.deepseek.com/user/balance", key, ::parseDeepSeekBalance)
                "moonshot" -> fetchParsed("https://api.moonshot.cn/v1/users/me/balance", key) {
                    parseMoonshotBalance(it, "moonshot", "CNY")
                }
                "moonshot-intl" -> fetchParsed("https://api.moonshot.ai/v1/users/me/balance", key) {
                    parseMoonshotBalance(it, "moonshot-intl", "USD")
                }
                "zai" -> fetchParsed("https://api.z.ai/api/monitor/usage/quota/limit", key) {
                    parseZaiLimits(it, "zai")
                }
                "bigmodel" -> fetchParsed("https://open.bigmodel.cn/api/monitor/usage/quota/limit", key) {
                    parseZaiLimits(it, "bigmodel")
                }
                else -> failure(OnlineFailureKind.UNSUPPORTED, "Online provider is not supported")
            }
        } catch (_: IOException) {
            failure(OnlineFailureKind.TRANSIENT, "Usage request failed")
        } catch (_: IllegalArgumentException) {
            failure(OnlineFailureKind.PARSE, "Usage response could not be read")
        }
    }

    private suspend fun fetchOpenRouter(apiKey: String): OnlineFetchOutcome {
        val keyResponse = request("https://openrouter.ai/api/v1/key", apiKey)
        statusFailure(keyResponse.statusCode)?.let { return it }
        val keyMeter = parseOpenRouterKey(keyResponse.body)
        val credits = try {
            request("https://openrouter.ai/api/v1/credits", apiKey)
        } catch (_: IOException) {
            return OnlineFetchOutcome.Success(listOf(keyMeter), staleKeys = setOf("openrouter.credits"))
        }
        if (statusFailure(credits.statusCode) != null) {
            return OnlineFetchOutcome.Success(listOf(keyMeter), staleKeys = setOf("openrouter.credits"))
        }
        val creditsMeter = try {
            parseOpenRouterCredits(credits.body)
        } catch (_: IllegalArgumentException) {
            return OnlineFetchOutcome.Success(listOf(keyMeter), staleKeys = setOf("openrouter.credits"))
        }
        return OnlineFetchOutcome.Success(listOf(keyMeter, creditsMeter))
    }

    private suspend fun fetchParsed(
        url: String,
        apiKey: String,
        parse: (String) -> List<MeterDraft>,
    ): OnlineFetchOutcome {
        val response = request(url, apiKey)
        statusFailure(response.statusCode)?.let { return it }
        return OnlineFetchOutcome.Success(parse(response.body))
    }

    private suspend fun request(url: String, apiKey: String): QuotaHttpResponse {
        require(url.startsWith("https://")) { "Usage requests must use HTTPS" }
        return http.get(url, apiKey)
    }

    private fun statusFailure(statusCode: Int): OnlineFetchOutcome.Failure? = when (statusCode) {
        in 200..299 -> null
        401, 403 -> failure(OnlineFailureKind.AUTH, "API key was rejected")
        else -> failure(OnlineFailureKind.TRANSIENT, "Usage request failed")
    }

    private fun failure(kind: OnlineFailureKind, message: String) = OnlineFetchOutcome.Failure(kind, message)
}
