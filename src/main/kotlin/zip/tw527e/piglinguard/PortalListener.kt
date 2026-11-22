package zip.tw527e.piglinguard

import org.bukkit.Location
import org.bukkit.PortalType
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPortalEvent

/** Runs on the thread that owns the entity, so [Entity.remove] is safe here. */
class PortalListener(private val plugin: PiglinGuardPlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityPortal(e: EntityPortalEvent) {
        val entity = e.entity
        // Fast path: farms also push items and other mobs through portals.
        if (entity.type != EntityType.ZOMBIFIED_PIGLIN) return
        val from = e.from
        val world = from.world ?: return
        val toEnv = e.to?.world?.environment
            ?: if (e.portalType == PortalType.NETHER && world.environment == World.Environment.NORMAL) World.Environment.NETHER else null

        val decision = evaluate(plugin, entity, from, world, toEnv)
        plugin.stats.record(decision, world.name, from.blockX, from.blockZ, entity.entitySpawnReason?.name)
        if (!decision.remove) return

        e.isCancelled = true
        entity.remove()
        if (plugin.settings.logRemovals) {
            plugin.logger.info("Removed zombified piglin at ${world.name} (${from.blockX}, ${from.blockY}, ${from.blockZ}): $decision")
        }
    }

    companion object {
        fun evaluate(plugin: PiglinGuardPlugin, entity: Entity, at: Location, world: World, toEnv: World.Environment?): Decision {
            val settings = plugin.settings
            val areas = plugin.areas.current
            val x = at.blockX
            val y = at.blockY
            val z = at.blockZ
            val facts = PortalFacts(
                isZombifiedPiglin = entity.type == EntityType.ZOMBIFIED_PIGLIN,
                fromEnvironment = world.environment,
                toEnvironment = toEnv,
                worldEnabled = world.name in settings.worlds,
                hasCustomName = entity.customName() != null,
                isLeashed = (entity as? LivingEntity)?.isLeashed == true,
                hasVehicleOrPassengers = entity.isInsideVehicle || entity.passengers.isNotEmpty(),
                spawnReason = entity.entitySpawnReason,
                inWhitelist = areas.firstHit(AreaKind.WHITELIST, world.name, x, y, z) != null,
                inBlacklist = areas.firstHit(AreaKind.BLACKLIST, world.name, x, y, z) != null,
            )
            return decide(facts, settings.autoDetect) {
                plugin.densityCache.density(world, x, z, settings.radius).total
            }
        }
    }
}
