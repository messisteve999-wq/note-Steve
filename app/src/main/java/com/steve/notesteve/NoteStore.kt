package com.steve.notesteve

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Note(val title: String, val body: String)

object NoteStore {
    private const val PREF = "steve_notes"
    private const val KEY = "notes"

    fun load(context: Context): MutableList<Note> {
        val raw = context.getSharedPreferences(PREF, 0)
            .getString(KEY, "[]") ?: "[]"
        val result = mutableListOf<Note>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(Note(item.optString("title"), item.optString("body")))
            }
        } catch (_: Exception) {
        }
        if (result.isEmpty()) result.add(Note("Ma première note", "Bienvenue dans Note - Steve !"))
        return result
    }

    fun save(context: Context, notes: List<Note>) {
        val array = JSONArray()
        notes.forEach {
            array.put(JSONObject().put("title", it.title).put("body", it.body))
        }
        context.getSharedPreferences(PREF, 0).edit()
            .putString(KEY, array.toString()).apply()
    }

    fun exportJson(context: Context): String {
        val array = JSONArray()
        load(context).forEach {
            array.put(JSONObject().put("title", it.title).put("body", it.body))
        }
        return array.toString(2)
    }

    fun importJson(context: Context, raw: String): Boolean {
        return try {
            val array = JSONArray(raw)
            val notes = mutableListOf<Note>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                notes.add(Note(item.optString("title"), item.optString("body")))
            }
            if (notes.isEmpty()) return false
            save(context, notes)
            true
        } catch (_: Exception) {
            false
        }
    }
}
