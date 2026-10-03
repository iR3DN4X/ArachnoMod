package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.platform.canEditSpiderConfig
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * THE SPIDER CONFIG BOOK - every setting on one in-game screen, with its description, its default,
 * and a reset button, instead of a wall of /spider config commands.
 *
 * The settings live on the SERVER (a dedicated server's config is not the player's), so the
 * screen is fed by it: opening the book sends a snapshot of every setting, and each edit goes
 * back as a small request that is checked, applied through the very same parsing and saving as
 * /spider config ([Config.bookSet]), and answered with a fresh snapshot. Operators and the owner
 * of a single-player world may change things; everyone else gets a read-only view.
 *
 * Both directions are a single string, so each loader's network layer ([ConfigBookNet]) is only
 * a few lines. This file never touches a client class: the screen lives in ConfigBookScreen.
 */
object ConfigBook {
    private const val FORMAT = "ACB1"
    private const val US = '\u001F'   // between fields
    private const val RS = '\u001E'   // between rows

    /** Longest snapshot a packet may carry, in characters (it is about 20k today). */
    const val MAX_LENGTH = 262144

    /** Marks a player who has already been handed a book, so they only ever get one. */
    const val GIVEN_TAG = "arachnomod_config_book"

    /** The book item - a plain Item on Fabric, a registry holder on NeoForge/Forge (set at init). */
    var bookItem: () -> Item = { throw IllegalStateException("config book item not registered") }

    class Row(
        val path: String, val kind: Char, val value: String, val default: String,
        val min: String, val max: String, val group: String, val description: String,
    )

    /** What the screen shows. [open] = show the screen; false = just refresh one already open. */
    class Snapshot(val open: Boolean, val canEdit: Boolean, val status: String, val rows: List<Row>)

    // ---- the wire format --------------------------------------------------------------------

    fun encode(s: Snapshot): String {
        val sb = StringBuilder()
        sb.append(FORMAT).append(US).append(if (s.open) '1' else '0').append(US)
            .append(if (s.canEdit) '1' else '0').append(US).append(clean(s.status))
        for (r in s.rows) {
            sb.append(RS)
            listOf(r.path, r.kind.toString(), r.value, r.default, r.min, r.max, r.group, r.description)
                .joinTo(sb, US.toString()) { clean(it) }
        }
        return sb.toString()
    }

    fun decode(data: String): Snapshot? {
        val records = data.split(RS)
        val head = records[0].split(US)
        if (head.size < 4 || head[0] != FORMAT) return null
        val rows = records.drop(1).mapNotNull { rec ->
            val f = rec.split(US)
            if (f.size < 8 || f[1].isEmpty()) null
            else Row(f[0], f[1][0], f[2], f[3], f[4], f[5], f[6], f[7])
        }
        return Snapshot(head[1] == "1", head[2] == "1", head[3], rows)
    }

    private fun clean(text: String) = text.replace(US, ' ').replace(RS, ' ')

    // Requests the screen sends back.
    fun setRequest(path: String, value: String) = "set$US$path$US${clean(value)}"
    fun resetRequest(path: String) = "reset$US$path"
    const val RESET_ALL_REQUEST = "resetall"
    const val REFRESH_REQUEST = "refresh"

    // ---- the server side --------------------------------------------------------------------

    fun snapshot(player: ServerPlayer, open: Boolean, status: String): Snapshot =
        Snapshot(open, player.canEditSpiderConfig(), status, Config.bookRows().map {
            Row(it.path, it.kind, it.value, it.default, it.min, it.max, it.group, it.description)
        })

    /** Show [player] the settings screen (the book's right-click, and /spider menu). */
    fun open(player: ServerPlayer) {
        ConfigBookNet.sendToPlayer(player, encode(snapshot(player, open = true, status = "")))
    }

    /** A request from the screen: apply it if the player may, and answer with a fresh snapshot. */
    fun handle(player: ServerPlayer, request: String) {
        ConfigBookNet.sendToPlayer(player, encode(snapshot(player, open = false, status = apply(player, request))))
    }

    /** The status line the screen shows afterwards ("" for nothing to say). */
    internal fun apply(player: ServerPlayer, request: String): String {
        val f = request.split(US)
        if (f[0] == REFRESH_REQUEST) return ""
        if (!player.canEditSpiderConfig()) return "Only operators can change these settings."
        return when {
            f[0] == "set" && f.size >= 3 -> {
                val error = Config.bookSet(f[1], f[2])
                if (error != null) "Not changed: $error"
                else {
                    SpiderSpawnManager.onConfigSet(f[1])
                    "Saved: ${f[1]} = ${Config.bookRows().firstOrNull { it.path == f[1] }?.value}"
                }
            }
            f[0] == "reset" && f.size >= 2 ->
                if (Config.bookReset(f[1])) { SpiderSpawnManager.onConfigSet(f[1]); "Reset ${f[1]} to its default." }
                else "Could not reset ${f[1]}."
            f[0] == RESET_ALL_REQUEST -> {
                var count = 0
                for (row in Config.bookRows()) {
                    if (row.value == row.default) continue
                    if (Config.bookReset(row.path)) { SpiderSpawnManager.onConfigSet(row.path); count++ }
                }
                if (count == 0) "Everything was already at its default." else "Reset $count setting(s) to their defaults."
            }
            else -> "Unknown request."
        }
    }

    /**
     * Hand a book to someone joining for the first time, if they may use it and the server wants
     * it (giveConfigBook). The tag makes it once per player per world, ever - a lost book is not
     * replaced on the next join; /spider menu and the creative tab still reach the screen.
     */
    fun onJoin(player: ServerPlayer) {
        if (!Config.GIVE_CONFIG_BOOK.get()) return
        if (!player.canEditSpiderConfig()) return
        if (!player.addTag(GIVEN_TAG)) return   // false: they already had it
        val stack = ItemStack(bookItem())
        if (!player.addItem(stack)) player.drop(stack, false)
    }
}
