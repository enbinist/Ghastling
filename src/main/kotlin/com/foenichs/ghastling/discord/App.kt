package com.foenichs.ghastling.discord

import com.foenichs.ghastling.config.AppConfig
import com.foenichs.ghastling.tags.TagListener
import dev.minn.jda.ktx.jdabuilder.light
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.LoggerFactory

class App(private val config: AppConfig) {

    private val logger = LoggerFactory.getLogger(App::class.java)

    fun start() {
        val jda = light(config.discordToken, enableCoroutines = true) {
            enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
        }

        jda.awaitReady()
        registerCommands(jda)
        TagListener.register(jda)
    }

    private fun registerCommands(jda: JDA) {
        val commandData = Commands.slash("tags", "Manage tags for this server.")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
            .addSubcommands(
                SubcommandData("manage", "Manage and create tags.")
                    .addOption(OptionType.STRING, "name", "The tag keyword.", true, true)
                    .addOption(OptionType.BOOLEAN, "remove", "Permanently delete this tag.", false),
                SubcommandData("show", "Find and display tags.")
            )

        // Register and unregister slash commands
        for (guild in jda.guilds) {
            if (guild.idLong in config.allowedGuilds) {
                guild.upsertCommand(commandData).queue()
                logger.info("Commands deployed for ${guild.id} (${guild.name})")
            } else {
                guild.updateCommands().queue()
            }
        }
    }
}