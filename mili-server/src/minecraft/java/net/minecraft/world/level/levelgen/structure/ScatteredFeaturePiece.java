package net.minecraft.world.level.levelgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public abstract class ScatteredFeaturePiece extends StructurePiece {
    protected final int width;
    protected final int height;
    protected final int depth;
    protected int heightPosition = -1;

    protected ScatteredFeaturePiece(StructurePieceType type, int x, int y, int z, int width, int height, int depth, Direction orientation) {
        super(type, 0, StructurePiece.makeBoundingBox(x, y, z, orientation, width, height, depth));
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.setOrientation(orientation);
    }

    protected ScatteredFeaturePiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
        this.width = tag.getIntOr("Width", 0);
        this.height = tag.getIntOr("Height", 0);
        this.depth = tag.getIntOr("Depth", 0);
        this.heightPosition = tag.getIntOr("HPos", 0);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Width", this.width);
        tag.putInt("Height", this.height);
        tag.putInt("Depth", this.depth);
        tag.putInt("HPos", this.heightPosition);
    }

    protected boolean updateAverageGroundHeight(LevelAccessor level, BoundingBox bounds, int height) {
        if (this.heightPosition >= 0) {
            return true;
        } else {
            int i = 0;
            int i1 = 0;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int z = this.boundingBox.minZ(); z <= this.boundingBox.maxZ(); z++) {
                for (int x = this.boundingBox.minX(); x <= this.boundingBox.maxX(); x++) {
                    mutableBlockPos.set(x, 64, z);
                    if (bounds.isInside(mutableBlockPos)) {
                        i += level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mutableBlockPos).getY();
                        i1++;
                    }
                }
            }

            if (i1 == 0) {
                return false;
            } else {
                this.heightPosition = i / i1;
                this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + height, 0);
                return true;
            }
        }
    }

    protected boolean updateHeightPositionToLowestGroundHeight(LevelAccessor level, int height) {
        if (this.heightPosition >= 0) {
            return true;
        } else {
            int i = level.getMaxY() + 1;
            boolean flag = false;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int z = this.boundingBox.minZ(); z <= this.boundingBox.maxZ(); z++) {
                for (int x = this.boundingBox.minX(); x <= this.boundingBox.maxX(); x++) {
                    mutableBlockPos.set(x, 0, z);
                    i = Math.min(i, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mutableBlockPos).getY());
                    flag = true;
                }
            }

            if (!flag) {
                return false;
            } else {
                this.heightPosition = i;
                this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + height, 0);
                return true;
            }
        }
    }
}
