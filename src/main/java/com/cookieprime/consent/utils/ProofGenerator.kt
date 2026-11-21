package com.cookieprime.consent.utils

import java.security.MessageDigest
import java.util.*

object ProofGenerator {
    fun hashConsent(json: String, ts: Long, actorId: String): String {
        val input = "${json}|${ts}|${actorId}"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun generateId(appId: String, ts: Long, hash: String): String {
        return "${appId}_${ts}_${hash.take(16)}"
    }
    
    fun validateHash(json: String, ts: Long, actorId: String, hash: String): Boolean {
        val computedHash = hashConsent(json, ts, actorId)
        return computedHash == hash
    }
}
