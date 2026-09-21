package com.subscriptiontracker.domain.provider

import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.QuotaOrigin
import com.subscriptiontracker.domain.model.QuotaSyncState
import java.time.Instant
import java.util.UUID

object OnlineQuotaMerge {
    fun apply(
        existing: List<Quota>,
        subscriptionId: UUID,
        providerId: String,
        outcome: OnlineFetchOutcome,
        now: Instant,
    ): List<Quota> {
        val prefix = "$providerId."
        val online = existing.filter {
            it.subscriptionId == subscriptionId &&
                it.origin == QuotaOrigin.ONLINE &&
                it.stableKey?.startsWith(prefix) == true
        }
        return when (outcome) {
            is OnlineFetchOutcome.Failure -> online.map { quota ->
                quota.copy(
                    syncState = if (outcome.kind == OnlineFailureKind.AUTH) {
                        QuotaSyncState.AUTH_REQUIRED
                    } else {
                        QuotaSyncState.STALE
                    },
                    syncNote = if (outcome.kind == OnlineFailureKind.AUTH) "auth-rejected" else "refresh-failed",
                )
            }
            is OnlineFetchOutcome.Success -> {
                val byKey = online.associateBy { it.stableKey }
                val returned = outcome.drafts.map { it.stableKey }.toSet()
                val fresh = outcome.drafts.map { draft ->
                    val previous = byKey[draft.stableKey]
                    Quota(
                        id = previous?.id ?: UUID.randomUUID(),
                        subscriptionId = subscriptionId,
                        name = draft.name,
                        unit = draft.unit,
                        used = draft.used,
                        remaining = draft.remaining,
                        limit = draft.limit,
                        percentage = draft.percentage,
                        unlimited = draft.unlimited,
                        resetEventId = previous?.resetEventId,
                        warningThresholdPercentage = previous?.warningThresholdPercentage,
                        updatedAt = now,
                        stableKey = draft.stableKey,
                        origin = QuotaOrigin.ONLINE,
                        syncState = QuotaSyncState.FRESH,
                        syncNote = draft.note,
                    )
                }
                val stale = online.filter { it.stableKey in outcome.staleKeys && it.stableKey !in returned }
                    .map { it.copy(syncState = QuotaSyncState.STALE, syncNote = "refresh-failed") }
                val missing = online.filter { it.stableKey !in returned && it.stableKey !in outcome.staleKeys }
                    .map { it.copy(syncState = QuotaSyncState.UNAVAILABLE, syncNote = "not-reported") }
                fresh + stale + missing
            }
        }
    }
}
