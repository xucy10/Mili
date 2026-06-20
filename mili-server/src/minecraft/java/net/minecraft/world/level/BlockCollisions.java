package net.minecraft.world.level;

import com.google.common.collect.AbstractIterator;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BlockCollisions<T> extends AbstractIterator<T> {
    private final AABB box;
    private final CollisionContext context;
    private final Cursor3D cursor;
    private final BlockPos.MutableBlockPos pos;
    private final VoxelShape entityShape;
    private final CollisionGetter collisionGetter;
    private final boolean onlySuffocatingBlocks;
    private @Nullable BlockGetter cachedBlockGetter;
    private long cachedBlockGetterPos;
    private final BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider;

    public BlockCollisions(
        CollisionGetter collisionGetter,
        @Nullable Entity entity,
        AABB box,
        boolean onlySuffocatingBlocks,
        BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider
    ) {
        this(collisionGetter, entity == null ? CollisionContext.empty() : CollisionContext.of(entity), box, onlySuffocatingBlocks, resultProvider);
    }

    public BlockCollisions(
        CollisionGetter collisionGetter,
        CollisionContext context,
        AABB box,
        boolean onlySuffocatingBlocks,
        BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider
    ) {
        this.context = context;
        this.pos = new BlockPos.MutableBlockPos();
        this.entityShape = Shapes.create(box);
        this.collisionGetter = collisionGetter;
        this.box = box;
        this.onlySuffocatingBlocks = onlySuffocatingBlocks;
        this.resultProvider = resultProvider;
        int i = Mth.floor(box.minX - 1.0E-7) - 1;
        int i1 = Mth.floor(box.maxX + 1.0E-7) + 1;
        int i2 = Mth.floor(box.minY - 1.0E-7) - 1;
        int i3 = Mth.floor(box.maxY + 1.0E-7) + 1;
        int i4 = Mth.floor(box.minZ - 1.0E-7) - 1;
        int i5 = Mth.floor(box.maxZ + 1.0E-7) + 1;
        this.cursor = new Cursor3D(i, i2, i4, i1, i3, i5);
    }

    private @Nullable BlockGetter getChunk(int x, int z) {
        int sectionPosX = SectionPos.blockToSectionCoord(x);
        int sectionPosZ = SectionPos.blockToSectionCoord(z);
        long packedChunkPos = ChunkPos.asLong(sectionPosX, sectionPosZ);
        if (this.cachedBlockGetter != null && this.cachedBlockGetterPos == packedChunkPos) {
            return this.cachedBlockGetter;
        } else {
            BlockGetter chunkForCollisions = this.collisionGetter.getChunkForCollisions(sectionPosX, sectionPosZ);
            this.cachedBlockGetter = chunkForCollisions;
            this.cachedBlockGetterPos = packedChunkPos;
            return chunkForCollisions;
        }
    }

    @Override
    protected T computeNext() {
        while (this.cursor.advance()) {
            int i = this.cursor.nextX();
            int i1 = this.cursor.nextY();
            int i2 = this.cursor.nextZ();
            int nextType = this.cursor.getNextType();
            if (nextType != 3) {
                BlockGetter chunk = this.getChunk(i, i2);
                if (chunk != null) {
                    this.pos.set(i, i1, i2);
                    BlockState blockState = chunk.getBlockState(this.pos);
                    if ((!this.onlySuffocatingBlocks || blockState.isSuffocating(chunk, this.pos))
                        && (nextType != 1 || blockState.hasLargeCollisionShape())
                        && (nextType != 2 || blockState.is(Blocks.MOVING_PISTON))) {
                        VoxelShape collisionShape = this.context.getCollisionShape(blockState, this.collisionGetter, this.pos);
                        if (collisionShape == Shapes.block()) {
                            if (this.box.intersects(i, i1, i2, i + 1.0, i1 + 1.0, i2 + 1.0)) {
                                return this.resultProvider.apply(this.pos, collisionShape.move(this.pos));
                            }
                        } else {
                            VoxelShape voxelShape = collisionShape.move(this.pos);
                            if (!voxelShape.isEmpty() && Shapes.joinIsNotEmpty(voxelShape, this.entityShape, BooleanOp.AND)) {
                                return this.resultProvider.apply(this.pos, voxelShape);
                            }
                        }
                    }
                }
            }
        }

        return this.endOfData();
    }
}
