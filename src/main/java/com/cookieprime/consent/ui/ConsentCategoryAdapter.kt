package com.cookieprime.consent.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Switch  // This is correct
import androidx.recyclerview.widget.RecyclerView
import com.cookieprime.consent.configuration.CategoryConfig
import android.util.Log
import android.widget.LinearLayout

class ConsentCategoryAdapter(private var categories: List<CategoryConfig>) : 
    RecyclerView.Adapter<ConsentCategoryAdapter.ViewHolder>() {
    
    companion object {
        private const val TAG = "ConsentCategoryAdapter"
        
        // Define our own IDs to avoid R class issues
        const val ID_CATEGORY_TITLE = 10001
        const val ID_CATEGORY_DESCRIPTION = 10002
        const val ID_CATEGORY_SWITCH = 10003
    }
    
    var onToggleChange: ((String, Boolean) -> Unit)? = null
    private val categoryStates = mutableMapOf<String, Boolean>()
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(ID_CATEGORY_TITLE)
        val description: TextView = itemView.findViewById(ID_CATEGORY_DESCRIPTION)
        val switch: Switch = itemView.findViewById(ID_CATEGORY_SWITCH)  // Changed to Switch
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        
        // Create main layout
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 16, 50, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
        
        // Create horizontal layout
        val horizontalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Create title
        val title = TextView(context).apply {
            id = ID_CATEGORY_TITLE
            text = "Category"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        // Create switch - Changed to Switch
        val switch = Switch(context).apply {  // Changed to Switch
            id = ID_CATEGORY_SWITCH
        }
        
        // Create description
        val description = TextView(context).apply {
            id = ID_CATEGORY_DESCRIPTION
            text = "Description"
            textSize = 14f
            setTextColor(0xFF757575.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
        }
        
        // Build the view
        horizontalLayout.addView(title)
        horizontalLayout.addView(switch)
        mainLayout.addView(horizontalLayout)
        mainLayout.addView(description)
        
        return ViewHolder(mainLayout)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        
        holder.title.text = category.label
        holder.description.text = category.description
        
        val isChecked = categoryStates[category.id] ?: category.default
        holder.switch.isChecked = isChecked
        holder.switch.isEnabled = !category.required
        
        holder.switch.setOnCheckedChangeListener { _, isChecked ->
            categoryStates[category.id] = isChecked
            onToggleChange?.invoke(category.id, isChecked)
        }
    }
    
    override fun getItemCount(): Int = categories.size
    
    fun updateCategories(newCategories: List<CategoryConfig>) {
        categories = newCategories
        categoryStates.clear()
        categories.forEach { category ->
            categoryStates[category.id] = category.default
        }
        notifyDataSetChanged()
    }
    
    fun updateCategoryState(categoryId: String, isChecked: Boolean) {
        categoryStates[categoryId] = isChecked
        val position = categories.indexOfFirst { it.id == categoryId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }
    
    fun getCategoryStates(): Map<String, Boolean> = categoryStates.toMap()
}