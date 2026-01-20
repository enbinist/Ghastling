package com.foenichs.ghastling.tags

import com.foenichs.ghastling.db.Guilds
import com.foenichs.ghastling.db.Tags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.interactions.commands.Command
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

enum class TagStyle { Accent, NoAccent, Raw }

data class Tag(
    val guildId: Long,
    val primary: String,
    val keywords: String,
    val content: String,
    val style: TagStyle,
    val usages: Long
) {
    val keywordList: List<String> by lazy { keywords.split(",").map { it.trim() } }

    val cachedContainer: Container? by lazy {
        if (style == TagStyle.Raw) return@lazy null

        val text = TextDisplay.of(content)
        val container = Container.of(text)

        if (style == TagStyle.Accent) {
            container.withAccentColor(Color(0xB5C8B4))
        } else {
            container.withAccentColor(null as Color?)
        }
    }
}

object TagService {
    private val logger = LoggerFactory.getLogger(TagService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    // Cache: GuildID -> { Keyword -> Tag }
    private val cache = ConcurrentHashMap<Long, Map<String, Tag>>()
    private val cooldowns = ConcurrentHashMap<String, Long>()

    private const val COOLDOWN_MS = 2500L
    const val MAX_CONTENT_LEN = 2000
    const val MAX_KEYWORD_LEN = 100
    const val MAX_KEYWORDS_TOTAL_LEN = 200

    /**
     * Checks if a specific channel and tag combination is on cooldown, returning remaining time in ms.
     */
    fun checkCooldown(channelId: Long, primary: String): Long {
        val key = "${channelId}_$primary"
        val now = System.currentTimeMillis()
        val last = cooldowns[key] ?: 0L
        val diff = now - last

        if (diff < COOLDOWN_MS) {
            return COOLDOWN_MS - diff
        }

        cooldowns[key] = now
        return 0L
    }

    /**
     * Retrieves a tag object by keyword for a specific guild, if it exists.
     */
    fun find(guildId: Long, keyword: String): Tag? {
        return getGuildCache(guildId)[keyword.trim().lowercase()]
    }

    /**
     * Generates autocomplete choices for a search query, matching against both primary keys and aliases.
     */
    fun autocomplete(guildId: Long, query: String): List<Command.Choice> {
        val distinctTags = getGuildCache(guildId).values.distinctBy { it.primary }
        val needle = query.trim().lowercase()
        return distinctTags.asSequence()
            .filter { it.primary.lowercase().contains(needle) || it.keywords.lowercase().contains(needle) }
            .take(25)
            .map { Command.Choice(it.keywords.take(100), it.primary) }
            .toList()
    }

    /**
     * Retrieves all unique tag objects associated with a specific guild.
     */
    fun listTags(guildId: Long): List<Tag> {
        return getGuildCache(guildId).values.distinctBy { it.primary }
    }

    /**
     * Checks if any of the provided new keywords conflict with existing tags in the guild.
     */
    fun findConflicts(guildId: Long, newKeywords: String, ignorePrimary: String? = null): Map<String, String> {
        val map = getGuildCache(guildId)
        val inputList = newKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val conflicts = mutableMapOf<String, String>()

        for (kw in inputList) {
            val owner = map[kw]
            if (owner != null && (ignorePrimary == null || owner.primary != ignorePrimary)) {
                conflicts[kw] = owner.primary
            }
        }
        return conflicts
    }

    /**
     * Creates or updates a tag in the database and invalidates the cache.
     */
    fun createOrUpdate(guildId: Long, rawKeywords: String, content: String, style: TagStyle, oldPrimary: String? = null): String {
        if (content.length > MAX_CONTENT_LEN) throw IllegalArgumentException("Content too long (${content.length} > $MAX_CONTENT_LEN)")

        val cleanKw = rawKeywords.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_KEYWORD_LEN }
            .distinct()
            .joinToString(", ")

        if (cleanKw.isBlank()) throw IllegalArgumentException("No valid keywords provided.")
        if (cleanKw.length > MAX_KEYWORDS_TOTAL_LEN) throw IllegalArgumentException("Total keywords length exceeds limit ($MAX_KEYWORDS_TOTAL_LEN).")

        val primary = cleanKw.split(",").first().trim()

        transaction {
            if (Guilds.selectAll().where { Guilds.id eq guildId }.empty()) {
                Guilds.insertIgnore { it[id] = guildId }
            }

            if (oldPrimary != null && oldPrimary != primary) {
                Tags.deleteWhere { (Tags.guildId eq guildId) and (Tags.primaryKeyword eq oldPrimary) }
            }

            Tags.upsert {
                it[Tags.guildId] = guildId
                it[Tags.primaryKeyword] = primary
                it[Tags.keywords] = cleanKw
                it[Tags.content] = content
                it[Tags.style] = style.name
            }
        }

        cache.remove(guildId)
        logger.info("Guild $guildId: Tag upserted '$primary' (old: '$oldPrimary')")
        return primary
    }

    /**
     * Deletes a tag from the database and invalidates the cache.
     */
    fun delete(guildId: Long, primary: String): Boolean {
        val deleted = transaction {
            Tags.deleteWhere { (Tags.guildId eq guildId) and (Tags.primaryKeyword eq primary) } > 0
        }
        if (deleted) {
            cache.remove(guildId)
            logger.info("Guild $guildId: Tag deleted '$primary'")
        }
        return deleted
    }

    /**
     * Asynchronously increments the usage counter for a specific tag.
     */
    fun incrementUsage(guildId: Long, primary: String) {
        scope.launch {
            try {
                transaction {
                    Tags.update({ (Tags.guildId eq guildId) and (Tags.primaryKeyword eq primary) }) {
                        with(SqlExpressionBuilder) { it.update(usages, usages + 1) }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to increment usage: ${e.message}")
            }
        }
    }

    /**
     * Fetches or constructs the keyword-to-tag map for a guild from the database.
     */
    private fun getGuildCache(guildId: Long): Map<String, Tag> {
        return cache.computeIfAbsent(guildId) { id ->
            val newMap = HashMap<String, Tag>()
            transaction {
                val rows = Tags.selectAll().where { Tags.guildId eq id }
                for (row in rows) {
                    val tag = rowToTag(row)
                    for (k in tag.keywordList) {
                        newMap[k.lowercase()] = tag
                    }
                }
            }
            newMap
        }
    }

    /**
     * Maps a database result row to a Tag domain object.
     */
    private fun rowToTag(row: ResultRow) = Tag(
        guildId = row[Tags.guildId],
        primary = row[Tags.primaryKeyword],
        keywords = row[Tags.keywords],
        content = row[Tags.content],
        style = runCatching { TagStyle.valueOf(row[Tags.style]) }.getOrDefault(TagStyle.Accent),
        usages = row[Tags.usages]
    )
}