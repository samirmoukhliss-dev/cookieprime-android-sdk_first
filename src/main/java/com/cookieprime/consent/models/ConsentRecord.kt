package com.cookieprime.consent.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cookieprime.consent.utils.ProofGenerator

@Entity(tableName = "consent_records")
data class ConsentRecord(
    @PrimaryKey val id: String,
    val appId: String,
    val timestamp: Long,
    val actorId: String?,
    val consentJson: String,
    val proofHash: String,
    val platform: String = "android",
    val appVersion: String? = null,
    val sdkVersion: String? = null,
    val deviceId: String? = null,
    val sessionId: String? = null,
    val synced: Boolean = false,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long? = null
) {
    companion object {
        fun create(
            appId: String, 
            consent: Map<String, Boolean>, 
            ts: Long, 
            actorId: String?
        ): ConsentRecord {
            val json = ConsentJsonConverter.toJson(consent)
            val hash = ProofGenerator.hashConsent(json, ts, actorId ?: "anonymous")
            val id = ProofGenerator.generateId(appId, ts, hash)
            
            return ConsentRecord(
                id = id,
                appId = appId,
                timestamp = ts,
                actorId = actorId,
                consentJson = json,
                proofHash = hash
            )
        }
    }
}

object ConsentJsonConverter {
    private val gson = com.google.gson.Gson()
    
    fun toJson(consent: Map<String, Boolean>): String {
        return gson.toJson(consent)
    }
    
    fun fromJson(json: String): Map<String, Boolean> {
        return try {
            gson.fromJson(json, Map::class.java) as Map<String, Boolean>
        } catch (e: Exception) {
            emptyMap()
        }
    }
}