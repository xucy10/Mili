package net.minecraft.world.level.entity;

import net.minecraft.server.level.FullChunkStatus;

public enum Visibility {
    HIDDEN(false, false),
    TRACKED(true, false),
    TICKING(true, true);

    private final boolean accessible;
    private final boolean ticking;

    private Visibility(final boolean accessible, final boolean ticking) {
        this.accessible = accessible;
        this.ticking = ticking;
    }

    public boolean isTicking() {
        return this.ticking;
    }

    public boolean isAccessible() {
        return this.accessible;
    }

    public static Visibility fromFullChunkStatus(FullChunkStatus fullChunkStatus) {
        if (fullChunkStatus.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
            return TICKING;
        } else {
            return fullChunkStatus.isOrAfter(FullChunkStatus.FULL) ? TRACKED : HIDDEN;
        }
    }
}
