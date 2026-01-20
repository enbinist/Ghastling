package com.foenichs.ghastling.discord

import com.foenichs.ghastling.tags.TagListener
import dev.minn.jda.ktx.jdabuilder.light
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.LoggerFactory

class App(private val token: String) {

    private val allowedGuilds = setOf(
        1044265134253670400L, // flame
        1423948607651971117L // test
    )

    fun start() {
        val jda = light(token, enableCoroutines = true) {
            enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
        }

        jda.awaitReady()
        registerCommands(jda)
        TagListener.register(jda)
    }

    private fun registerCommands(jda: JDA) {
        val commandData = Commands.slash("tags", "Manage tags for this server.")
            .addSubcommands(
                SubcommandData("manage", "Manage and create tags.")
                    .addOption(OptionType.STRING, "name", "The tag keyword.", true, true)
                    .addOption(OptionType.BOOLEAN, "remove", "Permanently delete this tag.", false),
                SubcommandData("show", "Find and display tags.")
            )
        for (id in allowedGuilds) {
            jda.getGuildById(id)?.upsertCommand(commandData)?.queue()
        }
    }
}