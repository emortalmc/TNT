package dev.emortal.tnt

import com.github.luben.zstd.Zstd
import dev.emortal.tnt.source.TNTSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    val tntScope = CoroutineScope(Dispatchers.IO)

    /**
     * Saves a .TNT world from chunks
     */
    fun createTNTFile(chunks: Collection<Chunk>, tntSource: TNTSource) {
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

        tntSource.save(compressed)

        writer.close()
        writer.flush()
    }

    /**
     * Converts an anvil folder to a TNT file
     *
     * Source support should be added
     */
    fun convertAnvilToTNT(pathToAnvil: Path, tntSaveSource: TNTSource) {
        val instanceManager = MinecraftServer.getInstanceManager()

        val mcaFiles = Files.list(pathToAnvil.resolve("region")).collect(
            Collectors.toSet())

        val convertInstance = instanceManager.createInstanceContainer()
        val loader = ConversionAnvilLoader(pathToAnvil)
        convertInstance.chunkLoader = loader

        val countDownLatch = CountDownLatch((mcaFiles.size) * 32 * 32) // each MCA file contains 32 chunks
        val chunks: MutableSet<Chunk> = ConcurrentHashMap.newKeySet()

        mcaFiles.forEach {
            val args = it.nameWithoutExtension.split(".").takeLast(2)
            val rX = args[0].toInt()
            val rZ = args[1].toInt()

            for (x in rX * 32 until rX * 32 + 32) {
                for (z in rZ * 32 until rZ * 32 + 32) {
                    convertInstance.loadChunk(x, z).thenAcceptAsync {
                        // Ignore chunks that contain no blocks
                        if (it.sections.any { it.blockPalette().count() > 0 }) chunks.add(it)

                        countDownLatch.countDown()
                    }
                }
            }
        }

        val before = System.nanoTime()
        countDownLatch.await()
        println("Took ${(System.nanoTime() - before) / 1_000_000}ms to convert")

        // TODO: make source independant
        createTNTFile(chunks, tntSaveSource)

        instanceManager.unregisterInstance(convertInstance)
    }

}