package com.subscriptiontracker.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

data class Quota(
    val id: UUID,
    val subscriptionId: UUID,
    val name: String,
    val unit: String,
    val used: BigDecimal? = null,
    val remaining: BigDecimal? = null,
    val limit: BigDecimal? = null,
    val percentage: BigDecimal? = null,
    val unlimited: Boolean = false,
    val resetEventId: UUID? = null,
    val warningThresholdPercentage: BigDecimal? = null,
    val updatedAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Quota name cannot be blank" }
        require(used == null || used >= BigDecimal.ZERO) { "Used quota cannot be negative" }
        require(remaining == null || remaining >= BigDecimal.ZERO) { "Remaining quota cannot be negative" }
        require(limit == null || limit > BigDecimal.ZERO) { "Quota limit must be positive" }
        require(percentage == null || percentage in BigDecimal.ZERO..BigDecimal(100)) {
            "Quota percentage must be between 0 and 100"
        }
        require(warningThresholdPercentage == null || warningThresholdPercentage in BigDecimal.ZERO..BigDecimal(100)) {
            "Warning threshold must be between 0 and 100"
        }
    }

    fun usedPercentage(): BigDecimal? {
        if (unlimited) return null
        percentage?.let { return it }
        if (used != null && limit != null) {
            return used.multiply(BigDecimal(100)).divide(limit, 2, RoundingMode.HALF_UP)
        }
        if (remaining != null && limit != null) {
            return limit.subtract(remaining)
                .multiply(BigDecimal(100))
                .divide(limit, 2, RoundingMode.HALF_UP)
        }
        return null
    }
}
