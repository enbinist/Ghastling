package com.foenichs.ghastling.config

data class AppConfig(
    val discordToken: String,
    val db: DbConfig
) {
    companion object {
        fun load(): AppConfig {
            return AppConfig(
                discordToken = Config.get("DISCORD_TOKEN"),
                db = DbConfig.fromConfig()
            )
        }
    }
}