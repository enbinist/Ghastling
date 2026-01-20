package com.foenichs.ghastling.config

import java.nio.file.Files
import java.nio.file.Path

object Dotenv {
    fun load(path: Path = Path.of(".env")): Map<String, String> {
        if (!Files.exists(path)) return emptyMap()

        val map = HashMap<String, String>()
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            val idx = line.indexOf('=')
            if (idx <= 0) return@forEach

            val key = line.take(idx).trim()
            var value = line.substring(idx + 1).trim()

            value = unquote(value)
            map[key] = value
        }
        return map
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length - 1)
            }
        }
        return value
    }
}