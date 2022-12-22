package dev.emortal.tnt;

import com.github.luben.zstd.Zstd;
import dev.emortal.tnt.source.TNTSource;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.binary.BinaryWriter;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class TNT {

    private static Logger LOGGER = LoggerFactory.getLogger("TNT");

    private static byte[] convertChunk(Chunk chunk) throws IOException {
        BinaryWriter writer = new BinaryWriter();

        writer.writeInt(chunk.getChunkX());
        writer.writeInt(chunk.getChunkZ());
        writer.writeByte((byte) chunk.getMinSection());
        writer.writeByte((byte) chunk.getMaxSection());
//        LOGGER.info("Chunk {} {} min max {} {}", chunk.getChunkX(), chunk.getChunkZ(), chunk.getMinSection(), chunk.getMaxSection());

        int airSkip = 0;
        boolean needsEnding = false;

        for (int y = chunk.getMinSection() * Chunk.CHUNK_SECTION_SIZE; y < chunk.getMaxSection() * Chunk.CHUNK_SECTION_SIZE; y++) {
            for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                    Block block = chunk.getBlock(x, y, z);

                    // check for the air block state
                    if (block.stateId() == 0) {
                        airSkip++;
                        if (airSkip == 1) {
                            writer.writeShort((short) 0);
                            needsEnding = true;
                        }

                        continue;
                    }
                    if (airSkip > 0) {
                        writer.writeInt(airSkip);
                        needsEnding = false;
                    }

                    airSkip = 0;

                    writer.writeShort(block.stateId());

                    NBTCompound nbt = block.nbt();
                    writer.writeBoolean(block.hasNbt());
                    if (nbt != null) {
                        writer.writeNBT("blockNBT", nbt);
                    }
                }
            }
        }

        // Air skip sometimes isn't written, maybe there is a cleaner way?
        if (needsEnding) {
            writer.writeInt(airSkip);
        }

        for (Section section : chunk.getSections()) {
            writer.writeByteArray(section.getBlockLight());
            writer.writeByteArray(section.getSkyLight());
        }

        byte[] bytes = writer.toByteArray();
//        byte[] compressed = Zstd.compress(bytes);

//        source.save(compressed);

        writer.close();
        writer.flush();

        return bytes;
    }

    public static void convertAnvilToTNT(Path anvilPath, TNTSource source) throws IOException, InterruptedException {
        InstanceManager im = MinecraftServer.getInstanceManager();

        Set<Path> mcas = Files.list(anvilPath.resolve("region")).collect(Collectors.toSet());

        InstanceContainer convertInstance = im.createInstanceContainer();
        AnvilLoader loader = new AnvilLoader(anvilPath);
        convertInstance.setChunkLoader(loader);

        CountDownLatch cdl = new CountDownLatch(mcas.size() * 32 * 32);

        ArrayList<byte[]> convertedChunks = new ArrayList<>();

        for (Path mca : mcas) {
            String[] args = mca.getFileName().toString().split("\\.");
            int rX = Integer.parseInt(args[1]);
            int rZ = Integer.parseInt(args[2]);

//            LOGGER.info("Found mca x{} z{}", rX, rZ);

            for (int x = rX * 32; x < rX * 32 + 32; x++) {
                for (int z = rZ * 32; z < rZ * 32 + 32; z++) {
                    convertInstance.loadChunk(x, z).thenAccept(chunk -> {

                        // Ignore chunks that contain no blocks
                        boolean containsBlocks = false;
                        for (Section section : chunk.getSections()) {
                            if (section.blockPalette().count() > 0) {
                                containsBlocks = true;
                                break;
                            }
                        }
                        if (!containsBlocks) {
                            cdl.countDown();
                            return;
                        }

//                        LOGGER.info("Using chunk {} {}", chunk.getChunkX(), chunk.getChunkZ());


                        byte[] converted = new byte[0];
                        try {
                            converted = convertChunk(chunk);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        convertedChunks.add(converted);
                        converted = null;

                        // We're now done with this chunk
                        convertInstance.unloadChunk(chunk);

                        cdl.countDown();
                    });
                }
            }
        }

        cdl.await();

        BinaryWriter writer = new BinaryWriter();

        writer.writeInt(convertedChunks.size());
        for (byte[] chunk : convertedChunks) {
            writer.writeBytes(chunk);
        }

//        LOGGER.info("Wrote {} chunks", convertedChunks.size());

        byte[] bytes = writer.toByteArray();
        byte[] compressed = Zstd.compress(bytes);

        source.save(compressed);

        writer.close();
        writer.flush();
    }

}
