package net.minecraft.world.entity.ai.behavior.declarative;

import com.mojang.datafixers.kinds.Const;
import com.mojang.datafixers.kinds.IdF;
import com.mojang.datafixers.kinds.K1;
import com.mojang.datafixers.kinds.OptionalBox;
import com.mojang.datafixers.kinds.Const.Mu;
import com.mojang.datafixers.util.Unit;
import java.util.Optional;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jspecify.annotations.Nullable;

public interface MemoryCondition<F extends K1, Value> {
    MemoryModuleType<Value> memory();

    MemoryStatus condition();

    @Nullable MemoryAccessor<F, Value> createAccessor(Brain<?> brain, Optional<Value> memory);

    public record Absent<Value>(@Override MemoryModuleType<Value> memory) implements MemoryCondition<Mu<Unit>, Value> {
        @Override
        public MemoryStatus condition() {
            return MemoryStatus.VALUE_ABSENT;
        }

        @Override
        public MemoryAccessor<Mu<Unit>, Value> createAccessor(Brain<?> brain, Optional<Value> memory) {
            return memory.isPresent() ? null : new MemoryAccessor<>(brain, this.memory, Const.create(Unit.INSTANCE));
        }
    }

    public record Present<Value>(@Override MemoryModuleType<Value> memory) implements MemoryCondition<com.mojang.datafixers.kinds.IdF.Mu, Value> {
        @Override
        public MemoryStatus condition() {
            return MemoryStatus.VALUE_PRESENT;
        }

        @Override
        public MemoryAccessor<com.mojang.datafixers.kinds.IdF.Mu, Value> createAccessor(Brain<?> brain, Optional<Value> memory) {
            return memory.isEmpty() ? null : new MemoryAccessor<>(brain, this.memory, IdF.create(memory.get()));
        }
    }

    public record Registered<Value>(@Override MemoryModuleType<Value> memory) implements MemoryCondition<com.mojang.datafixers.kinds.OptionalBox.Mu, Value> {
        @Override
        public MemoryStatus condition() {
            return MemoryStatus.REGISTERED;
        }

        @Override
        public MemoryAccessor<com.mojang.datafixers.kinds.OptionalBox.Mu, Value> createAccessor(Brain<?> brain, Optional<Value> memory) {
            return new MemoryAccessor<>(brain, this.memory, OptionalBox.create(memory));
        }
    }
}
