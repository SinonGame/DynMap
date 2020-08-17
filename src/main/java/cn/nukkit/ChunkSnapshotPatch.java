package cn.nukkit;

import cn.nukkit.level.format.FullChunk;

public interface ChunkSnapshotPatch extends FullChunk {
    /**
     * Capture thread-safe read-only snapshot of chunk data
     *
     * @return ChunkSnapshot
     */
    ChunkSnapshot getChunkSnapshot();
    /**
     * Capture thread-safe read-only snapshot of chunk data
     *
     * @param includeMaxblocky - if true, snapshot includes per-coordinate
     *     maximum Y values
     * @param includeBiome - if true, snapshot includes per-coordinate biome
     *     type
     * @param includeBiomeTempRain - if true, snapshot includes per-coordinate
     *     raw biome temperature and rainfall
     * @return ChunkSnapshot
     */
    ChunkSnapshot getChunkSnapshot(boolean includeMaxblocky, boolean includeBiome, boolean includeBiomeTempRain);

}
