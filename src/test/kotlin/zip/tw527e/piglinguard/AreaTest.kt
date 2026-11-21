package zip.tw527e.piglinguard

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AreaTest {
    private val box = Area.fromCorners("farm", "world", 10, 0, 10, -10, 100, -10)

    @Test
    fun cornersAreNormalised() {
        assertEquals(Area("farm", "world", -10, 0, -10, 10, 100, 10), box)
    }

    @Test
    fun invalidBoundsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Area("x", "world", 1, 0, 0, 0, 0, 0) }
    }

    @Test
    fun containsIsInclusiveOnEveryFace() {
        assertTrue(box.contains("world", 0, 50, 0))
        assertTrue(box.contains("world", -10, 0, -10))
        assertTrue(box.contains("world", 10, 100, 10))
        assertFalse(box.contains("world", 11, 50, 0))
        assertFalse(box.contains("world", -11, 50, 0))
        assertFalse(box.contains("world", 0, 101, 0))
        assertFalse(box.contains("world", 0, -1, 0))
        assertFalse(box.contains("world", 0, 50, 11))
        assertFalse(box.contains("world", 0, 50, -11))
    }

    @Test
    fun otherWorldNotContained() {
        assertFalse(box.contains("world_nether", 0, 50, 0))
    }

    @Test
    fun aroundCoversFullHeightAndRadius() {
        val a = Area.around("a", "world", 100, -50, 24, -64, 320)
        assertEquals(Area("a", "world", 76, -64, -74, 124, 319, -26), a)
        assertTrue(a.contains("world", 124, -64, -74))
        assertTrue(a.contains("world", 76, 319, -26))
        assertFalse(a.contains("world", 125, 0, -50))
        assertFalse(a.contains("world", 100, 320, -50))
    }

    @Test
    fun nameValidation() {
        assertTrue(Area.isValidName("gold_farm-1"))
        assertFalse(Area.isValidName(""))
        assertFalse(Area.isValidName("has space"))
        assertFalse(Area.isValidName("<red>"))
        assertFalse(Area.isValidName("a".repeat(33)))
    }

    @Test
    fun areaSetLookupAndUpdates() {
        val other = Area.fromCorners("other", "world", 1000, 0, 1000, 1010, 10, 1010)
        var set = AreaSet.EMPTY.with(AreaKind.WHITELIST, box).with(AreaKind.BLACKLIST, other)
        assertEquals(box, set.firstHit(AreaKind.WHITELIST, "world", 0, 0, 0))
        assertNull(set.firstHit(AreaKind.BLACKLIST, "world", 0, 0, 0))
        assertEquals(other, set.firstHit(AreaKind.BLACKLIST, "world", 1005, 5, 1005))
        assertNull(set.firstHit(AreaKind.WHITELIST, "world", 1005, 5, 1005))

        val bigger = box.copy(maxX = 500)
        set = set.with(AreaKind.WHITELIST, bigger)
        assertEquals(1, set.whitelist.size)
        assertEquals(bigger, set.firstHit(AreaKind.WHITELIST, "world", 400, 0, 0))

        set = set.without(AreaKind.WHITELIST, "farm")
        assertTrue(set.whitelist.isEmpty())
        assertEquals(1, set.blacklist.size)
    }

    @Test
    fun yamlRoundTrip() {
        val set = AreaSet.EMPTY.with(AreaKind.WHITELIST, box).with(AreaKind.BLACKLIST, box.copy(name = "bad"))
        val text = AreaYaml.write(set).saveToString()
        val back = AreaYaml.parse(YamlConfiguration().apply { loadFromString(text) }) { throw AssertionError(it) }
        assertEquals(set, back)
    }

    @Test
    fun invalidYamlEntriesAreSkippedWithWarning() {
        val yaml = YamlConfiguration().apply {
            loadFromString(
                """
                whitelist:
                  ok: { world: world, min: [0, 0, 0], max: [5, 5, 5] }
                  noWorld: { min: [0, 0, 0], max: [5, 5, 5] }
                  shortMin: { world: world, min: [0, 0], max: [5, 5, 5] }
                  textMax: { world: world, min: [0, 0, 0], max: [a, 5, 5] }
                  "bad name": { world: world, min: [0, 0, 0], max: [5, 5, 5] }
                  notASection: 3
                blacklist:
                  swapped: { world: world, min: [5, 5, 5], max: [0, 0, 0] }
                """.trimIndent(),
            )
        }
        val warnings = mutableListOf<String>()
        val set = AreaYaml.parse(yaml) { warnings += it }
        assertEquals(setOf("ok"), set.whitelist.keys)
        assertEquals(Area("swapped", "world", 0, 0, 0, 5, 5, 5), set.blacklist["swapped"])
        assertEquals(5, warnings.size)
    }

    @Test
    fun storePersists(@TempDir dir: File) {
        val file = File(dir, "areas.yml")
        val store = AreaStore(file) { throw AssertionError(it) }
        store.load()
        assertTrue(store.current.whitelist.isEmpty())
        store.add(AreaKind.BLACKLIST, box)
        assertTrue(store.remove(AreaKind.WHITELIST, "farm").not())

        val reloaded = AreaStore(file) { throw AssertionError(it) }
        reloaded.load()
        assertEquals(box, reloaded.current.blacklist["farm"])
        assertTrue(reloaded.remove(AreaKind.BLACKLIST, "farm"))
        reloaded.load()
        assertTrue(reloaded.current.blacklist.isEmpty())
    }
}
