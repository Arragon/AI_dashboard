package com.subscriptiontracker.domain.provider

import java.math.BigDecimal

data class OnlineProvider(
    val id: String,
    val displayName: String,
)

object OnlineProviders {
    val all: List<OnlineProvider> = listOf(
        OnlineProvider("openrouter", "OpenRouter"),
        OnlineProvider("deepseek", "DeepSeek"),
        OnlineProvider("moonshot", "Moonshot"),
        OnlineProvider("moonshot-intl", "Moonshot International"),
        OnlineProvider("zai", "z.ai"),
        OnlineProvider("bigmodel", "BigModel"),
    )

    fun find(id: String?): OnlineProvider? = all.firstOrNull { it.id == id }
}

data class MeterDraft(
    val stableKey: String,
    val name: String,
    val unit: String,
    val used: BigDecimal? = null,
    val remaining: BigDecimal? = null,
    val limit: BigDecimal? = null,
    val percentage: BigDecimal? = null,
    val unlimited: Boolean = false,
    val note: String? = null,
) {
    init {
        require(stableKey.isNotBlank()) { "Meter key cannot be blank" }
        require(name.isNotBlank()) { "Meter name cannot be blank" }
        require(unit.isNotBlank()) { "Meter unit cannot be blank" }
        require(used == null || used >= BigDecimal.ZERO) { "Used amount cannot be negative" }
        require(remaining == null || remaining >= BigDecimal.ZERO) { "Remaining amount cannot be negative" }
        require(limit == null || limit > BigDecimal.ZERO) { "Limit must be positive" }
        require(percentage == null || percentage in BigDecimal.ZERO..BigDecimal(100)) {
            "Percentage must be between 0 and 100"
        }
        require(note == null || note.isNotBlank()) { "Meter note cannot be blank" }
    }
}

enum class OnlineFailureKind { AUTH, TRANSIENT, PARSE, UNSUPPORTED }

sealed interface OnlineFetchOutcome {
    data class Success(
        val drafts: List<MeterDraft>,
        val staleKeys: Set<String> = emptySet(),
    ) : OnlineFetchOutcome

    data class Failure(
        val kind: OnlineFailureKind,
        val message: String,
    ) : OnlineFetchOutcome
}

data class QuotaHttpResponse(
    val statusCode: Int,
    val body: String,
)

fun interface QuotaHttp {
    suspend fun get(url: String, bearerToken: String): QuotaHttpResponse
}
