package zip.tw527e.piglinguard

import java.util.UUID

data class ChunkKey(val world: UUID, val x: Int, val z: Int)

/** Inclusive rectangle of chunk coordinates. */
data class ChunkRange(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
    val size: Int get() = (maxX - minX + 1) * (maxZ - minZ + 1)

    inline fun forEach(action: (Int, Int) -> Unit) {
        for (cx in minX..maxX) for (cz in minZ..maxZ) action(cx, cz)
    }

    companion object {
        /**
         * Every chunk that overlaps the square of blocks within Chebyshev distance [radius] of
         * block (x, z). Counting whole chunks makes the density chunk-precise, not block-precise.
         */
        fun around(x: Int, z: Int, radius: Int): ChunkRange =
            ChunkRange((x - radius) shr 4, (x + radius) shr 4, (z - radius) shr 4, (z + radius) shr 4)
    }
}
