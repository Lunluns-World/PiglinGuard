package zip.tw527e.piglinguard

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsAndChunkTest {
    private fun parse(yaml: String): Pair<Settings, List<String>> {
        val warnings = mutableListOf<String>()
        val s = Settings.parse(YamlConfiguration().apply { loadFromString(yaml) }) { warnings += it }
        return s to warnings
    }

    @Test
    fun emptyConfigUsesDefaults() {
        val (s, w) = parse("")
        assertEquals(Settings.DEFAULT, s)
        assertTrue(w.isEmpty())
    }

    @Test
    fun bundledConfigMatchesDefaults() {
        val text = javaClass.getResource("/config.yml")!!.readText()
        val (s, w) = parse(text)
        assertEquals(Settings.DEFAULT, s)
        assertTrue(w.isEmpty(), w.toString())
    }

    @Test
    fun validValuesAreRead() {
        val (s, w) = parse(
            """
            worlds: [world, farmworld]
            auto-detect: { enabled: false, radius: 32, threshold: 2000, require-portal-spawn: false, cache-ttl-minutes: 5 }
            log-removals: true
            """.trimIndent(),
        )
        assertEquals(Settings(setOf("world", "farmworld"), false, 32, 2000, false, 5, true), s)
        assertTrue(w.isEmpty())
    }

    @Test
    fun invalidValuesFallBackWithWarnings() {
        val (s, w) = parse(
            """
            worlds: world
            auto-detect: { enabled: maybe, radius: -1, threshold: lots, require-portal-spawn: 3, cache-ttl-minutes: 0 }
            log-removals: yesplease
            """.trimIndent(),
        )
        assertEquals(Settings.DEFAULT, s)
        assertEquals(7, w.size)
    }

    @Test
    fun lowThresholdWarnsButIsKept() {
        val (s, w) = parse("auto-detect: { threshold: 441 }")
        assertEquals(441, s.threshold)
        assertEquals(1, w.size)
        val (s2, w2) = parse("auto-detect: { threshold: 442 }")
        assertEquals(442, s2.threshold)
        assertTrue(w2.isEmpty())
    }

    @Test
    fun chunkRangeAroundOrigin() {
        val r = ChunkRange.around(0, 0, 24)
        assertEquals(ChunkRange(-2, 1, -2, 1), r)
        assertEquals(16, r.size)
    }

    @Test
    fun chunkRangeHandlesNegativesAndEdges() {
        // Block -1 is in chunk -1; -1 - 15 = -16 is still chunk -1.
        assertEquals(ChunkRange(-1, -1, -1, -1), ChunkRange.around(-1, -1, 0))
        // x 0..32 -> chunks 0..2, z -8..24 -> chunks -1..1
        assertEquals(ChunkRange(0, 2, -1, 1), ChunkRange.around(16, 8, 16))
        assertEquals(ChunkRange(0, 0, 0, 0), ChunkRange.around(8, 8, 7))
        assertEquals(ChunkRange(-1, 1, -1, 1), ChunkRange.around(8, 8, 9))
    }

    @Test
    fun chunkRangeForEachVisitsEveryChunkOnce() {
        val seen = mutableListOf<Pair<Int, Int>>()
        ChunkRange(-1, 0, 3, 5).forEach { x, z -> seen += x to z }
        assertEquals(6, seen.size)
        assertEquals(6, seen.toSet().size)
    }

    @Test
    fun statsCountsAndTopChunks() {
        val st = Stats()
        repeat(3) { st.record(Decision.REMOVE_AUTO, "world", 5, 5) }
        st.record(Decision.REMOVE_BLACKLIST, "world", 100, 100)
        st.record(Decision.IGNORE_WHITELIST, "world", 0, 0)
        assertEquals(3, st.count(Decision.REMOVE_AUTO))
        assertEquals(1, st.count(Decision.REMOVE_BLACKLIST))
        assertEquals(1, st.count(Decision.IGNORE_WHITELIST))
        val top = st.topRemovalChunks(5)
        assertEquals(listOf(ChunkLocation("world", 0, 0) to 3L, ChunkLocation("world", 6, 6) to 1L), top)
    }
}
