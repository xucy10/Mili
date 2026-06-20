package net.minecraft.world.level.chunk.status;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.Locale;

public final class ChunkDependencies {
    private final ImmutableList<ChunkStatus> dependencyByRadius;
    private final int[] radiusByDependency;

    public ChunkDependencies(ImmutableList<ChunkStatus> dependencyByRadius) {
        this.dependencyByRadius = dependencyByRadius;
        int i = dependencyByRadius.isEmpty() ? 0 : dependencyByRadius.getFirst().getIndex() + 1;
        this.radiusByDependency = new int[i];

        for (int i1 = 0; i1 < dependencyByRadius.size(); i1++) {
            ChunkStatus chunkStatus = dependencyByRadius.get(i1);
            int index = chunkStatus.getIndex();

            for (int i2 = 0; i2 <= index; i2++) {
                this.radiusByDependency[i2] = i1;
            }
        }
    }

    @VisibleForTesting
    public ImmutableList<ChunkStatus> asList() {
        return this.dependencyByRadius;
    }

    public int size() {
        return this.dependencyByRadius.size();
    }

    public int getRadiusOf(ChunkStatus status) {
        int index = status.getIndex();
        if (index >= this.radiusByDependency.length) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Requesting a ChunkStatus(%s) outside of dependency range(%s)", status, this.dependencyByRadius)
            );
        } else {
            return this.radiusByDependency[index];
        }
    }

    public int getRadius() {
        return Math.max(0, this.dependencyByRadius.size() - 1);
    }

    public ChunkStatus get(int radius) {
        return this.dependencyByRadius.get(radius);
    }

    @Override
    public String toString() {
        return this.dependencyByRadius.toString();
    }
}
