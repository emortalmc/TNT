package dev.emortal.tnt

import com.github.luben.zstd.Zstd
import dev.emortal.tnt.source.FileTNTSource
import dev.emortal.tnt.source.TNTSource
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.ChunkBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.binary.BinaryReader
import net.minestom.server.utils.chunk.ChunkUtils
import org.jglrxavpok.hephaistos.nbt.CompressedProcesser
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTReader
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CompletableFuture


val LOGGER = LoggerFactory.getLogger(TNTLoader::class.java)

class TNTLoader(val instance: Instance, val tntSource: TNTSource, val offset: Point = Pos.ZERO) : IChunkLoader {

    constructor(instance: Instance, path: String, offset: Point = Pos.ZERO) : this(instance, FileTNTSource(Path.of(path)), offset)

    private val chunksMap = Long2ObjectOpenHashMap<TNTChunk>()

    init {
        val blockManager = MinecraftServer.getBlockManager()

        val byteArray = tntSource.load().readAllBytes()
        val decompressed = Zstd.decompress(byteArray, Zstd.decompressedSize(byteArray).toInt())
        val reader = BinaryReader(decompressed)
        val nbtReader = NBTReader(reader, CompressedProcesser.NONE)

        val chunks = reader.readInt()

        for (chunkI in 0 until chunks) {
            val batch = ChunkBatch()

            val chunkX = reader.readInt()
            val chunkZ = reader.readInt()

            val minSection = reader.readByte().toInt()
            val maxSection = reader.readByte().toInt()

            val mstChunk = TNTChunk(batch, maxSection, minSection)

            for (sectionY in minSection until maxSection) {
                var airSkip = 0
                val section = mstChunk.sections[sectionY - minSection]

                for (x in 0 until Chunk.CHUNK_SIZE_X) {
                    for (y in 0 until Chunk.CHUNK_SECTION_SIZE) {
                        for (z in 0 until Chunk.CHUNK_SIZE_X) {
                            if (airSkip > 0) {
                                airSkip--
                                continue
                            }

                            val stateId = reader.readShort()

                            if (stateId == 0.toShort()) {
                                airSkip = reader.readInt() - 1
                                continue
                            }

                            val hasNbt = reader.readBoolean()

                            val block = if (hasNbt) {
                                val nbt = nbtReader.read()

                                Block.fromStateId(stateId)!!
                                    .let {
                                        it.withHandler(blockManager.getHandlerOrDummy(it.name()))
                                    }
                                    .withNbt(nbt as NBTCompound)
                            } else {
                                Block.fromStateId(stateId)!!
                            }

                            // TODO: fix X and Z offset
                            batch.setBlock(x/* + offset.blockX()*/, y + (sectionY * 16) + offset.blockY(), z/* + offset.blockZ()*/, block)
                        }
                    }
                }

                val blockLights = reader.readByteArray()
                val skyLights = reader.readByteArray()
                section.blockLight = blockLights
                section.skyLight = skyLights
            }

            chunksMap[ChunkUtils.getChunkIndex(chunkX, chunkZ)] = mstChunk
        }

        reader.close()
        nbtReader.close()
    }

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): CompletableFuture<Chunk?> {
        val mstChunk = chunksMap[ChunkUtils.getChunkIndex(chunkX, chunkZ)] ?: return CompletableFuture.completedFuture(null)
        val chunk = DynamicChunk(instance, chunkX, chunkZ)

        val future = CompletableFuture<Chunk?>()

        // Copy chunk light from mstChunk to the new chunk
        chunk.sections.forEachIndexed { i, it ->
            val sec = mstChunk.sections[i]
            it.blockLight = sec.blockLight
            it.skyLight = sec.skyLight
        }
        mstChunk.chunkBatch.apply(instance, chunk) { future.complete(chunk) }

        instance.saveChunksToStorage()

        return future
    }

    override fun saveChunk(chunk: Chunk): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun supportsParallelLoading(): Boolean = true

}