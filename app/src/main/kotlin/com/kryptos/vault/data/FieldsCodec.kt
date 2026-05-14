package com.kryptos.vault.data

import org.json.JSONArray
import org.json.JSONObject

/** Minimal JSON codec for ordered field maps — avoids a serialization dep. */
object FieldsCodec {
    fun encode(fields: List<Pair<String, String>>): String {
        val arr = JSONArray()
        fields.forEach { (k, v) ->
            arr.put(JSONObject().put("k", k).put("v", v))
        }
        return arr.toString()
    }

    fun decode(json: String): List<Pair<String, String>> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            o.optString("k") to o.optString("v")
        }
    }
}
