package com.example.offlinepos.ui

import android.content.Context
import org.json.JSONArray

object RecentSearchHelper {
    private const val PREFS_NAME = "recent_searches_prefs"
    private const val KEY_SEARCHES = "recent_searches_list"

    fun getRecentSearches(context: Context): List<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SEARCHES, null) ?: return emptyList()
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                if (item.isNotBlank()) {
                    list.add(item)
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSearchTerm(context: Context, term: String): List<String> {
        val clean = term.trim()
        if (clean.isBlank()) return getRecentSearches(context)
        val current = getRecentSearches(context).toMutableList()
        current.removeAll { it.equals(clean, ignoreCase = true) }
        current.add(0, clean)
        val trimmed = current.take(5)
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray(trimmed)
            prefs.edit().putString(KEY_SEARCHES, arr.toString()).apply()
        } catch (_: Exception) {}
        return trimmed
    }

    fun clearSearches(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_SEARCHES).apply()
        } catch (_: Exception) {}
    }
}
