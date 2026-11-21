package zip.tw527e.piglinguard

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

enum class AreaKind(val yamlKey: String, val command: String, val display: String) {
    WHITELIST("whitelist", "wl", "白名單"),
    BLACKLIST("blacklist", "bl", "黑名單"),
}

/** An axis-aligned box of blocks, bounds inclusive. Always normalised so min <= max. */
data class Area(
    val name: String,
    val world: String,
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
) {
    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "min must be <= max" }
    }

    fun contains(world: String, x: Int, y: Int, z: Int): Boolean =
        world == this.world && x in minX..maxX && y in minY..maxY && z in minZ..maxZ

    fun describe(): String = "$world ($minX, $minY, $minZ) ~ ($maxX, $maxY, $maxZ)"

    companion object {
        private val NAME = Regex("[A-Za-z0-9_\\-]{1,32}")

        fun isValidName(name: String): Boolean = NAME.matches(name)

        /** Box spanning two corners in any order. */
        fun fromCorners(name: String, world: String, x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) =
            Area(name, world, minOf(x1, x2), minOf(y1, y2), minOf(z1, z2), maxOf(x1, x2), maxOf(y1, y2), maxOf(z1, z2))

        /** Box centred on (x, z) with a horizontal radius, covering the full world height. */
        fun around(name: String, world: String, x: Int, z: Int, radius: Int, minHeight: Int, maxHeightExclusive: Int) =
            Area(name, world, x - radius, minHeight, z - radius, x + radius, maxHeightExclusive - 1, z + radius)
    }
}

/** Immutable snapshot of both lists; replaced as a whole on every change. */
data class AreaSet(
    val whitelist: Map<String, Area> = emptyMap(),
    val blacklist: Map<String, Area> = emptyMap(),
) {
    fun of(kind: AreaKind): Map<String, Area> = if (kind == AreaKind.WHITELIST) whitelist else blacklist

    fun firstHit(kind: AreaKind, world: String, x: Int, y: Int, z: Int): Area? =
        of(kind).values.firstOrNull { it.contains(world, x, y, z) }

    fun with(kind: AreaKind, area: Area): AreaSet = replace(kind, of(kind) + (area.name to area))

    fun without(kind: AreaKind, name: String): AreaSet = replace(kind, of(kind) - name)

    private fun replace(kind: AreaKind, map: Map<String, Area>) =
        if (kind == AreaKind.WHITELIST) copy(whitelist = map) else copy(blacklist = map)

    companion object {
        val EMPTY = AreaSet()
    }
}

object AreaYaml {
    fun parse(root: ConfigurationSection, warn: (String) -> Unit): AreaSet {
        var set = AreaSet.EMPTY
        for (kind in AreaKind.entries) {
            val section = root.getConfigurationSection(kind.yamlKey) ?: continue
            for (name in section.getKeys(false)) {
                val area = parseArea(name, section.getConfigurationSection(name))
                if (area == null) {
                    warn("areas.yml: ${kind.yamlKey}.$name 格式不正確，已略過")
                } else {
                    set = set.with(kind, area)
                }
            }
        }
        return set
    }

    private fun parseArea(name: String, s: ConfigurationSection?): Area? {
        if (s == null || !Area.isValidName(name)) return null
        val world = s.getString("world")?.takeIf { it.isNotBlank() } ?: return null
        val min = intTriple(s.getList("min")) ?: return null
        val max = intTriple(s.getList("max")) ?: return null
        return Area.fromCorners(name, world, min[0], min[1], min[2], max[0], max[1], max[2])
    }

    private fun intTriple(list: List<*>?): IntArray? {
        if (list == null || list.size != 3) return null
        val out = IntArray(3)
        for (i in 0 until 3) {
            out[i] = (list[i] as? Number)?.toInt() ?: return null
        }
        return out
    }

    fun write(set: AreaSet): YamlConfiguration {
        val yaml = YamlConfiguration()
        for (kind in AreaKind.entries) {
            val section = yaml.createSection(kind.yamlKey)
            for (area in set.of(kind).values.sortedBy { it.name }) {
                val s = section.createSection(area.name)
                s.set("world", area.world)
                s.set("min", listOf(area.minX, area.minY, area.minZ))
                s.set("max", listOf(area.maxX, area.maxY, area.maxZ))
            }
        }
        return yaml
    }
}

/** Holds the current [AreaSet] behind a volatile reference; writers are serialised. */
class AreaStore(private val file: File, private val warn: (String) -> Unit) {
    @Volatile
    var current: AreaSet = AreaSet.EMPTY
        private set

    @Synchronized
    fun load() {
        current = AreaYaml.parse(YamlConfiguration.loadConfiguration(file), warn)
    }

    @Synchronized
    fun add(kind: AreaKind, area: Area) {
        current = current.with(kind, area)
        save()
    }

    @Synchronized
    fun remove(kind: AreaKind, name: String): Boolean {
        if (name !in current.of(kind)) return false
        current = current.without(kind, name)
        save()
        return true
    }

    private fun save() {
        AreaYaml.write(current).save(file)
    }
}
