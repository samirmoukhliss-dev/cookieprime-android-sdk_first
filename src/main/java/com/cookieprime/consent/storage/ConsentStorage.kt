package com.cookieprime.consent.storage

import com.cookieprime.consent.models.ConsentRecord
import com.cookieprime.consent.models.ConsentStatus
import kotlinx.coroutines.flow.Flow

interface ConsentStorage {
    suspend fun saveConsentRecord(record: ConsentRecord)
    suspend fun getPendingRecords(): List<ConsentRecord>
    suspend fun markRecordSynced(recordId: String)
    suspend fun markSyncAttempt(recordId: String)
    fun getConsentStatus(category: String): ConsentStatus
    fun getLastSeenTimestamp(): Long?
    suspend fun getPendingCount(): Int
    suspend fun cleanup()
    suspend fun clearAllConsent()
    suspend fun getConsentHistory(days: Int = 30): List<ConsentRecord>
    suspend fun getAuditTrail(since: Long): List<ConsentAuditLog>
}

data class ConsentAuditLog(
    val logId: Long = 0,
    val recordId: String,
    val timestamp: Long,
    val action: String,
    val details: String? = null,
    val userId: String? = null
)
