package dev.emortal.tnt

import net.minestom.server.instance.Section
import net.minestom.server.instance.batch.ChunkBatch

class TNTChunk(val chunkBatch: ChunkBatch, val maxSection: Int, val minSection: Int) {

    val sections: List<Section> = Array(maxSection - minSection) {
        Section()
    }.toList()

}