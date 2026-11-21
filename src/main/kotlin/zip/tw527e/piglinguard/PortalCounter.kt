package zip.tw527e.piglinguard

import org.bukkit.Axis
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Orientable

/** Counts nether portal blocks in a [ChunkSnapshot]. Safe to call from any thread. */
class PortalCounter {
    private val portalX: BlockData = portal(Axis.X)
    private val portalZ: BlockData = portal(Axis.Z)

    fun count(snapshot: ChunkSnapshot, minY: Int, maxYExclusive: Int): Int {
        // Cheap palette check over the whole chunk first; most chunks have no portal at all.
        if (!snapshot.contains(portalX) && !snapshot.contains(portalZ)) return 0
        var n = 0
        val sections = (maxYExclusive - minY) shr 4
        for (s in 0 until sections) {
            if (snapshot.isSectionEmpty(s)) continue
            val baseY = minY + (s shl 4)
            for (y in baseY until baseY + 16) {
                for (x in 0 until 16) {
                    for (z in 0 until 16) {
                        if (snapshot.getBlockType(x, y, z) == Material.NETHER_PORTAL) n++
                    }
                }
            }
        }
        return n
    }

    private fun portal(axis: Axis): BlockData =
        Material.NETHER_PORTAL.createBlockData { (it as Orientable).axis = axis }
}
