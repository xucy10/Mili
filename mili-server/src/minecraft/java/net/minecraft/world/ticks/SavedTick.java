package net.minecraft.world.ticks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.Hash.Strategy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public record SavedTick<T>(T type, BlockPos pos, int delay, TickPriority priority) {
    public static final Strategy<SavedTick<?>> UNIQUE_TICK_HASH = new Strategy<SavedTick<?>>() {
        @Override
        public int hashCode(SavedTick<?> savedTick) {
            return 31 * savedTick.pos().hashCode() + savedTick.type().hashCode();
        }

        @Override
        public boolean equals(@Nullable SavedTick<?> first, @Nullable SavedTick<?> second) {
            return first == second || first != null && second != null && first.type() == second.type() && first.pos().equals(second.pos());
        }
    };

    public static <T> Codec<SavedTick<T>> codec(Codec<T> typeCodec) {
        MapCodec<BlockPos> mapCodec = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.INT.fieldOf("x").forGetter(Vec3i::getX), Codec.INT.fieldOf("y").forGetter(Vec3i::getY), Codec.INT.fieldOf("z").forGetter(Vec3i::getZ)
                )
                .apply(instance, BlockPos::new)
        );
        return RecordCodecBuilder.create(
            instance -> instance.group(
                    typeCodec.fieldOf("i").forGetter(SavedTick::type),
                    mapCodec.forGetter(SavedTick::pos),
                    Codec.INT.fieldOf("t").forGetter(SavedTick::delay),
                    TickPriority.CODEC.fieldOf("p").forGetter(SavedTick::priority)
                )
                .apply(instance, SavedTick::new)
        );
    }

    public static <T> List<SavedTick<T>> filterTickListForChunk(List<SavedTick<T>> tickList, ChunkPos chunkPos) {
        long packedChunkPos = chunkPos.toLong();
        return tickList.stream().filter(savedTick -> ChunkPos.asLong(savedTick.pos()) == packedChunkPos).toList();
    }

    public ScheduledTick<T> unpack(long gameTime, long subTickOrder) {
        return new ScheduledTick<>(this.type, this.pos, gameTime + this.delay, this.priority, subTickOrder);
    }

    public static <T> SavedTick<T> probe(T type, BlockPos pos) {
        return new SavedTick<>(type, pos, 0, TickPriority.NORMAL);
    }
}
