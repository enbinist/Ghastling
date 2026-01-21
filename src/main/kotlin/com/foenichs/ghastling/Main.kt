package com.foenichs.ghastling

import com.foenichs.ghastling.config.AppConfig
import com.foenichs.ghastling.db.DatabaseFactory
import com.foenichs.ghastling.discord.App
import com.foenichs.ghastling.tags.TagService

fun main() {
    // Load Config
    val config = AppConfig.load()
    TagService.init(config)

    // Initialize Database
    DatabaseFactory.init(config)

    // Start the Bot
    App(config).start()
}