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
import java.util.*;
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

            for (int x = rX * 32; x < rX * 32 + 32; x++) {
                for (int z = rZ * 32; z < rZ * 32 + 32; z++) {
                    convertInstance.loadChunk(x, z).thenAccept(chunk -> {
                        if (!chunkContainsBlocks(chunk)) {
                            cdl.countDown();
                            return;
                        }

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

        MinecraftServer.getInstanceManager().unregisterInstance(convertInstance);

        BinaryWriter writer = new BinaryWriter();

        writer.writeInt(convertedChunks.size());
        for (byte[] chunk : convertedChunks) {
            writer.writeBytes(chunk);
        }

        byte[] bytes = writer.toByteArray();
        source.save(bytes);

        writer.close();
        writer.flush();
    }


    public static Collection<Chunk> filterEmptyChunks(Collection<Chunk> chunks) {
        Set<Chunk> newChunks = new HashSet<>();

        for (Chunk chunk : chunks) {
            if (!chunkContainsBlocks(chunk)) continue;

            newChunks.add(chunk);
        }

        return newChunks;
    }

    public static boolean chunkContainsBlocks(Chunk chunk) {
        boolean containsBlocks = false;
        for (Section section : chunk.getSections()) {
            if (section.blockPalette().count() > 0) {
                containsBlocks = true;
                break;
            }
        }
        return containsBlocks;
    }

    public static void convertChunksToTNT(Collection<Chunk> chunks, TNTSource source) throws IOException {
        BinaryWriter writer = new BinaryWriter();

        writer.writeInt(chunks.size());
        for (Chunk chunk : chunks) {
            byte[] converted = new byte[0];
            try {
                converted = convertChunk(chunk);
            } catch (IOException e) {
                e.printStackTrace();
            }

            writer.writeBytes(converted);
        }

        byte[] bytes = writer.toByteArray();
        source.save(bytes);

        writer.close();
        writer.flush();
    }

}
