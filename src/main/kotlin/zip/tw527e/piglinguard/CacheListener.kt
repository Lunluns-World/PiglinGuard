package zip.tw527e.piglinguard

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.PortalCreateEvent

/** Keeps [PortalDensityCache] up to date. Every handler runs on the thread that owns the chunk. */
class CacheListener(private val plugin: PiglinGuardPlugin) : Listener {
    private val cache get() = plugin.densityCache

    private fun tracked(world: World): Boolean = world.name in plugin.settings.worlds

    private fun isPortalPart(type: Material) = type == Material.NETHER_PORTAL || type == Material.OBSIDIAN

    private fun dirty(block: Block) = cache.markDirty(block.world, block.x shr 4, block.z shr 4)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(e: ChunkLoadEvent) {
        if (tracked(e.world)) cache.onLoad(e.world, e.chunk.x, e.chunk.z)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkUnload(e: ChunkUnloadEvent) {
        cache.onUnload(e.world, e.chunk.x, e.chunk.z)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPortalCreate(e: PortalCreateEvent) {
        if (!tracked(e.world)) return
        e.blocks.map { (it.x shr 4) to (it.z shr 4) }.toSet().forEach { (cx, cz) -> cache.markDirty(e.world, cx, cz) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) {
        if (isPortalPart(e.block.type) && tracked(e.block.world)) dirty(e.block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPhysics(e: BlockPhysicsEvent) {
        // Fires very often: cheapest checks first.
        if (!isPortalPart(e.changedType) && e.block.type != Material.NETHER_PORTAL) return
        if (tracked(e.block.world)) dirty(e.block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(e: BlockExplodeEvent) = explosion(e.block.world, e.blockList())

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(e: EntityExplodeEvent) = explosion(e.location.world, e.blockList())

    private fun explosion(world: World?, blocks: List<Block>) {
        if (world == null || !tracked(world)) return
        blocks.asSequence()
            .filter { isPortalPart(it.type) }
            .map { (it.x shr 4) to (it.z shr 4) }
            .toSet()
            .forEach { (cx, cz) -> cache.markDirty(world, cx, cz) }
    }
}
