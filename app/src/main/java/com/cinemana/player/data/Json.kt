package com.cinemana.player.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The Cinemana API is loosely typed (numbers vs strings, flags as "1"/"0"), so we read JSON defensively. */
fun JsonElement?.asStringOrNull(): String? = when (this) {
    null -> null
    is JsonNull -> null
    is JsonPrimitive -> content
    else -> null
}

fun JsonObject.str(key: String): String? = this[key].asStringOrNull()?.takeIf { it.isNotBlank() }

/** "1" / 1 / true -> true (port of truthyFlag). */
fun JsonElement?.isTruthyFlag(): Boolean {
    val p = this as? JsonPrimitive ?: return false
    if (p is JsonNull) return false
    return p.content == "1" || p.content.equals("true", ignoreCase = true)
}

fun String?.toIntLoose(): Int? {
    val s = this?.trim() ?: return null
    return s.toIntOrNull() ?: s.toDoubleOrNull()?.toInt()
}

/** Port of parseResponseItems: array, or an object wrapping an array, or a single object. */
fun parseResponseItems(data: JsonElement): List<JsonElement> = when (data) {
    is JsonArray -> data.toList()
    is JsonObject -> {
        val key = listOf("data", "results", "items", "videos", "episodes").firstOrNull { data[it] is JsonArray }
        if (key != null) (data[key] as JsonArray).toList() else listOf(data)
    }
    else -> emptyList()
}
