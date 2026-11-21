package com.cookieprime.consent.models

import com.cookieprime.consent.LicenseState

data class SdkLicenseInfo(
    val state: LicenseState,
    val isTrial: Boolean,
    val trialDaysLeft: Int,
    val isValid: Boolean,
    val errorMessage: String?
)