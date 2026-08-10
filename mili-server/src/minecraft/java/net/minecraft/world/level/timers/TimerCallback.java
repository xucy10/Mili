package net.minecraft.world.level.timers;

import com.mojang.serialization.MapCodec;

public interface TimerCallback<T> {
    void handle(T obj, TimerQueue<T> manager, long gameTime);

    MapCodec<? extends TimerCallback<T>> codec();
}
