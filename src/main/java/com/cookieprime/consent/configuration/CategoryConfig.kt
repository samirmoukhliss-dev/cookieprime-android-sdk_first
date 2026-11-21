package com.cookieprime.consent.configuration

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CategoryConfig(
    val id: String,
    val label: String,
    val description: String,
    val required: Boolean,
    val default: Boolean
) : Parcelable {  // Changed from Serializable to Parcelable
    
    companion object {
        fun createDefaultCategories(): List<CategoryConfig> {
            return listOf(
                CategoryConfig(
                    id = "analytics",
                    label = "Analytics & Performance",
                    description = "Help us improve the app by collecting usage data and crash reports",
                    required = false,
                    default = true
                ),
                CategoryConfig(
                    id = "advertising",
                    label = "Personalized Advertising",
                    description = "Show you relevant advertisements based on your interests",
                    required = false,
                    default = false
                ),
                CategoryConfig(
                    id = "personalization",
                    label = "Personalized Content",
                    description = "Customize your experience with personalized content",
                    required = false,
                    default = true
                ),
                CategoryConfig(
                    id = "location",
                    label = "Location Services",
                    description = "Provide location-based features and services",
                    required = false,
                    default = false
                ),
                CategoryConfig(
                    id = "essential",
                    label = "Essential",
                    description = "Required for basic app functionality",
                    required = true,
                    default = true
                )
            )
        }
    }
}