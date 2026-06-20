package net.minecraft.world.entity;

import java.util.function.Consumer;

public class AnimationState {
    private static final int STOPPED = Integer.MIN_VALUE;
    private int startTick = Integer.MIN_VALUE;

    public void start(int tickCount) {
        this.startTick = tickCount;
    }

    public void startIfStopped(int tickCount) {
        if (!this.isStarted()) {
            this.start(tickCount);
        }
    }

    public void animateWhen(boolean condition, int tickCount) {
        if (condition) {
            this.startIfStopped(tickCount);
        } else {
            this.stop();
        }
    }

    public void stop() {
        this.startTick = Integer.MIN_VALUE;
    }

    public void ifStarted(Consumer<AnimationState> action) {
        if (this.isStarted()) {
            action.accept(this);
        }
    }

    public void fastForward(int duration, float speed) {
        if (this.isStarted()) {
            this.startTick -= (int)(duration * speed);
        }
    }

    public long getTimeInMillis(float gameTime) {
        float f = gameTime - this.startTick;
        return (long)(f * 50.0F);
    }

    public boolean isStarted() {
        return this.startTick != Integer.MIN_VALUE;
    }

    public void copyFrom(AnimationState other) {
        this.startTick = other.startTick;
    }
}
