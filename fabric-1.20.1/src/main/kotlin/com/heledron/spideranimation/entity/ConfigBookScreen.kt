package com.heledron.spideranimation.entity

import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

/**
 * CLIENT-ONLY. The settings screen behind the Spider Config Book: every setting, a page at a
 * time, filterable by category and by a search box. Each row is the setting's name (hover it -
 * or its value - for the full description, default and range), its value (an ON/OFF toggle, or
 * a text box with a save tick), and a reset arrow. Settings that differ from their default are
 * named in yellow. "Reset ALL" asks first.
 *
 * PER-VERSION FILE, built only from stock widgets so it draws the same everywhere: 1.20.1 must
 * paint its own background, 26.1.2 renamed the whole render pass (so nothing is overridden there).
 *
 * Nothing is changed locally: every edit goes to the server ([send]) and the screen redraws from
 * the snapshot that comes back, so what you see is always what the server actually saved.
 */
class ConfigBookScreen(
    private var snap: ConfigBook.Snapshot,
    private val send: (String) -> Unit,
) : Screen(Component.literal("ArachnoMod Settings")) {

    companion object {
        private const val ROW_H = 24
        private const val TOP = 46

        /** A snapshot arrived from the server: refresh the open screen, or open one if asked to. */
        fun receive(data: String, send: (String) -> Unit) {
            val snap = ConfigBook.decode(data) ?: return
            val mc = Minecraft.getInstance()
            val open = mc.screen as? ConfigBookScreen
            if (open != null) open.refresh(snap)
            else if (snap.open) mc.setScreen(ConfigBookScreen(snap, send))
        }

        /** "spawnMinMinutes" -> "Spawn Min Minutes". */
        fun pretty(path: String): String =
            path.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
    }

    private var category = 0
    private var page = 0
    private var query = ""
    /** Text typed into a box but not saved yet, by setting. */
    private val pending = HashMap<String, String>()
    /** Everything that is rebuilt when the page, filter or data changes (search box and buttons stay). */
    private val rowWidgets = mutableListOf<AbstractWidget>()

    private fun panelWidth() = min(width - 16, 470)
    private fun left() = (width - panelWidth()) / 2
    private fun bottom() = height - 26
    private fun perPage() = max(1, (bottom() - 20 - TOP) / ROW_H)

    private fun categories(): List<String> = listOf("All") + snap.rows.map { it.group }.distinct()

    private fun visibleRows(): List<ConfigBook.Row> {
        val cat = categories().getOrElse(category) { "All" }
        val q = query.trim().lowercase()
        return snap.rows.filter { r ->
            (cat == "All" || r.group == cat) &&
                (q.isEmpty() || r.path.lowercase().contains(q) || pretty(r.path).lowercase().contains(q) ||
                    r.description.lowercase().contains(q))
        }
    }

    override fun isPauseScreen(): Boolean = false   // edits are applied by the server; keep it ticking

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)   // 1.20.1 screens paint their own backdrop
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun init() {
        val w = panelWidth()
        val left = left()
        addRenderableWidget(text(title, (width - font.width(title)) / 2, 6))

        val catW = 150
        val box = EditBox(font, left + 1, 21, w - catW - 6, 18, Component.literal("Search"))
        box.setMaxLength(64)
        box.value = query
        box.setHint(Component.literal("Search settings...").withStyle(ChatFormatting.DARK_GRAY))
        box.setResponder { if (it != query) { query = it; page = 0; layoutRows() } }
        addRenderableWidget(box)
        addRenderableWidget(Button.builder(Component.literal("Category: ${categories().getOrElse(category) { "All" }}")) {
            category = (category + 1) % categories().size
            page = 0
            rebuildWidgets()
        }.bounds(left + w - catW, 20, catW, 20).tooltip(Tooltip.create(Component.literal("Show one group of settings at a time."))).build())

        val by = bottom()
        addRenderableWidget(Button.builder(Component.literal("<")) { page--; layoutRows() }
            .bounds(left, by, 20, 20).build())
        addRenderableWidget(Button.builder(Component.literal(">")) { page++; layoutRows() }
            .bounds(left + 150, by, 20, 20).build())
        val resetAll = Button.builder(Component.literal("Reset ALL")) { confirmResetAll() }
            .bounds(left + w - 170, by, 82, 20)
            .tooltip(Tooltip.create(Component.literal("Put every setting back to its default.")))
            .build()
        resetAll.active = snap.canEdit
        addRenderableWidget(resetAll)
        addRenderableWidget(Button.builder(Component.literal("Done")) { onClose() }
            .bounds(left + w - 82, by, 82, 20).build())

        layoutRows()
    }

    private fun <T : AbstractWidget> dynamic(widget: T): T {
        rowWidgets += widget
        return addRenderableWidget(widget)
    }

    private fun text(message: Component, x: Int, y: Int): StringWidget =
        StringWidget(x, y, font.width(message), 10, message, font)

    /** Trim [s] to fit [width] pixels, with an ellipsis when it had to be cut. */
    private fun fit(s: String, width: Int): String {
        if (font.width(s) <= width) return s
        var cut = s
        while (cut.isNotEmpty() && font.width("$cut...") > width) cut = cut.dropLast(1)
        return "$cut..."
    }

    private fun layoutRows() {
        rowWidgets.forEach { removeWidget(it) }
        rowWidgets.clear()

        val w = panelWidth()
        val left = left()
        val rows = visibleRows()
        val pp = perPage()
        val pages = max(1, (rows.size + pp - 1) / pp)
        page = page.coerceIn(0, pages - 1)

        var y = TOP
        for (r in rows.drop(page * pp).take(pp)) {
            addRow(r, left, y, w)
            y += ROW_H
        }
        if (rows.isEmpty()) {
            val none = Component.literal("No settings match.").withStyle(ChatFormatting.GRAY)
            dynamic(text(none, (width - font.width(none)) / 2, TOP + 6))
        }

        val pageText = Component.literal("Page ${page + 1}/$pages").withStyle(ChatFormatting.GRAY)
        dynamic(text(pageText, left + 85 - font.width(pageText) / 2, bottom() + 6))

        val notice = when {
            snap.status.isNotEmpty() -> Component.literal(fit(snap.status, w)).withStyle(
                if (snap.status.startsWith("Saved") || snap.status.startsWith("Reset") || snap.status.startsWith("Everything"))
                    ChatFormatting.GREEN else ChatFormatting.RED)
            !snap.canEdit -> Component.literal("Read only: only operators can change these settings.").withStyle(ChatFormatting.GOLD)
            else -> Component.literal("Hover a setting to see what it does. Changes save as soon as you apply them.")
                .withStyle(ChatFormatting.DARK_GRAY)
        }
        dynamic(text(notice, (width - font.width(notice)) / 2, bottom() - 14))
    }

    private fun addRow(r: ConfigBook.Row, left: Int, y: Int, w: Int) {
        val labelW = (w * 0.46).toInt()
        val resetW = 20
        val setW = 20
        val valueW = w - labelW - resetW - setW - 12
        val vx = left + labelW + 4
        val changed = r.value != r.default
        val tip = Tooltip.create(describe(r))

        val name = Component.literal(fit(pretty(r.path), labelW))
            .withStyle(if (changed) ChatFormatting.YELLOW else ChatFormatting.WHITE)
        dynamic(text(name, left, y + 6)).setTooltip(tip)

        if (r.kind == 'B') {
            val on = r.value.toBoolean()
            val toggle = Button.builder(Component.literal(if (on) "ON" else "OFF")
                .withStyle(if (on) ChatFormatting.GREEN else ChatFormatting.RED)) {
                send(ConfigBook.setRequest(r.path, (!on).toString()))
            }.bounds(vx, y, valueW + setW + 4, 20).tooltip(tip).build()
            toggle.active = snap.canEdit
            dynamic(toggle)
        } else {
            val box = EditBox(font, vx + 1, y + 1, valueW - 2, 18, Component.literal(r.path))
            box.setMaxLength(256)
            box.value = pending[r.path] ?: r.value
            box.setTooltip(tip)
            box.setEditable(snap.canEdit)
            val save = Button.builder(Component.literal("✔")) { commit(r.path, box.value) }
                .bounds(vx + valueW + 2, y, setW, 20)
                .tooltip(Tooltip.create(Component.literal("Save this value"))).build()
            save.active = snap.canEdit && box.value != r.value
            box.setResponder {
                if (it == r.value) pending.remove(r.path) else pending[r.path] = it
                save.active = snap.canEdit && it != r.value
            }
            dynamic(box)
            dynamic(save)
        }

        val reset = Button.builder(Component.literal("↺")) { pending.remove(r.path); send(ConfigBook.resetRequest(r.path)) }
            .bounds(left + w - resetW, y, resetW, 20)
            .tooltip(Tooltip.create(Component.literal("Reset to default: ${shown(r.default)}"))).build()
        reset.active = snap.canEdit && changed
        dynamic(reset)
    }

    private fun shown(value: String) = value.ifEmpty { "(empty)" }

    private fun describe(r: ConfigBook.Row): Component {
        val sb = StringBuilder(r.description.ifEmpty { pretty(r.path) })
        sb.append("\n\nDefault: ").append(shown(r.default))
        if (r.min.isNotEmpty()) sb.append("\nRange: ").append(r.min).append(" to ").append(r.max)
        sb.append("\nConfig key: ").append(r.path)
        return Component.literal(sb.toString())
    }

    private fun commit(path: String, text: String) {
        pending.remove(path)
        send(ConfigBook.setRequest(path, text))
    }

    private fun confirmResetAll() {
        val mc = minecraft ?: return
        mc.setScreen(ConfirmScreen(BooleanConsumer { yes ->
            if (yes) send(ConfigBook.RESET_ALL_REQUEST)
            mc.setScreen(this)
        }, Component.literal("Reset ALL spider settings?"),
            Component.literal("Every setting goes back to its default. This cannot be undone.")))
    }

    /** A fresh snapshot from the server: redraw the rows, keeping the page, filter and typing. */
    fun refresh(next: ConfigBook.Snapshot) {
        snap = next
        pending.keys.retainAll { path -> snap.rows.any { it.path == path && it.value != pending[path] } }
        layoutRows()
    }

    /** Closing saves anything typed but not yet applied. */
    override fun onClose() {
        for ((path, text) in pending.toMap()) {
            if (snap.rows.any { it.path == path && it.value != text }) send(ConfigBook.setRequest(path, text))
        }
        pending.clear()
        super.onClose()
    }
}
