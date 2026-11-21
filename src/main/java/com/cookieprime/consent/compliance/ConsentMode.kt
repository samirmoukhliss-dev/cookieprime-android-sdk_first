package com.cookieprime.consent.compliance

enum class ConsentMode {
    ALL_DENIED,    // Was REJECT_ALL
    ALL_GRANTED,   // Was ACCEPT_ALL
    CUSTOM;
    
    fun toConsentMap(): Map<String, Boolean> {
        return when (this) {
            ALL_DENIED -> mapOf(
                "analytics" to false,
                "advertising" to false,
                "personalization" to false,
                "location" to false,
                "social" to false
            )
            ALL_GRANTED -> mapOf(
                "analytics" to true,
                "advertising" to true,
                "personalization" to true,
                "location" to true,
                "social" to true
            )
            CUSTOM -> emptyMap()
        }
    }
    
    companion object {
        fun fromString(mode: String): ConsentMode {
            return when (mode.lowercase()) {
                "accept_all", "all_granted" -> ALL_GRANTED
                "reject_all", "all_denied" -> ALL_DENIED
                else -> CUSTOM
            }
        }
    }
}