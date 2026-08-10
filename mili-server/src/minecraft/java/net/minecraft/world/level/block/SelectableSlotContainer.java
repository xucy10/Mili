package net.minecraft.world.level.block;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public interface SelectableSlotContainer {
    int getRows();

    int getColumns();

    default OptionalInt getHitSlot(BlockHitResult hitResult, Direction direction) {
        return getRelativeHitCoordinatesForBlockFace(hitResult, direction).map(vec2 -> {
            int section = getSection(1.0F - vec2.y, this.getRows());
            int section1 = getSection(vec2.x, this.getColumns());
            return OptionalInt.of(section1 + section * this.getColumns());
        }).orElseGet(OptionalInt::empty);
    }

    private static Optional<Vec2> getRelativeHitCoordinatesForBlockFace(BlockHitResult hitResult, Direction direction) {
        Direction direction1 = hitResult.getDirection();
        if (direction != direction1) {
            return Optional.empty();
        } else {
            BlockPos blockPos = hitResult.getBlockPos().relative(direction1);
            Vec3 vec3 = hitResult.getLocation().subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            double x = vec3.x();
            double y = vec3.y();
            double z = vec3.z();

            return switch (direction1) {
                case NORTH -> Optional.of(new Vec2((float)(1.0 - x), (float)y));
                case SOUTH -> Optional.of(new Vec2((float)x, (float)y));
                case WEST -> Optional.of(new Vec2((float)z, (float)y));
                case EAST -> Optional.of(new Vec2((float)(1.0 - z), (float)y));
                case DOWN, UP -> Optional.empty();
            };
        }
    }

    public static int getSection(float x, int columns) {
        float f = x * 16.0F;
        float f1 = 16.0F / columns;
        return Mth.clamp(Mth.floor(f / f1), 0, columns - 1);
    }
}
