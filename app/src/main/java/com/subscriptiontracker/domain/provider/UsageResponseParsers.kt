package com.subscriptiontracker.domain.provider

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val usageJson = Json { ignoreUnknownKeys = true }

internal fun parseOpenRouterKey(body: String): MeterDraft {
    val data = usageJson.parseToJsonElement(body).jsonObject.obj("data")
    val usage = data.decimal("usage") ?: unreadable()
    val rawLimit = data.decimal("limit")
    val limit = rawLimit?.takeIf { it > BigDecimal.ZERO }
    val remaining = data.decimal("limit_remaining")
    val overLimit = remaining != null && remaining < BigDecimal.ZERO
    return MeterDraft(
        stableKey = "openrouter.key",
        name = "API key usage",
        unit = "USD",
        used = usage,
        remaining = remaining?.takeIf { it >= BigDecimal.ZERO },
        limit = limit,
        unlimited = rawLimit == null,
        note = if (overLimit) "over-limit" else null,
    )
}

internal fun parseOpenRouterCredits(body: String): MeterDraft {
    val data = usageJson.parseToJsonElement(body).jsonObject.obj("data")
    val total = data.decimal("total_credits") ?: unreadable()
    val used = data.decimal("total_usage") ?: unreadable()
    val remaining = total.subtract(used)
    val limit = total.takeIf { it > BigDecimal.ZERO }
    return MeterDraft(
        stableKey = "openrouter.credits",
        name = "Account credits",
        unit = "USD",
        used = used.takeIf { it >= BigDecimal.ZERO },
        remaining = remaining.takeIf { it >= BigDecimal.ZERO },
        limit = limit,
        note = if (remaining < BigDecimal.ZERO) "over-limit" else null,
    )
}

internal fun parseDeepSeekBalance(body: String): List<MeterDraft> {
    val infos = usageJson.parseToJsonElement(body).jsonObject["balance_infos"]?.jsonArray ?: unreadable()
    return infos.mapIndexedNotNull { index, element ->
        val row = element as? JsonObject ?: return@mapIndexedNotNull null
        val currency = row.string("currency")?.takeIf { it.length == 3 } ?: "credit"
        val total = row.decimal("total_balance")?.takeIf { it >= BigDecimal.ZERO } ?: return@mapIndexedNotNull null
        val granted = row.decimal("granted_balance")?.takeIf { it >= BigDecimal.ZERO }
        val topped = row.decimal("topped_up_balance")?.takeIf { it >= BigDecimal.ZERO }
        val note = if (granted != null && topped != null) "granted=$granted;topped=$topped" else null
        MeterDraft(
            stableKey = "deepseek.balance.$currency.$index",
            name = "Balance $currency",
            unit = currency,
            remaining = total,
            note = note,
        )
    }
}

internal fun parseMoonshotBalance(body: String, keyPrefix: String, currency: String): List<MeterDraft> {
    val root = usageJson.parseToJsonElement(body).jsonObject
    val code = root.string("code")
    if (code != null && code != "0") unreadable()
    val data = root.obj("data")
    val available = data.decimal("available_balance") ?: unreadable()
    val voucher = data.decimal("voucher_balance")
    val cash = data.decimal("cash_balance")
    val deficit = cash?.takeIf { it < BigDecimal.ZERO }?.abs()
    val meters = mutableListOf(
        MeterDraft(
            stableKey = "$keyPrefix.available",
            name = "Available balance",
            unit = currency,
            remaining = available.takeIf { it >= BigDecimal.ZERO },
            note = deficit?.let { "deficit=$it;currency=$currency" },
        ),
    )
    if (voucher != null && voucher >= BigDecimal.ZERO) {
        meters += MeterDraft(
            stableKey = "$keyPrefix.voucher",
            name = "Voucher balance",
            unit = currency,
            remaining = voucher,
        )
    }
    if (cash != null && cash >= BigDecimal.ZERO) {
        meters += MeterDraft(
            stableKey = "$keyPrefix.cash",
            name = "Cash balance",
            unit = currency,
            remaining = cash,
        )
    }
    return meters
}

internal fun parseZaiLimits(body: String, keyPrefix: String): List<MeterDraft> {
    val root = usageJson.parseToJsonElement(body).jsonObject
    val code = root.string("code")
    if (code != null && code != "0" && code != "200") unreadable()
    val data = root["data"] as? JsonObject ?: return emptyList()
    val limits = data["limits"] as? JsonArray ?: return emptyList()
    return limits.mapIndexedNotNull { index, element ->
        val row = element as? JsonObject ?: return@mapIndexedNotNull null
        val type = row.string("type")?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
        val current = row.decimal("currentValue")?.takeIf { it >= BigDecimal.ZERO }
        val remainingRaw = row.decimal("remaining")
        val limit = row.decimal("usage")?.takeIf { it > BigDecimal.ZERO }
        val percentage = row.wholeNumber("percentage")?.takeIf { it in BigDecimal.ZERO..BigDecimal(100) }
        val overLimit = remainingRaw != null && remainingRaw < BigDecimal.ZERO
        val remaining = remainingRaw?.takeIf { it >= BigDecimal.ZERO }
        val hasCounts = current != null || remaining != null || limit != null
        if (!hasCounts && percentage == null) return@mapIndexedNotNull null
        MeterDraft(
            stableKey = "$keyPrefix.${type.lowercase()}.$index",
            name = zaiLimitName(type),
            unit = zaiLimitUnit(type),
            used = current,
            remaining = remaining,
            limit = if (hasCounts) limit else null,
            percentage = if (hasCounts) null else percentage,
            note = if (overLimit) "over-limit" else null,
        )
    }
}

private fun zaiLimitName(type: String): String = when (type) {
    "TOKENS_LIMIT" -> "Token limit"
    "CREDIT_LIMIT" -> "Credit limit"
    "TIME_LIMIT" -> "Time limit"
    else -> type
}

private fun zaiLimitUnit(type: String): String = when (type) {
    "TOKENS_LIMIT" -> "tokens"
    "CREDIT_LIMIT" -> "credits"
    "TIME_LIMIT" -> "units"
    else -> "units"
}

private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: unreadable()

private fun JsonObject.decimal(key: String): BigDecimal? {
    val value = this[key] ?: return null
    if (value is JsonNull) return null
    val primitive = value as? JsonPrimitive ?: return null
    return primitive.content.toBigDecimalOrNull()
}

private fun JsonObject.wholeNumber(key: String): BigDecimal? {
    val number = decimal(key) ?: return null
    val normalized = number.stripTrailingZeros()
    if (normalized.scale() > 0) return null
    return number
}

private fun JsonObject.string(key: String): String? {
    val value = this[key] ?: return null
    if (value is JsonNull) return null
    val primitive = value as? JsonPrimitive ?: return null
    return primitive.content
}

private fun unreadable(): Nothing = throw IllegalArgumentException("Usage response could not be read")
