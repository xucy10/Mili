package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

public interface ChunkTrackingView {
    ChunkTrackingView EMPTY = new ChunkTrackingView() {
        @Override
        public boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
            return false;
        }

        @Override
        public void forEach(Consumer<ChunkPos> action) {
        }
    };

    static ChunkTrackingView of(ChunkPos center, int viewDistance) {
        return new ChunkTrackingView.Positioned(center, viewDistance);
    }

    static void difference(
        ChunkTrackingView oldChunkTrackingView, ChunkTrackingView newChunkTrackingView, Consumer<ChunkPos> chunkMarker, Consumer<ChunkPos> chunkDropper
    ) {
        if (!oldChunkTrackingView.equals(newChunkTrackingView)) {
            if (oldChunkTrackingView instanceof ChunkTrackingView.Positioned positioned
                && newChunkTrackingView instanceof ChunkTrackingView.Positioned positioned1
                && positioned.squareIntersects(positioned1)) {
                int min = Math.min(positioned.minX(), positioned1.minX());
                int min1 = Math.min(positioned.minZ(), positioned1.minZ());
                int max = Math.max(positioned.maxX(), positioned1.maxX());
                int max1 = Math.max(positioned.maxZ(), positioned1.maxZ());

                for (int i = min; i <= max; i++) {
                    for (int i1 = min1; i1 <= max1; i1++) {
                        boolean flag = positioned.contains(i, i1);
                        boolean flag1 = positioned1.contains(i, i1);
                        if (flag != flag1) {
                            if (flag1) {
                                chunkMarker.accept(new ChunkPos(i, i1));
                            } else {
                                chunkDropper.accept(new ChunkPos(i, i1));
                            }
                        }
                    }
                }
            } else {
                oldChunkTrackingView.forEach(chunkDropper);
                newChunkTrackingView.forEach(chunkMarker);
            }
        }
    }

    default boolean contains(ChunkPos chunkPos) {
        return this.contains(chunkPos.x, chunkPos.z);
    }

    default boolean contains(int x, int z) {
        return this.contains(x, z, true);
    }

    boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder);

    void forEach(Consumer<ChunkPos> action);

    default boolean isInViewDistance(int x, int z) {
        return this.contains(x, z, false);
    }

    static boolean isInViewDistance(int centerX, int centerZ, int viewDistance, int x, int z) {
        return isWithinDistance(centerX, centerZ, viewDistance, x, z, false);
    }

    static boolean isWithinDistance(int centerX, int centerZ, int viewDistance, int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
        int i = includeOuterChunksAdjacentToViewBorder ? 2 : 1;
        long l = Math.max(0, Math.abs(x - centerX) - i);
        long l1 = Math.max(0, Math.abs(z - centerZ) - i);
        long l2 = l * l + l1 * l1;
        int i1 = viewDistance * viewDistance;
        return l2 < i1;
    }

    public record Positioned(ChunkPos center, int viewDistance) implements ChunkTrackingView {
        int minX() {
            return this.center.x - this.viewDistance - 1;
        }

        int minZ() {
            return this.center.z - this.viewDistance - 1;
        }

        int maxX() {
            return this.center.x + this.viewDistance + 1;
        }

        int maxZ() {
            return this.center.z + this.viewDistance + 1;
        }

        @VisibleForTesting
        protected boolean squareIntersects(ChunkTrackingView.Positioned other) {
            return this.minX() <= other.maxX() && this.maxX() >= other.minX() && this.minZ() <= other.maxZ() && this.maxZ() >= other.minZ();
        }

        @Override
        public boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
            return ChunkTrackingView.isWithinDistance(this.center.x, this.center.z, this.viewDistance, x, z, includeOuterChunksAdjacentToViewBorder);
        }

        @Override
        public void forEach(Consumer<ChunkPos> action) {
            for (int i = this.minX(); i <= this.maxX(); i++) {
                for (int i1 = this.minZ(); i1 <= this.maxZ(); i1++) {
                    if (this.contains(i, i1)) {
                        action.accept(new ChunkPos(i, i1));
                    }
                }
            }
        }
    }
}
