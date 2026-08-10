package net.minecraft.world.entity.ai.behavior.declarative;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.K1;
import java.util.Optional;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public final class MemoryAccessor<F extends K1, Value> {
    private final Brain<?> brain;
    private final MemoryModuleType<Value> memoryType;
    private final App<F, Value> value;

    public MemoryAccessor(Brain<?> brain, MemoryModuleType<Value> memoryType, App<F, Value> value) {
        this.brain = brain;
        this.memoryType = memoryType;
        this.value = value;
    }

    public App<F, Value> value() {
        return this.value;
    }

    public void set(Value value) {
        this.brain.setMemory(this.memoryType, Optional.of(value));
    }

    public void setOrErase(Optional<Value> memory) {
        this.brain.setMemory(this.memoryType, memory);
    }

    public void setWithExpiry(Value memory, long expiry) {
        this.brain.setMemoryWithExpiry(this.memoryType, memory, expiry);
    }

    public void erase() {
        this.brain.eraseMemory(this.memoryType);
    }
}
