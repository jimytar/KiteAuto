package com.jimytar.kiteauto.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Service(val name: String, val url: String)

/** User-editable list of streaming sites, kept as JSON in SharedPreferences. */
class Services(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("services", Context.MODE_PRIVATE)

    fun load(): List<Service> {
        val json = prefs.getString(KEY, null) ?: return DEFAULTS
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                arr.getJSONObject(i).let { Service(it.getString("name"), it.getString("url")) }
            }.ifEmpty { DEFAULTS }
        } catch (e: org.json.JSONException) {
            DEFAULTS
        }
    }

    fun save(list: List<Service>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "list"
        val DEFAULTS = listOf(Service("YouTube", "https://m.youtube.com"))

        /** Turns typed text into a URL: web addresses as-is, anything else becomes a YouTube search. */
        fun toUrl(input: String): String {
            val text = input.trim()
            if (text.startsWith("http://") || text.startsWith("https://")) return text
            if (!text.contains(' ') && text.contains('.')) return "https://$text"
            return "https://m.youtube.com/results?search_query=" + android.net.Uri.encode(text)
        }
    }
}
