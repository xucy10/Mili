package fun.bm.mili.villager;

import org.bukkit.World;

/**
 * Read-only grid of block snapshots around a villager.
 */
public final class BlockGrid {
    private final World world;
    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final int radius;

    public BlockGrid(World world, int baseX, int baseY, int baseZ, int radius) {
        this.world = world;
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseZ = baseZ;
        this.radius = radius;
    }

    public BlockSnapshot at(int x, int y, int z) {
        if (world == null) return null;
        var block = world.getBlockAt(x, y, z);
        var type = block.getType();
        return new BlockSnapshot(type, type.isSolid(), block.isPassable());
    }
}