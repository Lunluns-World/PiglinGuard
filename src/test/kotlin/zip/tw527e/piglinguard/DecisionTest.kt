package zip.tw527e.piglinguard

import org.bukkit.World.Environment
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class DecisionTest {
    /** A farm piglin that should be removed by auto-detect. */
    private val farm = PortalFacts(
        isZombifiedPiglin = true,
        fromEnvironment = Environment.NORMAL,
        toEnvironment = Environment.NETHER,
        worldEnabled = true,
        hasCustomName = false,
        isLeashed = false,
        hasVehicleOrPassengers = false,
        spawnReason = SpawnReason.NETHER_PORTAL,
        inWhitelist = false,
        inBlacklist = false,
    )
    private val auto = AutoDetect(enabled = true, requirePortalSpawn = true, threshold = 1000)
    private val dense = { 5000L }

    private fun failDensity(): Long = throw AssertionError("density must not be read")

    @Test
    fun farmPiglinIsRemovedByAutoDetect() {
        assertEquals(Decision.REMOVE_AUTO, decide(farm, auto, dense))
    }

    @Test
    fun notZombifiedPiglin() {
        assertEquals(Decision.IGNORE_NOT_ZOMBIFIED_PIGLIN, decide(farm.copy(isZombifiedPiglin = false), auto, ::failDensity))
    }

    @Test
    fun wrongDirection() {
        val cases = listOf(
            farm.copy(fromEnvironment = Environment.NETHER, toEnvironment = Environment.NORMAL),
            farm.copy(toEnvironment = Environment.THE_END),
            farm.copy(fromEnvironment = Environment.THE_END),
            farm.copy(toEnvironment = null),
            farm.copy(fromEnvironment = null),
        )
        cases.forEach { assertEquals(Decision.IGNORE_NOT_OVERWORLD_TO_NETHER, decide(it, auto, ::failDensity)) }
    }

    @Test
    fun worldNotEnabled() {
        assertEquals(Decision.IGNORE_WORLD_NOT_ENABLED, decide(farm.copy(worldEnabled = false), auto, ::failDensity))
    }

    @Test
    fun customName() {
        assertEquals(Decision.IGNORE_CUSTOM_NAME, decide(farm.copy(hasCustomName = true), auto, ::failDensity))
    }

    @Test
    fun leashed() {
        assertEquals(Decision.IGNORE_LEASHED, decide(farm.copy(isLeashed = true), auto, ::failDensity))
    }

    @Test
    fun vehicleOrPassengers() {
        assertEquals(Decision.IGNORE_VEHICLE_OR_PASSENGERS, decide(farm.copy(hasVehicleOrPassengers = true), auto, ::failDensity))
    }

    @Test
    fun protectionsBeatBlacklist() {
        val listed = farm.copy(inBlacklist = true)
        assertEquals(Decision.IGNORE_CUSTOM_NAME, decide(listed.copy(hasCustomName = true), auto, ::failDensity))
        assertEquals(Decision.IGNORE_LEASHED, decide(listed.copy(isLeashed = true), auto, ::failDensity))
        assertEquals(Decision.IGNORE_VEHICLE_OR_PASSENGERS, decide(listed.copy(hasVehicleOrPassengers = true), auto, ::failDensity))
        assertEquals(Decision.IGNORE_WORLD_NOT_ENABLED, decide(listed.copy(worldEnabled = false), auto, ::failDensity))
    }

    @Test
    fun whitelistWinsOverBlacklistAndDensity() {
        assertEquals(Decision.IGNORE_WHITELIST, decide(farm.copy(inWhitelist = true), auto, ::failDensity))
        assertEquals(Decision.IGNORE_WHITELIST, decide(farm.copy(inWhitelist = true, inBlacklist = true), auto, ::failDensity))
    }

    @Test
    fun blacklistSkipsDensityAndSpawnReason() {
        val f = farm.copy(inBlacklist = true, spawnReason = SpawnReason.NATURAL)
        assertEquals(Decision.REMOVE_BLACKLIST, decide(f, auto, ::failDensity))
        assertEquals(Decision.REMOVE_BLACKLIST, decide(f, auto.copy(enabled = false), ::failDensity))
    }

    @Test
    fun autoDetectDisabled() {
        assertEquals(Decision.IGNORE_AUTO_DISABLED, decide(farm, auto.copy(enabled = false), ::failDensity))
    }

    @ParameterizedTest
    @EnumSource(SpawnReason::class, names = ["NETHER_PORTAL", "REINFORCEMENTS"], mode = EnumSource.Mode.EXCLUDE)
    fun notPortalSpawn(reason: SpawnReason) {
        assertEquals(Decision.IGNORE_NOT_PORTAL_SPAWN, decide(farm.copy(spawnReason = reason), auto, ::failDensity))
    }

    @Test
    fun reinforcementsCountAsFarmSpawnByDefault() {
        assertEquals(Decision.REMOVE_AUTO, decide(farm.copy(spawnReason = SpawnReason.REINFORCEMENTS), auto, dense))
    }

    @Test
    fun farmSpawnReasonsAreConfigurable() {
        val onlyPortal = auto.copy(farmSpawnReasons = setOf(SpawnReason.NETHER_PORTAL))
        assertEquals(Decision.IGNORE_NOT_PORTAL_SPAWN, decide(farm.copy(spawnReason = SpawnReason.REINFORCEMENTS), onlyPortal, ::failDensity))
        val withDefault = auto.copy(farmSpawnReasons = setOf(SpawnReason.DEFAULT))
        assertEquals(Decision.REMOVE_AUTO, decide(farm.copy(spawnReason = SpawnReason.DEFAULT), withDefault, dense))
        assertEquals(Decision.IGNORE_NOT_PORTAL_SPAWN, decide(farm, withDefault, ::failDensity))
    }

    @Test
    fun unknownSpawnReasonIsNotPortalSpawn() {
        assertEquals(Decision.IGNORE_NOT_PORTAL_SPAWN, decide(farm.copy(spawnReason = null), auto, ::failDensity))
    }

    @Test
    fun spawnReasonNotRequired() {
        val f = farm.copy(spawnReason = SpawnReason.NATURAL)
        assertEquals(Decision.REMOVE_AUTO, decide(f, auto.copy(requirePortalSpawn = false), dense))
        assertEquals(Decision.IGNORE_LOW_DENSITY, decide(f, auto.copy(requirePortalSpawn = false)) { 0L })
    }

    @Test
    fun densityThresholdIsInclusive() {
        assertEquals(Decision.REMOVE_AUTO, decide(farm, auto) { 1000L })
        assertEquals(Decision.IGNORE_LOW_DENSITY, decide(farm, auto) { 999L })
        assertEquals(Decision.IGNORE_LOW_DENSITY, decide(farm, auto) { 0L })
    }

    @Test
    fun singleVanillaPortalIsNotAFarm() {
        assertEquals(Decision.IGNORE_LOW_DENSITY, decide(farm, auto) { Settings.MAX_VANILLA_PORTAL.toLong() })
    }

    @Test
    fun onlyRemoveDecisionsRemove() {
        Decision.entries.forEach { assertEquals(it.name.startsWith("REMOVE_"), it.remove, it.name) }
        assertTrue(Decision.REMOVE_AUTO.remove)
        assertFalse(Decision.IGNORE_LOW_DENSITY.remove)
    }
}
