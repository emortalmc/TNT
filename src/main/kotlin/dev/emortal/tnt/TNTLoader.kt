package dev.emortal.tnt

import com.github.jinahya.bit.io.BitInput
import com.github.jinahya.bit.io.BitInputAdapter
import com.github.jinahya.bit.io.StreamByteInput
import com.github.luben.zstd.Zstd
import dev.emortal.tnt.source.FileTNTSource
import dev.emortal.tnt.source.TNTSource
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.*
import net.minestom.server.instance.batch.ChunkBatch
import net.minestom.server.utils.binary.BinaryReader
import net.minestom.server.utils.chunk.ChunkUtils
import org.jglrxavpok.hephaistos.nbt.CompressedProcesser
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTReader
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CompletableFuture


val LOGGER = LoggerFactory.getLogger(TNTLoader::class.java)

class TNTLoader(val instance: InstanceContainer, val tntSource: TNTSource, val offset: Point = Pos.ZERO) : IChunkLoader {

    constructor(instance: InstanceContainer, path: String, offset: Point = Pos.ZERO) : this(instance, FileTNTSource(Path.of(path)), offset)

    private var chunksMap: Long2ObjectOpenHashMap<TNTChunk>

    init {
        val blockManager = MinecraftServer.getBlockManager()

        val byteArray = tntSource.load().readAllBytes()
        val decompressed = Zstd.decompress(byteArray, Zstd.decompressedSize(byteArray).toInt())
        val binaryReader = BinaryReader(decompressed)
        val nbtReader = NBTReader(binaryReader, CompressedProcesser.NONE)

        val reader: BitInput = BitInputAdapter.from(StreamByteInput.from(binaryReader))

        val hasLights = reader.readBoolean()

        val chunks = reader.readInt32()

        chunksMap = Long2ObjectOpenHashMap<TNTChunk>(chunks)

        for (chunkI in 0 until chunks) {
            val batch = ChunkBatch()

            val chunkX = reader.readInt32()
            val chunkZ = reader.readInt32()

            val minSection = reader.readByte8().toInt()
            val maxSection = reader.readByte8().toInt()

            val mstChunk = TNTChunk(batch, maxSection, minSection)

            if (hasLights) {
                for (sectionY in minSection until maxSection) {
                    val section = mstChunk.sections[sectionY - minSection]

                    val blockLightCount = reader.readInt32()
                    val blockLights = ByteArray(blockLightCount) {
                        reader.readByte8()
                    }

                    val skyLightCount = reader.readInt32()
                    val skyLights = ByteArray(skyLightCount) {
                        reader.readByte8()
                    }

                    section.blockLight = blockLights
                    section.skyLight = skyLights
                }
            }

            val nbtCount = reader.readInt32()
            val nbtMap = mutableMapOf<Int, NBTCompound>()
            repeat(nbtCount) {
                nbtMap[binaryReader.readInt()] = nbtReader.read() as NBTCompound
            }

            val maxBitsForState = reader.readInt32()

            val paletteSize = reader.readInt32()
            val paletteStrings = mutableListOf<String>()

            repeat(paletteSize) {
                val blockString = binaryReader.readSizedString()
                paletteStrings.add(blockString)
            }

            val paletteBlocks = paletteStrings.map { TNT.blockFromString(it) }

            val blockCount = reader.readInt32()
            var blockI = 0

            blockLoop@ for (x in 0 until Chunk.CHUNK_SIZE_X) {
                for (y in -64 until 320) {
                    for (z in 0 until Chunk.CHUNK_SIZE_X) {
                        val index = reader.readInt(maxBitsForState)
                        val block = paletteBlocks[index]

                        val blockIndex = ChunkUtils.getBlockIndex(x, y, z)

                        if (nbtMap.containsKey(blockIndex)) {

                            mstChunk.chunkBatch.setBlock(x, y, z, block.withNbt(nbtMap[ChunkUtils.getBlockIndex(x, y, z)]).withHandler(blockManager.getHandlerOrDummy(block.name())))
                        } else {
                            mstChunk.chunkBatch.setBlock(x, y, z, block)
                        }


                        blockI++
                        if (blockI >= blockCount) break@blockLoop
                    }
                }
            }

            nbtMap.clear()
            paletteStrings.clear()

            chunksMap[ChunkUtils.getChunkIndex(chunkX, chunkZ)] = mstChunk
        }

        binaryReader.close()
        nbtReader.close()
        reader.close()
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

        return future
    }

    override fun saveChunk(chunk: Chunk): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    /**
     * Clears the loaded chunks and then removes itself from the instance
     *
     * Should be called when the instance is unloaded
     */
    fun unload() {
        chunksMap.clear()
        instance.chunkLoader = null
    }

    override fun supportsParallelLoading(): Boolean = true
    override fun supportsParallelSaving(): Boolean = true

}