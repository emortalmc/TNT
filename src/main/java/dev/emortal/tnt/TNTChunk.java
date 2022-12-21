package dev.emortal.tnt;

import net.minestom.server.instance.Section;
import net.minestom.server.instance.batch.ChunkBatch;

import java.util.Arrays;

public class TNTChunk {

    public ChunkBatch batch;
    public Section[] sections;
    public TNTChunk(ChunkBatch batch, int maxSection, int minSection) {
        this.sections = new Section[maxSection - minSection];
        Arrays.setAll(sections, (a) -> new Section());

        this.batch = batch;
    }

}
