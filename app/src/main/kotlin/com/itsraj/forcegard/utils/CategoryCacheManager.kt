package com.itsraj.forcegard.utils

import android.content.Context
import android.content.SharedPreferences

enum class CategorySource {
    SYSTEM,
    INTERNET
}

data class CachedCategory(
    val category: AppCategory,
    val source: CategorySource
)

class CategoryCacheManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "AppCategoryCache"
        private const val DELIMITER = "|"
    }

    fun getCachedCategory(packageName: String): CachedCategory? {
        val cachedValue = prefs.getString(packageName, null) ?: return null
        val parts = cachedValue.split(DELIMITER)
        if (parts.size != 2) {
            // Invalid format, clear it
            prefs.edit().remove(packageName).apply()
            return null
        }

        return try {
            val category = AppCategory.valueOf(parts[0])
            val source = CategorySource.valueOf(parts[1])
            CachedCategory(category, source)
        } catch (e: Exception) {
            // Probably enum mismatch after upgrade or corruption
            prefs.edit().remove(packageName).apply()
            null
        }
    }

    fun saveCategory(packageName: String, category: AppCategory, source: CategorySource) {
        val value = "${category.name}$DELIMITER${source.name}"
        prefs.edit().putString(packageName, value).apply()
    }
}
