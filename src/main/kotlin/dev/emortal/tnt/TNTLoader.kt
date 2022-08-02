package dev.emortal.tnt

import com.github.jinahya.bit.io.BitInput
import com.github.jinahya.bit.io.BitInputAdapter
import com.github.jinahya.bit.io.StreamByteInput
import com.github.luben.zstd.Zstd
import dev.emortal.tnt.source.FileTNTSource
import dev.emortal.tnt.source.TNTSource
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.utils.binary.BinaryReader
import net.minestom.server.utils.chunk.ChunkUtils
import org.jglrxavpok.hephaistos.nbt.CompressedProcesser
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTReader
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap


val LOGGER = LoggerFactory.getLogger(TNTLoader::class.java)

class TNTLoader(val instance: Instance, val tntSource: TNTSource, val offset: Point = Pos.ZERO) : IChunkLoader {

    constructor(instance: Instance, path: String, offset: Point = Pos.ZERO) : this(instance, FileTNTSource(Path.of(path)), offset)

    private var chunksMap: ConcurrentHashMap<Long, DynamicChunk>

    init {
        val blockManager = MinecraftServer.getBlockManager()

        val byteArray = tntSource.load().readAllBytes()
        val decompressed = Zstd.decompress(byteArray, Zstd.decompressedSize(byteArray).toInt())
        val binaryReader = BinaryReader(decompressed)
        val nbtReader = NBTReader(binaryReader, CompressedProcesser.NONE)

        val reader: BitInput = BitInputAdapter.from(StreamByteInput.from(binaryReader))

        val hasLights = binaryReader.readBoolean()

        val chunks = reader.readInt32()

        chunksMap = ConcurrentHashMap<Long, DynamicChunk>(chunks)

        for (chunkI in 0 until chunks) {


            val chunkX = reader.readInt32()
            val chunkZ = reader.readInt32()

            val minSection = reader.readByte8().toInt()
            val maxSection = reader.readByte8().toInt()

            val chunk = DynamicChunk(instance, chunkX, chunkZ)

            if (hasLights) {
                for (sectionY in minSection until maxSection) {
                    chunk.sections.forEach {
                        it.blockLight = binaryReader.readByteArray()
                        it.skyLight = binaryReader.readByteArray()
                    }
                    //mstChunk.lightArrays.add(binaryReader.readByteArray() to binaryReader.readByteArray())
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
                val string = binaryReader.readSizedString()
                paletteStrings.add(string)
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

                            chunk.setBlock(x, y, z, block.withNbt(nbtMap[ChunkUtils.getBlockIndex(x, y, z)]).withHandler(blockManager.getHandlerOrDummy(block.name())))
                        } else {
                            chunk.setBlock(x, y, z, block)
                        }


                        blockI++
                        if (blockI >= blockCount) break@blockLoop
                    }
                }
            }

            nbtMap.clear()
            paletteStrings.clear()

            chunksMap[ChunkUtils.getChunkIndex(chunkX, chunkZ)] = chunk
        }

        binaryReader.close()
        nbtReader.close()
        reader.close()
        LOGGER.info("Chunk map size: ${chunksMap.size}")
    }

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): CompletableFuture<Chunk?> {
        val mstChunk = chunksMap[ChunkUtils.getChunkIndex(chunkX, chunkZ)] ?: return CompletableFuture.completedFuture(null)
        //chunksMap.remove(ChunkUtils.getChunkIndex(chunkX, chunkZ))

        return CompletableFuture.completedFuture(mstChunk)
    }

    override fun saveChunk(chunk: Chunk): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    /**
     * Clears the loaded chunks
     *
     * You may also want to set the chunk loader of the instance to `null`
     *
     * Should be called when the instance is unloaded
     */
    fun unload() {
        chunksMap.clear()
    }

    override fun supportsParallelLoading(): Boolean = false
    override fun supportsParallelSaving(): Boolean = false

}