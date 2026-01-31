package com.itsraj.forcegard.utils

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object InternetCategoryLookup {
    private const val TAG = "InternetCategoryLookup"
    private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id="

    fun fetchCategory(packageName: String): AppCategory? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$PLAY_STORE_URL$packageName")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val content = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    content.append(line)
                    // Optimization: stop if we find what we need
                    if (content.contains("itemprop=\"genre\"") || content.contains("category/")) {
                        // Keep reading a bit more to ensure we have the category
                    }
                }
                parseCategoryFromHtml(content.toString())
            } else {
                Log.w(TAG, "Failed to fetch category for $packageName: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching category for $packageName: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseCategoryFromHtml(html: String): AppCategory? {
        // Look for Play Store category in the HTML
        // Example: <a href="/store/apps/category/GAME_ACTION" ...
        val categoryPattern = "/store/apps/category/([^\"? ]+)".toRegex()
        val match = categoryPattern.find(html)
        if (match != null) {
            val rawCategory = match.groupValues[1].uppercase()
            return normalizeCategory(rawCategory)
        }
        return null
    }

    private fun normalizeCategory(rawCategory: String): AppCategory {
        return when {
            rawCategory.contains("GAME") -> AppCategory.GAME
            rawCategory.contains("SOCIAL") || rawCategory.contains("COMMUNICATION") -> AppCategory.SOCIAL
            rawCategory.contains("VIDEO") || rawCategory.contains("MUSIC") || rawCategory.contains("AUDIO") || rawCategory.contains("ENTERTAINMENT") -> AppCategory.VIDEO_MUSIC
            rawCategory.contains("EDUCATION") -> AppCategory.EDUCATION
            rawCategory.contains("PRODUCTIVITY") || rawCategory.contains("TOOLS") || rawCategory.contains("BUSINESS") || rawCategory.contains("FINANCE") -> AppCategory.PRODUCTIVITY
            else -> AppCategory.OTHER
        }
    }
}
