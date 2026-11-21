package com.cookieprime.consent.storage

import androidx.room.*
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.cookieprime.consent.models.ConsentRecord
import com.cookieprime.consent.models.ConsentStatus
import kotlinx.coroutines.flow.Flow
import android.util.Log
import android.content.SharedPreferences

@Entity(tableName = "consent_records")
data class RoomConsentRecord(
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
    val policyVersion: String = "1.0",
    val actionType: String = "user_selection",
    val synced: Boolean = false,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long? = null
) {
    fun toConsentRecord(): ConsentRecord {
        return ConsentRecord(
            id = id,
            appId = appId,
            timestamp = timestamp,
            actorId = actorId,
            consentJson = consentJson,
            proofHash = proofHash,
            platform = platform,
            appVersion = appVersion,
            sdkVersion = sdkVersion,
            deviceId = deviceId,
            sessionId = sessionId
        )
    }
    
    companion object {
        fun fromConsentRecord(record: ConsentRecord): RoomConsentRecord {
            return RoomConsentRecord(
                id = record.id,
                appId = record.appId,
                timestamp = record.timestamp,
                actorId = record.actorId,
                consentJson = record.consentJson,
                proofHash = record.proofHash,
                platform = record.platform,
                appVersion = record.appVersion,
                sdkVersion = record.sdkVersion,
                deviceId = record.deviceId,
                sessionId = record.sessionId
            )
        }
    }
}

@Entity(tableName = "consent_audit_log")
data class RoomConsentAuditLog(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val recordId: String,
    val timestamp: Long,
    val action: String,
    val details: String? = null,
    val userId: String? = null
) {
    fun toConsentAuditLog(): ConsentAuditLog {
        return ConsentAuditLog(
            logId = logId,
            recordId = recordId,
            timestamp = timestamp,
            action = action,
            details = details,
            userId = userId
        )
    }
}

