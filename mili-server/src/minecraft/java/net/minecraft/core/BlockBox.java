package net.minecraft.core;

import io.netty.buffer.ByteBuf;
import java.util.Iterator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;

public record BlockBox(BlockPos min, BlockPos max) implements Iterable<BlockPos> {
    public static final StreamCodec<ByteBuf, BlockBox> STREAM_CODEC = new StreamCodec<ByteBuf, BlockBox>() {
        @Override
        public BlockBox decode(ByteBuf buffer) {
            return new BlockBox(FriendlyByteBuf.readBlockPos(buffer), FriendlyByteBuf.readBlockPos(buffer));
        }

        @Override
        public void encode(ByteBuf buffer, BlockBox value) {
            FriendlyByteBuf.writeBlockPos(buffer, value.min());
            FriendlyByteBuf.writeBlockPos(buffer, value.max());
        }
    };

    public BlockBox(final BlockPos min, final BlockPos max) {
        this.min = BlockPos.min(min, max);
        this.max = BlockPos.max(min, max);
    }

    public static BlockBox of(BlockPos pos) {
        return new BlockBox(pos, pos);
    }

    public static BlockBox of(BlockPos pos1, BlockPos pos2) {
        return new BlockBox(pos1, pos2);
    }

    public BlockBox include(BlockPos pos) {
        return new BlockBox(BlockPos.min(this.min, pos), BlockPos.max(this.max, pos));
    }

    public boolean isBlock() {
        return this.min.equals(this.max);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.min.getX()
            && pos.getY() >= this.min.getY()
            && pos.getZ() >= this.min.getZ()
            && pos.getX() <= this.max.getX()
            && pos.getY() <= this.max.getY()
            && pos.getZ() <= this.max.getZ();
    }

    public AABB aabb() {
        return AABB.encapsulatingFullBlocks(this.min, this.max);
    }

    @Override
    public Iterator<BlockPos> iterator() {
        return BlockPos.betweenClosed(this.min, this.max).iterator();
    }

    public int sizeX() {
        return this.max.getX() - this.min.getX() + 1;
    }

    public int sizeY() {
        return this.max.getY() - this.min.getY() + 1;
    }

    public int sizeZ() {
        return this.max.getZ() - this.min.getZ() + 1;
    }

    public BlockBox extend(Direction direction, int amount) {
        if (amount == 0) {
            return this;
        } else {
            return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? of(this.min, BlockPos.max(this.min, this.max.relative(direction, amount)))
                : of(BlockPos.min(this.min.relative(direction, amount), this.max), this.max);
        }
    }

    public BlockBox move(Direction direction, int amount) {
        return amount == 0 ? this : new BlockBox(this.min.relative(direction, amount), this.max.relative(direction, amount));
    }

    public BlockBox offset(Vec3i vector) {
        return new BlockBox(this.min.offset(vector), this.max.offset(vector));
    }
}
