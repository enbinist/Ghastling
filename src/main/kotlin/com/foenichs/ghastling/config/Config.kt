package com.foenichs.ghastling.config

object Config {
    private val dotenv: Map<String, String> by lazy { Dotenv.load() }

    fun get(key: String, default: String? = null): String {
        val fromEnv = System.getenv(key)
        if (!fromEnv.isNullOrBlank()) return fromEnv

        val fromDotenv = dotenv[key]
        if (!fromDotenv.isNullOrBlank()) return fromDotenv

        return default ?: error("Missing config key $key (env or .env)")
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        val raw = get(key, default.toString())
        return raw.toBooleanStrictOrNull() ?: default
    }

    fun getInt(key: String, default: Int): Int {
        return get(key, default.toString()).toInt()
    }
}