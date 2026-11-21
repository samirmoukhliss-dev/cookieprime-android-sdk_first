// FILE: cookieprime-android-sdk/src/main/java/com/cookieprime/consent/ui/ConsentActivity.kt
package com.cookieprime.consent.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookieprime.consent.ConsentSDK
import com.cookieprime.consent.configuration.CategoryConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope

class ConsentActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "ConsentActivity"
        private const val ARG_APP_ID = "app_id"
        private const val ARG_FORCE_SHOW = "force_show"
        
        fun createIntent(activity: android.app.Activity, appId: String, forceShow: Boolean = false): Intent {
            return Intent(activity, ConsentActivity::class.java).apply {
                putExtra(ARG_APP_ID, appId)
                putExtra(ARG_FORCE_SHOW, forceShow)
            }
        }
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var acceptAllButton: Button
    private lateinit var saveButton: Button
    private lateinit var rejectAllButton: Button
    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView
    
    private val sdk: ConsentSDK?
        get() = try {
            ConsentSDK.getInstanceOrNull()
        } catch (e: Exception) {
            null
        }
    
    private val adapter by lazy { ConsentCategoryAdapter(emptyList()) }
    private val selectedCategories = mutableMapOf<String, Boolean>()
    private var allCategories: List<CategoryConfig> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== ConsentActivity.onCreate() STARTED ===")
        
        createProgrammaticLayout()
        
        setupRecyclerView()
        setupButtonListeners()
        loadCategories()
        
        Log.d(TAG, "=== ConsentActivity.onCreate() COMPLETED ===")
    }
    
    private fun createProgrammaticLayout() {
        Log.d(TAG, "Creating programmatic layout")
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        titleView = TextView(this).apply {
            text = "Privacy Choices"
            textSize = 20f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        
        descriptionView = TextView(this).apply {
            text = "We respect your privacy. Choose which data processing activities you allow."
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
        
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ConsentActivity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { bottomMargin = 24 }
        }
        
        acceptAllButton = Button(this).apply {
            text = "Accept All"
            setBackgroundColor(0xFF6200EE.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        
        saveButton = Button(this).apply {
            text = "Save Selection"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        
        rejectAllButton = Button(this).apply {
            text = "Reject All"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        layout.addView(titleView)
        layout.addView(descriptionView)
        layout.addView(recyclerView)
        layout.addView(acceptAllButton)
        layout.addView(saveButton)
        layout.addView(rejectAllButton)
        
        setContentView(layout)
    }
    
    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        adapter.onToggleChange = { categoryId, isChecked ->
            Log.d(TAG, "Category $categoryId toggled: $isChecked")
            selectedCategories[categoryId] = isChecked
            updateSaveButton()
        }
    }
    
    private fun setupButtonListeners() {
        acceptAllButton.setOnClickListener {
            Log.d(TAG, "Accept All button clicked!")
            onAcceptAllClicked()
        }
        
        saveButton.setOnClickListener {
            Log.d(TAG, "Save button clicked!")
            onSaveClicked()
        }
        
        rejectAllButton.setOnClickListener {
            Log.d(TAG, "Reject All button clicked!")
            onRejectAllClicked()
        }
    }
    
    private fun loadCategories() {
        Log.d(TAG, "Loading categories")
        val sdk = this.sdk ?: run {
            Log.e(TAG, "SDK is null!")
            return
        }
        
        val recommended = sdk.getRecommendedConsentCategories()
        Log.d(TAG, "Recommended categories: $recommended")
        
        allCategories = recommended.map { (id, label) ->
            CategoryConfig(
                id = id,
                label = label,
                description = getDescriptionForCategory(id),
                required = id == "essential",
                default = getDefaultForCategory(id)
            )
        }
        
        Log.d(TAG, "Created ${allCategories.size} category configs")
        adapter.updateCategories(allCategories)
        
        allCategories.forEach { category ->
            selectedCategories[category.id] = category.default
        }
        
        updateSaveButton()
    }
    
    private fun getDescriptionForCategory(category: String): String {
        return when (category) {
            "analytics" -> "Help us improve by collecting usage data and crash reports"
            "advertising" -> "Show you relevant advertisements based on your interests"
            "personalization" -> "Customize your experience with personalized content"
            "location" -> "Provide location-based features and services"
            "social" -> "Enable social sharing and interactions"
            "performance" -> "Monitor app performance and detect issues"
            else -> "Required for basic app functionality"
        }
    }
    
    private fun getDefaultForCategory(category: String): Boolean {
        return when (category) {
            "analytics" -> true
            "advertising" -> false
            "personalization" -> true
            "location" -> false
            "social" -> false
            "performance" -> true
            else -> true
        }
    }
    
    private fun onAcceptAllClicked() {
        Log.d(TAG, "onAcceptAllClicked()")
        allCategories.forEach { category ->
            if (!category.required) {
                selectedCategories[category.id] = true
                adapter.updateCategoryState(category.id, true)
            }
        }
        updateSaveButton()
    }
    
    private fun onRejectAllClicked() {
        Log.d(TAG, "onRejectAllClicked()")
        allCategories.forEach { category ->
            if (!category.required) {
                selectedCategories[category.id] = false
                adapter.updateCategoryState(category.id, false)
            }
        }
        updateSaveButton()
    }
    
    private fun onSaveClicked() {
        Log.d(TAG, "onSaveClicked() - Saving: $selectedCategories")
        if (selectedCategories.isEmpty()) {
            Log.w(TAG, "No categories selected, cannot save")
            return
        }
        
        // Disable buttons to prevent double-click
        saveButton.isEnabled = false
        acceptAllButton.isEnabled = false
        rejectAllButton.isEnabled = false
        
        // Use SDK's scope instead of lifecycleScope to prevent cancellation
        sdk?.scope?.launch {
            try {
                sdk?.saveConsent(selectedCategories)
                Log.d(TAG, "Consent saved successfully")
                
                val resultIntent = Intent().apply {
                    putExtra("consent_data", HashMap(selectedCategories))
                    putExtra("proof_timestamp", System.currentTimeMillis())
                    putExtra("categories_count", selectedCategories.size)
                }
                
                setResult(android.app.Activity.RESULT_OK, resultIntent)
                
                // Small delay to ensure upload starts
                kotlinx.coroutines.delay(100)
                
                withContext(Dispatchers.Main) {
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save consent", e)
                setResult(android.app.Activity.RESULT_CANCELED)
                
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }
    
    private fun updateSaveButton() {
        saveButton.isEnabled = selectedCategories.isNotEmpty()
        Log.d(TAG, "Save button enabled: ${saveButton.isEnabled}")
    }
    
    override fun onBackPressed() {
        Log.d(TAG, "Back button pressed - consent cancelled")
        setResult(android.app.Activity.RESULT_CANCELED)
        super.onBackPressed()
    }
}