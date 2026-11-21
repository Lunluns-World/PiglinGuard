package zip.tw527e.piglinguard

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

data class ChunkLocation(val world: String, val x: Int, val z: Int)

/** Counters since start-up. All thread-safe. */
class Stats {
    private val byDecision = Decision.entries.associateWith { LongAdder() }
    private val removalsByChunk = ConcurrentHashMap<ChunkLocation, LongAdder>()

    fun record(decision: Decision, world: String, blockX: Int, blockZ: Int) {
        byDecision.getValue(decision).increment()
        if (decision.remove) {
            removalsByChunk.computeIfAbsent(ChunkLocation(world, blockX shr 4, blockZ shr 4)) { LongAdder() }.increment()
        }
    }

    fun count(decision: Decision): Long = byDecision.getValue(decision).sum()

    fun topRemovalChunks(n: Int): List<Pair<ChunkLocation, Long>> =
        removalsByChunk.entries.map { it.key to it.value.sum() }.sortedByDescending { it.second }.take(n)
}
