package com.heledron.spideranimation.entity

/**
 * DEBUG VIEW — the original mod's debug graphics (TheCymaera's "Toggle Debug Graphics"), drawn
 * over every spider by [com.heledron.spideranimation.spider.renderSpiderDebug] while [enabled].
 * Toggled with `/spider debug` (OP), because there is no way to see it from the server: this mod
 * draws with server-side display entities, and F3's debug screen is purely client state, so
 * hooking the key would take a client mod plus a custom packet on three different loader APIs.
 *
 * The parts are ordinary display entities, so EVERYONE nearby sees them, on vanilla clients too —
 * for recording or showing someone what the legs are doing, that is what you want. Switching it
 * off simply stops submitting them; the display tracker discards them on the next tick.
 */
object SpiderDebug {
    var enabled = false

    /** What each part means, sent to whoever switches the view on. */
    const val LEGEND = "Spider debug view ON. Gold bars: where each leg looks for ground. " +
        "Cyan: each leg's trigger zone, where a foot steps once it leaves (red = leg stretched too far). " +
        "Feet: diamond planted, lapis touching, redstone in the air. Glass cubes: step targets " +
        "(lime = on ground). Green outline: the feet holding it up, with the centre of mass and " +
        "normal force inside. Emerald nub: facing. Diamond axes: preferred orientation."
}
