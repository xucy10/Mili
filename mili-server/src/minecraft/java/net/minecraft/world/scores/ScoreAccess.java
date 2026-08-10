package net.minecraft.world.scores;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import org.jspecify.annotations.Nullable;

public interface ScoreAccess {
    int get();

    void set(int value);

    default int add(int increment) {
        int i = this.get() + increment;
        this.set(i);
        return i;
    }

    default int increment() {
        return this.add(1);
    }

    default void reset() {
        this.set(0);
    }

    boolean locked();

    void unlock();

    void lock();

    @Nullable Component display();

    void display(@Nullable Component value);

    void numberFormatOverride(@Nullable NumberFormat format);
}
