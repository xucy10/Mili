package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.util.Util;

public interface InsideBlockEffectApplier {
    InsideBlockEffectApplier NOOP = new InsideBlockEffectApplier() {
        @Override
        public void apply(InsideBlockEffectType type) {
        }

        @Override
        public void runBefore(InsideBlockEffectType type, Consumer<Entity> effect) {
        }

        @Override
        public void runAfter(InsideBlockEffectType type, Consumer<Entity> effect) {
        }
    };

    void apply(InsideBlockEffectType type);

    void runBefore(InsideBlockEffectType type, Consumer<Entity> effect);

    void runAfter(InsideBlockEffectType type, Consumer<Entity> effect);

    public static class StepBasedCollector implements InsideBlockEffectApplier {
        private static final InsideBlockEffectType[] APPLY_ORDER = InsideBlockEffectType.values();
        private static final int NO_STEP = -1;
        private final Map<InsideBlockEffectType, Consumer<Entity>> effectsInStep = new java.util.EnumMap<>(InsideBlockEffectType.class); // Paper - track position inside effect was triggered on
        private final Map<InsideBlockEffectType, List<Consumer<Entity>>> beforeEffectsInStep = Util.makeEnumMap(
            InsideBlockEffectType.class, insideBlockEffectType -> new ArrayList<>()
        );
        private final Map<InsideBlockEffectType, List<Consumer<Entity>>> afterEffectsInStep = Util.makeEnumMap(
            InsideBlockEffectType.class, insideBlockEffectType -> new ArrayList<>()
        );
        private final List<Consumer<Entity>> finalEffects = new ArrayList<>();
        private int lastStep = -1;

        public void advanceStep(int step, net.minecraft.core.BlockPos pos) { // Paper - track position inside effect was triggered on
            this.currentBlockPos = pos; // Paper - track position inside effect was triggered on
            if (this.lastStep != step) {
                this.lastStep = step;
                this.flushStep();
            }
        }

        public void applyAndClear(Entity entity) {
            this.flushStep();

            for (Consumer<Entity> consumer : this.finalEffects) {
                if (!entity.isAlive()) {
                    break;
                }

                consumer.accept(entity);
            }

            this.finalEffects.clear();
            this.lastStep = -1;
        }

        private void flushStep() {
            for (InsideBlockEffectType insideBlockEffectType : APPLY_ORDER) {
                List<Consumer<Entity>> list = this.beforeEffectsInStep.get(insideBlockEffectType);
                this.finalEffects.addAll(list);
                list.clear();
                if (this.effectsInStep.remove(insideBlockEffectType) instanceof final Consumer<Entity> recordedEffect) { // Paper - track position inside effect was triggered on - better than null check to avoid diff.
                    this.finalEffects.add(recordedEffect); // Paper - track position inside effect was triggered on
                }

                List<Consumer<Entity>> list1 = this.afterEffectsInStep.get(insideBlockEffectType);
                this.finalEffects.addAll(list1);
                list1.clear();
            }
        }

        @Override
        public void apply(InsideBlockEffectType type) {
            this.effectsInStep.put(type, recorded(type));  // Paper - track position inside effect was triggered on
        }

        @Override
        public void runBefore(InsideBlockEffectType type, Consumer<Entity> effect) {
            this.beforeEffectsInStep.get(type).add(effect);
        }

        @Override
        public void runAfter(InsideBlockEffectType type, Consumer<Entity> effect) {
            this.afterEffectsInStep.get(type).add(effect);
        }

        // Paper start - track position inside effect was triggered on
        private net.minecraft.core.BlockPos currentBlockPos = null;

        private Consumer<Entity> recorded(final InsideBlockEffectType type) {
            return new RecordedEffect(this.currentBlockPos.immutable(), type.effect());
        }

        record RecordedEffect(
            net.minecraft.core.BlockPos blockPos,
            InsideBlockEffectType.Applier applier
        ) implements Consumer<Entity> {

            @Override
            public void accept(final Entity entity) {
                this.applier.affect(entity, blockPos);
            }
        }
        // Paper end - track position inside effect was triggered on
    }
}
