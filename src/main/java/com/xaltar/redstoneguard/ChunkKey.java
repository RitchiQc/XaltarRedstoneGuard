package com.xaltar.redstoneguard;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.Objects;

/**
 * Immutable key representing a chunk in a specific world.
 * Used for fast lookups in concurrent hash maps.
 */
public final class ChunkKey {

    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final int hashCode;

    public ChunkKey(String world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.hashCode = Objects.hash(world, chunkX, chunkZ);
    }

    public static ChunkKey fromChunk(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public static ChunkKey fromLocation(Location location) {
        return new ChunkKey(
                location.getWorld().getName(),
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4
        );
    }

    public static ChunkKey fromBlock(String world, int blockX, int blockZ) {
        return new ChunkKey(world, blockX >> 4, blockZ >> 4);
    }

    public String getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkKey)) return false;
        ChunkKey other = (ChunkKey) o;
        return chunkX == other.chunkX
                && chunkZ == other.chunkZ
                && world.equals(other.world);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return world + ":" + chunkX + ":" + chunkZ;
    }
}
