// FILE: cookieprime-android-sdk/src/main/java/com/cookieprime/consent/license/LicenseManager.kt
package com.cookieprime.consent.license

object LicenseManager {
    private var _isLicenseValid: Boolean = false
    private var _licenseError: String? = null
    
    fun setLicenseValid(isValid: Boolean) {
        _isLicenseValid = isValid
    }
    
    fun setLicenseError(error: String?) {
        _licenseError = error
    }
    
    fun isLicenseValid(): Boolean = _isLicenseValid
    
    fun getLicenseError(): String? = _licenseError
    
    fun clear() {
        _isLicenseValid = false
        _licenseError = null
    }
}