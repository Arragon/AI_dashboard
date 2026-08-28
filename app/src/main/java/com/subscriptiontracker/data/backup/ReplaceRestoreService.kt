package com.subscriptiontracker.data.backup

import com.subscriptiontracker.data.database.AtomicRecordReplacement
import java.io.InputStream

class ReplaceRestoreService(
    private val codec: BackupCodec,
    private val replacement: AtomicRecordReplacement,
) {
    suspend fun restore(input: InputStream, confirmed: Boolean): RestoreResult = restore(codec.read(input), confirmed)

    suspend fun restore(encoded: String, confirmed: Boolean): RestoreResult = restore(codec.decode(encoded), confirmed)

    private suspend fun restore(decoded: BackupDecodeResult, confirmed: Boolean): RestoreResult = when (decoded) {
        is BackupDecodeResult.Failure -> RestoreResult.Failure(decoded.errors)
        is BackupDecodeResult.Success -> {
            if (!confirmed) {
                RestoreResult.Failure(
                    listOf(
                        BackupValidationError(
                            BackupErrorCode.CONFIRMATION_REQUIRED,
                            "confirmation",
                            "Replace restore requires explicit confirmation",
                        ),
                    ),
                )
            } else {
                val content = decoded.backup.content
                replacement.replaceAll(content.subscriptions, content.events, content.quotas)
                RestoreResult.Success(decoded.backup)
            }
        }
    }
}
