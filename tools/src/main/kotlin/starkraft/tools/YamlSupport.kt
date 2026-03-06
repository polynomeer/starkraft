package starkraft.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.nio.file.Files
import java.nio.file.Path

internal fun readStructuredElement(path: Path): JsonElement {
    val raw = Files.readString(path)
    val lower = path.fileName.toString().lowercase()
    if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
        val value = Load(LoadSettings.builder().build()).loadFromString(raw)
        return toJsonElement(value)
    }
    return kotlinx.serialization.json.Json.parseToJsonElement(raw)
}

private fun toJsonElement(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> ->
            JsonObject(
                buildMap {
                    for ((k, v) in value) {
                        if (k == null) continue
                        put(k.toString(), toJsonElement(v))
                    }
                }
            )
        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }
