package fun.bm.mili.villager;

import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Read-only grid of block snapshots around a villager.
 * Uses LRU-like caching to avoid repeated world.getBlockAt() calls.
 */
public final class BlockGrid {
    private static final int MAX_CACHE_SIZE = 64;

    private final World world;
    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final int radius;
    private final Map<Long, BlockSnapshot> cache;

    public BlockGrid(World world, int baseX, int baseY, int baseZ, int radius) {
        this.world = world;
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseZ = baseZ;
        this.radius = radius;
        this.cache = new HashMap<>(MAX_CACHE_SIZE);
    }

    public BlockSnapshot at(int x, int y, int z) {
        if (world == null) return null;

        long key = packKey(x, y, z);
        BlockSnapshot cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        var block = world.getBlockAt(x, y, z);
        var type = block.getType();
        BlockSnapshot snapshot = new BlockSnapshot(type, type.isSolid(), block.isPassable());

        if (cache.size() < MAX_CACHE_SIZE) {
            cache.put(key, snapshot);
        }

        return snapshot;
    }

    private static long packKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | ((long) (z & 0x3FFFFFF));
    }
}