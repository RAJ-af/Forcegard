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
        if (packageName.isEmpty()) return null

        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$PLAY_STORE_URL$packageName")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000 // Reduced timeout for better responsiveness
            connection.readTimeout = 3000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val content = StringBuilder()
                var line: String?
                var linesRead = 0
                // Play Store pages are large, but category info is usually in the first few hundred lines
                while (reader.readLine().also { line = it } != null && linesRead < 500) {
                    content.append(line)
                    linesRead++
                }
                val html = content.toString()
                if (html.isEmpty()) {
                    Log.w(TAG, "Received empty HTML for $packageName")
                    null
                } else {
                    parseCategoryFromHtml(html)
                }
            } else {
                Log.w(TAG, "Failed to fetch category for $packageName: HTTP $responseCode")
                null
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Timeout fetching category for $packageName")
            null
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Offline or DNS failure for $packageName")
            null
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
