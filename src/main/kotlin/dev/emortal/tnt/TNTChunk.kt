package dev.emortal.tnt

import net.minestom.server.instance.batch.ChunkBatch

class TNTChunk() {

    var chunkBatch: ChunkBatch? = null
    val lightArrays = mutableListOf<Pair<ByteArray, ByteArray>>()

}