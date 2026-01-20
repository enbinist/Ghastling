package com.foenichs.ghastling

import com.foenichs.ghastling.config.AppConfig
import com.foenichs.ghastling.db.DatabaseFactory
import com.foenichs.ghastling.discord.App

fun main() {
    // Load Config
    val config = AppConfig.load()

    // Initialize Database
    DatabaseFactory.init(config)

    // Start the Bot
    App(config.discordToken).start()
}