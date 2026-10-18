package com.heledron.spideranimation.ecs

/**
 * Bevy-style Entity Component System. Ported verbatim from the original `utilities/ecs/ecs.kt`,
 * with one addition: `remove(entity)` for immediate (same-tick) despawn used by the item toggle.
 */
class Ecs {
    val entities = mutableListOf<EcsEntity>()
    private val eventListeners = mutableListOf<(Any) -> Unit>()

    private val startSystems = mutableListOf<(Ecs) -> Unit>()
    private val tickSystems = mutableListOf<(Ecs) -> Unit>()
    private val renderSystems = mutableListOf<(Ecs) -> Unit>()

    fun onStart(func: (Ecs) -> Unit) { startSystems += func }
    fun onTick(func: (Ecs) -> Unit) { tickSystems += func }
    fun onRender(func: (Ecs) -> Unit) { renderSystems += func }

    inline fun <reified T : Any> onEvent(crossinline listener: (T) -> Unit) {
        addEventListener { event -> if (event is T) listener(event) }
    }

    fun addEventListener(listener: (Any) -> Unit) { eventListeners += listener }

    fun <T : Any> emit(message: T) {
        for (listener in eventListeners.toList()) listener(message)
    }

    fun spawn(vararg components: Any): EcsEntity {
        val entity = EcsEntity()
        for (component in components) entity.components.add(component)
        entities.add(entity)
        return entity
    }

    fun remove(entity: EcsEntity) { entities.remove(entity) }

    @JvmName("query1")
    inline fun <reified T : Any> query(): List<T> =
        entities.mapNotNull { it.query<T>() }

    @JvmName("query2")
    inline fun <reified A : Any, reified B : Any> query(): List<Pair<A, B>> =
        entities.mapNotNull { entity ->
            val a = entity.query<A>() ?: return@mapNotNull null
            val b = entity.query<B>() ?: return@mapNotNull null
            a to b
        }

    @JvmName("query3")
    inline fun <reified A : Any, reified B : Any, reified C : Any> query(): List<Triple<A, B, C>> =
        entities.mapNotNull { entity ->
            val a = entity.query<A>() ?: return@mapNotNull null
            val b = entity.query<B>() ?: return@mapNotNull null
            val c = entity.query<C>() ?: return@mapNotNull null
            Triple(a, b, c)
        }

    @JvmName("query4")
    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any> query(): List<Quadruple<A, B, C, D>> =
        entities.mapNotNull { entity ->
            val a = entity.query<A>() ?: return@mapNotNull null
            val b = entity.query<B>() ?: return@mapNotNull null
            val c = entity.query<C>() ?: return@mapNotNull null
            val d = entity.query<D>() ?: return@mapNotNull null
            Quadruple(a, b, c, d)
        }

    fun start() { for (system in startSystems.toList()) system(this) }

    fun update() {
        for (system in tickSystems.toList()) system(this)
        entities.removeIf { it.scheduledForRemoval }
    }

    fun render() { for (system in renderSystems.toList()) system(this) }
}

class EcsEntity {
    val components = mutableListOf<Any>()
    var scheduledForRemoval = false

    fun remove() { scheduledForRemoval = true }

    inline fun <reified T : Any> removeComponent() { components.removeIf { it is T } }

    inline fun <reified Old : Any> replaceComponent(component: Any) {
        components.removeIf { it is Old }
        components.add(component)
    }

    inline fun <reified T : Any> query(): T? {
        if (this is T) return this
        @Suppress("UNCHECKED_CAST")
        return components.find { it is T } as T?
    }
}

class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val forth: D) {
    operator fun component1() = first
    operator fun component2() = second
    operator fun component3() = third
    operator fun component4() = forth
}
