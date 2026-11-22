package zip.tw527e.piglinguard

import org.bukkit.World
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason

/** Result of [decide]. [remove] tells the listener whether to cancel the teleport and remove the entity. */
enum class Decision(val remove: Boolean, val reason: String) {
    IGNORE_NOT_ZOMBIFIED_PIGLIN(false, "不是殭屍化豬布林"),
    IGNORE_NOT_OVERWORLD_TO_NETHER(false, "不是主世界 → 地獄"),
    IGNORE_WORLD_NOT_ENABLED(false, "這個世界沒有啟用"),
    IGNORE_CUSTOM_NAME(false, "有自訂名稱"),
    IGNORE_LEASHED(false, "被拴繩拴著"),
    IGNORE_VEHICLE_OR_PASSENGERS(false, "正在騎乘或載著其他實體"),
    IGNORE_WHITELIST(false, "在白名單區域內"),
    REMOVE_BLACKLIST(true, "在黑名單區域內"),
    IGNORE_AUTO_DISABLED(false, "自動偵測已關閉"),
    IGNORE_NOT_PORTAL_SPAWN(false, "生成原因不在 farm-spawn-reasons 裡"),
    IGNORE_LOW_DENSITY(false, "附近傳送門方塊數量未達門檻"),
    REMOVE_AUTO(true, "自動偵測：附近傳送門方塊數量達到門檻"),
}

/** Facts about one portal attempt, collected by the listener (or by `/pg scan`). */
data class PortalFacts(
    val isZombifiedPiglin: Boolean,
    val fromEnvironment: World.Environment?,
    val toEnvironment: World.Environment?,
    val worldEnabled: Boolean,
    val hasCustomName: Boolean,
    val isLeashed: Boolean,
    val hasVehicleOrPassengers: Boolean,
    val spawnReason: SpawnReason?,
    val inWhitelist: Boolean,
    val inBlacklist: Boolean,
)

data class AutoDetect(
    val enabled: Boolean,
    val requirePortalSpawn: Boolean,
    val threshold: Int,
    /** Spawn reasons that count as "made by the farm" when [requirePortalSpawn] is on. */
    val farmSpawnReasons: Set<SpawnReason> = DEFAULT_FARM_SPAWN_REASONS,
)

/**
 * Portal-spawned piglins, plus the reinforcements a zombified piglin can call in when it is hit
 * (Zombie reinforcement logic), which farms produce while killing them.
 */
val DEFAULT_FARM_SPAWN_REASONS: Set<SpawnReason> = setOf(SpawnReason.NETHER_PORTAL, SpawnReason.REINFORCEMENTS)

/**
 * The whole decision, first match wins. Pure: [density] is only called when the auto-detect
 * step actually needs it, so callers can make it a cache lookup.
 */
fun decide(facts: PortalFacts, auto: AutoDetect, density: () -> Long): Decision {
    if (!facts.isZombifiedPiglin) return Decision.IGNORE_NOT_ZOMBIFIED_PIGLIN
    if (facts.fromEnvironment != World.Environment.NORMAL || facts.toEnvironment != World.Environment.NETHER) {
        return Decision.IGNORE_NOT_OVERWORLD_TO_NETHER
    }
    if (!facts.worldEnabled) return Decision.IGNORE_WORLD_NOT_ENABLED
    if (facts.hasCustomName) return Decision.IGNORE_CUSTOM_NAME
    if (facts.isLeashed) return Decision.IGNORE_LEASHED
    if (facts.hasVehicleOrPassengers) return Decision.IGNORE_VEHICLE_OR_PASSENGERS

    if (facts.inWhitelist) return Decision.IGNORE_WHITELIST
    if (facts.inBlacklist) return Decision.REMOVE_BLACKLIST

    if (!auto.enabled) return Decision.IGNORE_AUTO_DISABLED
    if (auto.requirePortalSpawn && facts.spawnReason !in auto.farmSpawnReasons) {
        return Decision.IGNORE_NOT_PORTAL_SPAWN
    }
    return if (density() >= auto.threshold) Decision.REMOVE_AUTO else Decision.IGNORE_LOW_DENSITY
}
