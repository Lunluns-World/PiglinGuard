package zip.tw527e.piglinguard

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DensityResult(val total: Long, val chunks: Int, val missing: Int)

/**
 * Portal block count per loaded chunk.
 *
 * - Readers ([density]) only touch the map, from any thread. A missing chunk counts as 0 and a
 *   computation is scheduled on that chunk's region.
 * - A computation takes a [org.bukkit.ChunkSnapshot] on the owning region thread, counts on an
 *   async thread, then writes the map. Chunks are never loaded for this.
 */
class PortalDensityCache(private val plugin: Plugin) {
    private class Entry(val count: Int, val computedAt: Long)

    private val counts = ConcurrentHashMap<ChunkKey, Entry>()
    private val pending = ConcurrentHashMap.newKeySet<ChunkKey>()
    /** Marked dirty while a computation was already pending: compute once more afterwards. */
    private val redo = ConcurrentHashMap.newKeySet<ChunkKey>()
    /** Unloaded while a computation was pending: drop its result. */
    private val discard = ConcurrentHashMap.newKeySet<ChunkKey>()

    private val counter by lazy { PortalCounter() }

    @Volatile
    var ttlNanos: Long = TimeUnit.MINUTES.toNanos(10)

    val size: Int get() = counts.size

    fun density(world: World, x: Int, z: Int, radius: Int): DensityResult {
        val range = ChunkRange.around(x, z, radius)
        val now = System.nanoTime()
        var total = 0L
        var missing = 0
        range.forEach { cx, cz ->
            val key = ChunkKey(world.uid, cx, cz)
            val e = counts[key]
            if (e == null) {
                missing++
                request(world, cx, cz, 0)
            } else {
                total += e.count
                if (now - e.computedAt > ttlNanos) request(world, cx, cz, 0)
            }
        }
        return DensityResult(total, range.size, missing)
    }

    /** Chunk loaded: compute soon. */
    fun onLoad(world: World, cx: Int, cz: Int) {
        discard.remove(ChunkKey(world.uid, cx, cz))
        request(world, cx, cz, 1)
    }

    /** Portal created or possibly broken: recompute after the blocks have settled. */
    fun markDirty(world: World, cx: Int, cz: Int) = request(world, cx, cz, DIRTY_DELAY_TICKS)

    fun onUnload(world: World, cx: Int, cz: Int) {
        val key = ChunkKey(world.uid, cx, cz)
        counts.remove(key)
        redo.remove(key)
        if (pending.contains(key)) discard.add(key)
    }

    fun clear() {
        counts.clear()
        redo.clear()
    }

    private fun request(world: World, cx: Int, cz: Int, delayTicks: Long) {
        val key = ChunkKey(world.uid, cx, cz)
        if (!pending.add(key)) {
            if (delayTicks > 0) redo.add(key)
            return
        }
        try {
            val scheduler = Bukkit.getRegionScheduler()
            if (delayTicks <= 0) {
                scheduler.execute(plugin, world, cx, cz) { snapshotAndCount(world, cx, cz, key) }
            } else {
                scheduler.runDelayed(plugin, world, cx, cz, { snapshotAndCount(world, cx, cz, key) }, delayTicks)
            }
        } catch (e: Exception) {
            // Plugin disabling or scheduler refused; leave the chunk uncached.
            pending.remove(key)
        }
    }

    /** Runs on the region thread that owns the chunk. */
    private fun snapshotAndCount(world: World, cx: Int, cz: Int, key: ChunkKey) {
        if (!world.isChunkLoaded(cx, cz)) {
            finish(world, key, null)
            return
        }
        val snapshot = try {
            world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false)
        } catch (e: Exception) {
            finish(world, key, null)
            return
        }
        val minY = world.minHeight
        val maxY = world.maxHeight
        try {
            Bukkit.getAsyncScheduler().runNow(plugin) {
                var result: Int? = null
                try {
                    result = counter.count(snapshot, minY, maxY)
                } finally {
                    finish(world, key, result)
                }
            }
        } catch (e: Exception) {
            pending.remove(key)
        }
    }

    private fun finish(world: World, key: ChunkKey, result: Int?) {
        val dropped = discard.remove(key)
        if (result != null && !dropped) counts[key] = Entry(result, System.nanoTime())
        pending.remove(key)
        if (redo.remove(key) && !dropped) request(world, key.x, key.z, DIRTY_DELAY_TICKS)
    }

    companion object {
        const val DIRTY_DELAY_TICKS = 20L
    }
}
