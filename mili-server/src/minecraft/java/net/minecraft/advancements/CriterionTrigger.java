package net.minecraft.advancements;

import com.mojang.serialization.Codec;
import net.minecraft.server.PlayerAdvancements;

public interface CriterionTrigger<T extends CriterionTriggerInstance> {
    void addPlayerListener(PlayerAdvancements playerAdvancements, CriterionTrigger.Listener<T> listener);

    void removePlayerListener(PlayerAdvancements playerAdvancements, CriterionTrigger.Listener<T> listener);

    void removePlayerListeners(PlayerAdvancements playerAdvancements);

    Codec<T> codec();

    default Criterion<T> createCriterion(T triggerInstance) {
        return new Criterion<>(this, triggerInstance);
    }

    public record Listener<T extends CriterionTriggerInstance>(T trigger, AdvancementHolder advancement, String criterion) {
        public void run(PlayerAdvancements playerAdvancements) {
            playerAdvancements.award(this.advancement, this.criterion);
        }
    }
}
