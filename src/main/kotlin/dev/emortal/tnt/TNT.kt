package dev.emortal.tnt

import com.github.jinahya.bit.io.BitOutputAdapter
import com.github.jinahya.bit.io.StreamByteOutput
import com.github.luben.zstd.Zstd
import dev.emortal.tnt.source.TNTSource
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.MathUtils
import net.minestom.server.utils.binary.BinaryWriter
import net.minestom.server.utils.chunk.ChunkUtils
import org.jglrxavpok.hephaistos.nbt.NBTCompound
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
     * Saves a .TNT world from chunks
     */
    fun createTNTFile(chunks: Collection<Chunk>, tntSource: TNTSource, saveLights: Boolean = true) {
        val binaryWriter = BinaryWriter()

        val writer = BitOutputAdapter.from(StreamByteOutput.from(binaryWriter))

        // Write settings
        writer.writeBoolean(saveLights)

        writer.writeInt32(chunks.size)

        val paletteBlocks = ArrayList<String>()
        val paletteIndexes = ArrayList<Int>(chunks.size * 16 * 384 * 16)

        chunks.forEach {

            val indexToNBT = ConcurrentHashMap<Int, NBTCompound>()

            writer.writeInt32(it.chunkX)
            writer.writeInt32(it.chunkZ)

            writer.writeByte8(it.minSection.toByte())
            writer.writeByte8(it.maxSection.toByte())

            if (saveLights) {
                // Write lighting
                it.sections.forEach { section ->
                    val blockLight = section.blockLight
                    writer.writeInt32(blockLight.size)
                    blockLight.forEach {
                        writer.writeByte8(it)
                    }

                    val skyLight = section.skyLight
                    writer.writeInt32(skyLight.size)
                    skyLight.forEach {
                        writer.writeByte8(it)
                    }
                }
            }

            for (x in 0 until Chunk.CHUNK_SIZE_X) {
                for (y in -64 until 320) {
                    for (z in 0 until Chunk.CHUNK_SIZE_X) {
                        val pos = Vec(x.toDouble(), y.toDouble(), z.toDouble())
                        val block = it.getBlock(pos)
                        val blockString = stringFromBlock(block)

                        val index = paletteBlocks.indexOf(blockString)
                        if (index == -1) {
                            paletteBlocks.add(blockString)
                            paletteIndexes.add(paletteBlocks.size - 1)
                        } else {
                            paletteIndexes.add(index)
                        }

                        val nbt = block.nbt()
                        if (nbt != null) indexToNBT[ChunkUtils.getBlockIndex(x, y, z)] = nbt
                    }
                }
            }

            // Write NBT
            writer.writeInt32(indexToNBT.size)
            indexToNBT.forEach {
                binaryWriter.writeInt(it.key)
                binaryWriter.writeNBT("blockNBT", it.value)
            }

            // TODO: Global palette option
            // Write blocks then clear for next chunk
            val maxBitsForState = MathUtils.bitsToRepresent(paletteBlocks.size) + 1
            writer.writeInt32(maxBitsForState)

            writer.writeInt32(paletteBlocks.size)
            LOGGER.info("palette size: ${paletteBlocks.size}")
            paletteBlocks.forEach {
                //binaryWriter.writeShort(it)
                binaryWriter.writeSizedString(it)
            }

            writer.writeInt32(paletteIndexes.size)
            //LOGGER.info("Palette blocks indexes ${paletteIndexes.size}")
            paletteIndexes.forEach {
                writer.writeInt(maxBitsForState, it)
                //binaryWriter.writeInt(it)
            }
            paletteBlocks.clear()
            paletteIndexes.clear()
        }

        val bytes: ByteArray = binaryWriter.toByteArray()
        val compressed = Zstd.compress(bytes)

        tntSource.save(compressed)

        writer.close()
        writer.flush()
        binaryWriter.close()
        binaryWriter.flush()
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

        val countDownLatch = CountDownLatch((mcaFiles.size) * 32 * 32) // each MCA file contains 32 chunks on each axis
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

    fun stringFromBlock(block: Block): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(block.name())

        if (block.properties().isNotEmpty()) {
            stringBuilder.append("[")
            var i = 0
            block.properties().forEach {
                if (i != 0) stringBuilder.append(",")
                stringBuilder.append(it.key)
                stringBuilder.append("=")
                stringBuilder.append(it.value)
                i++
            }
            stringBuilder.append("]")
        }

        return stringBuilder.toString()
    }

    fun blockFromString(string: String): Block {
        if (string.contains("[")) {
            val args = string.split("[")
            val namespace = args[0]
            val properties = args[1]
                .replace("]", "")
                .split(",")
                .associate {
                    val args = it.split("=")
                    args[0] to args[1]
                }

            return Block.fromNamespaceId(namespace)!!.withProperties(properties)
        } else {
            return Block.fromNamespaceId(string)!!
        }
    }

}