@Dao
interface ConsentDao {
    @Query("SELECT * FROM consent_records WHERE synced = 0 AND syncAttempts < 3 ORDER BY timestamp ASC LIMIT 50")
    suspend fun getPendingRecords(): List<RoomConsentRecord>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RoomConsentRecord)
    
    @Insert
    suspend fun insertAuditLog(log: RoomConsentAuditLog)
    
    @Query("UPDATE consent_records SET synced = 1 WHERE id = :recordId")
    suspend fun markSynced(recordId: String)
    
    @Query("UPDATE consent_records SET syncAttempts = syncAttempts + 1, lastSyncAttempt = :attemptTime WHERE id = :recordId")
    suspend fun markSyncAttempt(recordId: String, attemptTime: Long)
    
    @Query("SELECT * FROM consent_records WHERE appId = :appId ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentRecords(appId: String): List<RoomConsentRecord>
    
    @Query("SELECT * FROM consent_records WHERE appId = :appId AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getRecordsSince(appId: String, since: Long): List<RoomConsentRecord>
    
    @Query("SELECT COUNT(*) FROM consent_records WHERE synced = 0")
    suspend fun getPendingCount(): Int
    
    @Query("DELETE FROM consent_records WHERE timestamp < :olderThan AND synced = 1")
    suspend fun cleanupOldRecords(olderThan: Long)
    
    @Query("SELECT * FROM consent_audit_log WHERE recordId = :recordId ORDER BY timestamp DESC")
    suspend fun getAuditLogsForRecord(recordId: String): List<RoomConsentAuditLog>
    
    @Query("SELECT * FROM consent_audit_log WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getRecentAuditLogs(since: Long): List<RoomConsentAuditLog>
    
    @Query("DELETE FROM consent_audit_log WHERE timestamp < :olderThan")
    suspend fun cleanupOldAuditLogs(olderThan: Long)
    
    @Query("DELETE FROM consent_records WHERE appId = :appId")
    suspend fun deleteAllForApp(appId: String)
    
    @Query("DELETE FROM consent_audit_log")
    suspend fun deleteAllAuditLogs()
}

@Database(
    entities = [RoomConsentRecord::class, RoomConsentAuditLog::class],
    version = 2,
    exportSchema = true
)
abstract class ConsentDatabase : RoomDatabase() {
    abstract fun consentDao(): ConsentDao
    
    companion object {
        @Volatile
        private var INSTANCE: ConsentDatabase? = null
        
        fun getInstance(context: Context): ConsentDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ConsentDatabase::class.java,
                    "cookieprime_consent.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
        
        private val MIGRATION_1_2 = androidx.room.migration.Migration(1, 2) { database ->
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `consent_audit_log` (
                    `logId` INTEGER PRIMARY KEY AUTOINCREMENT,
                    `recordId` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `action` TEXT NOT NULL,
                    `details` TEXT,
                    `userId` TEXT
                )
                """.trimIndent()
            )
        }
    }
}

class RoomConsentStorage(private val context: Context) : ConsentStorage {
    private val database = ConsentDatabase.getInstance(context)
    private val dao = database.consentDao()
    
    // NO ENCRYPTION - use regular SharedPreferences
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("cookieprime_consent_simple", Context.MODE_PRIVATE)
    }
    
    override suspend fun saveConsentRecord(record: ConsentRecord) {
        try {
            val roomRecord = RoomConsentRecord.fromConsentRecord(record)
            dao.insertRecord(roomRecord)
            
            val auditLog = RoomConsentAuditLog(
                recordId = record.id,
                timestamp = System.currentTimeMillis(),
                action = "created",
                details = "Consent record created",
                userId = record.actorId
            )
            dao.insertAuditLog(auditLog)
            
            val consentMap = ConsentJsonConverter.fromJson(record.consentJson)
            consentMap.forEach { (category, granted) ->
                val status = if (granted) ConsentStatus.GRANTED else ConsentStatus.DENIED
                prefs.edit().putString("consent_$category", status.name).apply()
            }
            
            prefs.edit().putLong("consent_last_seen_ts", record.timestamp).apply()
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to save consent record", e)
            throw e
        }
    }
    
    override suspend fun getPendingRecords(): List<ConsentRecord> {
        return try {
            dao.getPendingRecords().map { it.toConsentRecord() }
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to get pending records", e)
            emptyList()
        }
    }
    
    override suspend fun markRecordSynced(recordId: String) {
        try {
            dao.markSynced(recordId)
            
            val auditLog = RoomConsentAuditLog(
                recordId = recordId,
                timestamp = System.currentTimeMillis(),
                action = "synced",
                details = "Record synced to server"
            )
            dao.insertAuditLog(auditLog)
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to mark record as synced", e)
        }
    }
    
    override suspend fun markSyncAttempt(recordId: String) {
        try {
            dao.markSyncAttempt(recordId, System.currentTimeMillis())
            
            val auditLog = RoomConsentAuditLog(
                recordId = recordId,
                timestamp = System.currentTimeMillis(),
                action = "sync_attempt",
                details = "Sync attempt recorded"
            )
            dao.insertAuditLog(auditLog)
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to mark sync attempt", e)
        }
    }
    
    override fun getConsentStatus(category: String): ConsentStatus {
        return try {
            val value = prefs.getString("consent_$category", null) 
                ?: return ConsentStatus.UNKNOWN
            ConsentStatus.valueOf(value)
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to get consent status for category: $category", e)
            ConsentStatus.UNKNOWN
        }
    }
    
    override fun getLastSeenTimestamp(): Long? {
        return try {
            val value = prefs.getLong("consent_last_seen_ts", -1L)
            if (value == -1L) null else value
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to get last seen timestamp", e)
            null
        }
    }
    
    override suspend fun getPendingCount(): Int {
        return try {
            dao.getPendingCount()
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to get pending count", e)
            0
        }
    }
    
    override suspend fun cleanup() {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
            dao.cleanupOldRecords(thirtyDaysAgo)
            
            val ninetyDaysAgo = System.currentTimeMillis() - (90 * 24 * 60 * 60 * 1000L)
            dao.cleanupOldAuditLogs(ninetyDaysAgo)
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to cleanup old records", e)
        }
    }
    
    override suspend fun getAuditTrail(since: Long): List<ConsentAuditLog> {
        return try {
            dao.getRecentAuditLogs(since).map { it.toConsentAuditLog() }
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to get audit trail", e)
            emptyList()
        }
    }
    
    override suspend fun getConsentHistory(days: Int): List<ConsentRecord> {
        return try {
            val since = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            // Using default appId since we're in SDK context
            dao.getRecordsSince("default_app", since).map { it.toConsentRecord() }
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to get consent history", e)
            emptyList()
        }
    }
    
    override suspend fun clearAllConsent() {
        try {
            val auditLog = RoomConsentAuditLog(
                recordId = "system_clear",
                timestamp = System.currentTimeMillis(),
                action = "clear_all",
                details = "All consent data cleared by system"
            )
            dao.insertAuditLog(auditLog)
            
            prefs.edit().clear().apply()
            
            // Note: We don't delete from Room DB for audit trail purposes
            // but we do mark all records as synced
            dao.getPendingRecords().forEach { record ->
                dao.markSynced(record.id)
            }
        } catch (e: Exception) {
            Log.e("CookiePrime", "Failed to clear all consent", e)
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
            val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Boolean>>() {}.type
            gson.fromJson<Map<String, Boolean>>(json, mapType) ?: emptyMap()
        } catch (e: Exception) {
            Log.e("ConsentJsonConverter", "Failed to parse JSON: $json", e)
            emptyMap()
        }
    }
}