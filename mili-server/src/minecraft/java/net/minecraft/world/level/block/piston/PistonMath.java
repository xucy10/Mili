package net.minecraft.world.level.block.piston;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class PistonMath {
    public static AABB getMovementArea(AABB bounds, Direction dir, double delta) {
        double d = delta * dir.getAxisDirection().getStep();
        double min = Math.min(d, 0.0);
        double max = Math.max(d, 0.0);
        switch (dir) {
            case WEST:
                return new AABB(bounds.minX + min, bounds.minY, bounds.minZ, bounds.minX + max, bounds.maxY, bounds.maxZ);
            case EAST:
                return new AABB(bounds.maxX + min, bounds.minY, bounds.minZ, bounds.maxX + max, bounds.maxY, bounds.maxZ);
            case DOWN:
                return new AABB(bounds.minX, bounds.minY + min, bounds.minZ, bounds.maxX, bounds.minY + max, bounds.maxZ);
            case UP:
            default:
                return new AABB(bounds.minX, bounds.maxY + min, bounds.minZ, bounds.maxX, bounds.maxY + max, bounds.maxZ);
            case NORTH:
                return new AABB(bounds.minX, bounds.minY, bounds.minZ + min, bounds.maxX, bounds.maxY, bounds.minZ + max);
            case SOUTH:
                return new AABB(bounds.minX, bounds.minY, bounds.maxZ + min, bounds.maxX, bounds.maxY, bounds.maxZ + max);
        }
    }
}
