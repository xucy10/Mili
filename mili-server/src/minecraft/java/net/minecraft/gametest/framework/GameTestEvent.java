package net.minecraft.gametest.framework;

import org.jspecify.annotations.Nullable;

class GameTestEvent {
    public final @Nullable Long expectedDelay;
    public final Runnable assertion;

    private GameTestEvent(@Nullable Long expectedDelay, Runnable assertion) {
        this.expectedDelay = expectedDelay;
        this.assertion = assertion;
    }

    static GameTestEvent create(Runnable assertion) {
        return new GameTestEvent(null, assertion);
    }

    static GameTestEvent create(long expectedDelay, Runnable assertion) {
        return new GameTestEvent(expectedDelay, assertion);
    }
}
