package dev.emortal.tnt

import com.github.luben.zstd.Zstd
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.binary.BinaryWriter
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension

object TNT {

    private val LOGGER = LoggerFactory.getLogger(TNT::class.java)

    /**
     * Creates a .TNT world from chunks
     * @param chunks The chunks to use to create the world
     * @param path Where to create the file
     */
    fun createTNTFile(chunks: Collection<Chunk>, path: Path) {
        val writer = BinaryWriter()

        writer.writeInt(chunks.size)

        chunks.forEach {
            writer.writeInt(it.chunkX)
            writer.writeInt(it.chunkZ)

            writer.writeByte(it.minSection.toByte())
            writer.writeByte(it.maxSection.toByte())

            it.sections.forEachIndexed { sectionI, section ->
                var airSkip = 0
                var needsEnding = false
                for (x in 0 until Chunk.CHUNK_SIZE_X) {
                    for (y in 0 until Chunk.CHUNK_SECTION_SIZE) {
                        for (z in 0 until Chunk.CHUNK_SIZE_X) {
                            val block = it.getBlock(x, y + ((sectionI + it.minSection) * Chunk.CHUNK_SECTION_SIZE), z)

                            if (block == Block.AIR) {
                                airSkip++
                                if (airSkip == 1) {
                                    writer.writeShort(0)
                                    needsEnding = true
                                }

                                continue
                            }
                            if (airSkip > 0) {
                                writer.writeInt(airSkip)
                                needsEnding = false
                            }

                            airSkip = 0

                            val nbt = block.nbt()

                            writer.writeShort(block.stateId())
                            writer.writeBoolean(block.hasNbt())
                            if (nbt != null) {
                                writer.writeNBT("blockNBT", nbt)
                            }
                        }
                    }
                }

                // Air skip sometimes isn't written, maybe there is a cleaner way?
                if (needsEnding) {
                    writer.writeInt(airSkip)
                }

                writer.writeByteArray(section.blockLight)
                writer.writeByteArray(section.skyLight)
            }
        }

        val bytes: ByteArray = writer.toByteArray()
        val compressed = Zstd.compress(bytes)

        writer.close()
        writer.flush()

        Files.write(path, compressed)
    }

    fun convertAnvilToTNT(path: Path) {
        val instanceManager = MinecraftServer.getInstanceManager()

        val mcaFiles = Files.list(path.resolve("region")).collect(
            Collectors.toSet())
        val convertInstance = instanceManager.createInstanceContainer()
        val loader = ConversionAnvilLoader(path)
        convertInstance.chunkLoader = loader

        val countDownLatch = CountDownLatch((mcaFiles.size) * 1024)
        val chunks: MutableSet<Chunk> = ConcurrentHashMap.newKeySet()

        mcaFiles.forEach {
            val args = it.nameWithoutExtension.split(".").takeLast(2)
            val rX = args[0].toInt()
            val rZ = args[1].toInt()

            for (x in rX * 32 until rX * 32 + 32)
                for (z in rZ * 32 until rZ * 32 + 32) {
                    convertInstance.loadChunk(x, z).thenAccept {
                        var hasBlocks = false
                        for (x in 0 until 16) {
                            if (hasBlocks) break
                            for (y in -64 until 320) {
                                if (hasBlocks) break
                                for (z in 0 until 16) {
                                    if (it.getBlock(x, y, z) != Block.AIR) {
                                        hasBlocks = true
                                        break
                                    }
                                }
                            }
                        }

                        // Ignore chunks that contain no blocks
                        if (hasBlocks) chunks.add(it)

                        countDownLatch.countDown()
                    }
                }
        }

        countDownLatch.await()

        createTNTFile(chunks, path)

        instanceManager.unregisterInstance(convertInstance)
    }

}