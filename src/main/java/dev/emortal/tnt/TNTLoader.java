package dev.emortal.tnt;

import dev.emortal.tnt.source.TNTSource;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.batch.BatchOption;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.utils.binary.BinaryReader;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jglrxavpok.hephaistos.nbt.CompressedProcesser;
import org.jglrxavpok.hephaistos.nbt.NBT;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTException;
import org.jglrxavpok.hephaistos.nbt.NBTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class TNTLoader implements IChunkLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(TNTLoader.class);

    private final TNTSource source;
    public final Long2ObjectOpenHashMap<TNTChunk> chunksMap = new Long2ObjectOpenHashMap<>();

    public TNTLoader(TNTSource source) throws IOException, NBTException {
        this.source = source;

        BlockManager blockManager = MinecraftServer.getBlockManager();

        byte[] byteArray = source.load().readAllBytes();
//        byte[] decompressed = Zstd.decompress(byteArray, (int) Zstd.decompressedSize(byteArray));
//        BinaryReader reader = new BinaryReader(decompressed);
        BinaryReader reader = new BinaryReader(byteArray);
        NBTReader nbtReader = new NBTReader(reader, CompressedProcesser.NONE);

        int chunks = reader.readInt();
//        LOGGER.info("Reading {} chunks", chunks);

        for (int chunkI = 0; chunkI < chunks; chunkI++) {
            ChunkBatch batch = new ChunkBatch(new BatchOption()/*.setSendUpdate(false)*/.setUnsafeApply(true));
//            ChunkBatch batch = new ChunkBatch();

            int chunkX = reader.readInt();
            int chunkZ = reader.readInt();

            int minSection = reader.readByte();
            int maxSection = reader.readByte();

//            LOGGER.info("Load chunk {} {} min max {} {}", chunkX, chunkZ, minSection, maxSection);

            TNTChunk mstChunk = new TNTChunk(batch, maxSection, minSection);

            int airSkip = 0;

            for (int y = minSection * Chunk.CHUNK_SECTION_SIZE; y < maxSection * Chunk.CHUNK_SECTION_SIZE; y++) {
                for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                    for (int z = 0; z < Chunk.CHUNK_SIZE_X; z++) {
                        if (airSkip > 0) {
                            airSkip--;
                            continue;
                        }

                        short stateId = reader.readShort();

                        if (stateId == 0) {
                            airSkip = reader.readInt() - 1;
                            continue;
                        }

                        boolean hasNbt = reader.readBoolean();

                        Block block;

                        if (hasNbt) {
                            NBT nbt = nbtReader.read();

                            Block b = Block.fromStateId(stateId);
                            block = b.withHandler(blockManager.getHandlerOrDummy(b.name())).withNbt((NBTCompound) nbt);
                        } else {
                            block = Block.fromStateId(stateId);
                        }

                        batch.setBlock(x, y, z, block);
                    }
                }
            }

            for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                Section section = mstChunk.sections[sectionY - minSection];
                byte[] blockLights = reader.readByteArray();
                byte[] skyLights = reader.readByteArray();
                section.setBlockLight(blockLights);
                section.setSkyLight(skyLights);
            }

            chunksMap.put(ChunkUtils.getChunkIndex(chunkX, chunkZ), mstChunk);
        }

        reader.close();
        nbtReader.close();
    }

    @Override
    public @NotNull CompletableFuture<@Nullable Chunk> loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        TNTChunk mstChunk = chunksMap.get(ChunkUtils.getChunkIndex(chunkX, chunkZ));
        if(mstChunk == null)
            return CompletableFuture.completedFuture(null);
        DynamicChunk chunk = new DynamicChunk(instance, chunkX, chunkZ);

        CompletableFuture<Chunk> future = new CompletableFuture<>();

        // Copy chunk light from mstChunk to the new chunk
        chunk.getSections().forEach(it -> {
            Section sec = mstChunk.sections[chunk.getSections().indexOf(it)];



            it.setBlockLight(sec.getBlockLight());
            it.setSkyLight(sec.getSkyLight());
        });

        // We can use unsafe as it does not matter what thread the callback is returned from
        mstChunk.batch.unsafeApply(instance, chunk, future::complete);

        return future;
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunk(@NotNull Chunk chunk) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean supportsParallelLoading() {
        return true;
    }

    @Override
    public boolean supportsParallelSaving() {
        return true;
    }
}