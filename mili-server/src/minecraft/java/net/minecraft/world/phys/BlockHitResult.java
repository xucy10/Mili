package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class BlockHitResult extends HitResult {
    private final Direction direction;
    private final BlockPos blockPos;
    private final boolean miss;
    private final boolean inside;
    private final boolean worldBorderHit;

    public static BlockHitResult miss(Vec3 location, Direction direction, BlockPos blockPos) {
        return new BlockHitResult(true, location, direction, blockPos, false, false);
    }

    public BlockHitResult(Vec3 location, Direction direction, BlockPos blockPos, boolean inside) {
        this(false, location, direction, blockPos, inside, false);
    }

    public BlockHitResult(Vec3 location, Direction direction, BlockPos blockPos, boolean inside, boolean worldBorderHit) {
        this(false, location, direction, blockPos, inside, worldBorderHit);
    }

    private BlockHitResult(boolean miss, Vec3 location, Direction direction, BlockPos blockPos, boolean inside, boolean worldBorderHit) {
        super(location);
        this.miss = miss;
        this.direction = direction;
        this.blockPos = blockPos;
        this.inside = inside;
        this.worldBorderHit = worldBorderHit;
    }

    public BlockHitResult withDirection(Direction newFace) {
        return new BlockHitResult(this.miss, this.location, newFace, this.blockPos, this.inside, this.worldBorderHit);
    }

    public BlockHitResult withPosition(BlockPos pos) {
        return new BlockHitResult(this.miss, this.location, this.direction, pos, this.inside, this.worldBorderHit);
    }

    public BlockHitResult hitBorder() {
        return new BlockHitResult(this.miss, this.location, this.direction, this.blockPos, this.inside, true);
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public Direction getDirection() {
        return this.direction;
    }

    @Override
    public HitResult.Type getType() {
        return this.miss ? HitResult.Type.MISS : HitResult.Type.BLOCK;
    }

    public boolean isInside() {
        return this.inside;
    }

    public boolean isWorldBorderHit() {
        return this.worldBorderHit;
    }
}
