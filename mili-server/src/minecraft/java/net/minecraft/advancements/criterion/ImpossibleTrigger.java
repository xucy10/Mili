package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.server.PlayerAdvancements;

public class ImpossibleTrigger implements CriterionTrigger<ImpossibleTrigger.TriggerInstance> {
    @Override
    public void addPlayerListener(PlayerAdvancements playerAdvancements, CriterionTrigger.Listener<ImpossibleTrigger.TriggerInstance> listener) {
    }

    @Override
    public void removePlayerListener(PlayerAdvancements playerAdvancements, CriterionTrigger.Listener<ImpossibleTrigger.TriggerInstance> listener) {
    }

    @Override
    public void removePlayerListeners(PlayerAdvancements playerAdvancements) {
    }

    @Override
    public Codec<ImpossibleTrigger.TriggerInstance> codec() {
        return ImpossibleTrigger.TriggerInstance.CODEC;
    }

    public record TriggerInstance() implements CriterionTriggerInstance {
        public static final Codec<ImpossibleTrigger.TriggerInstance> CODEC = MapCodec.unitCodec(new ImpossibleTrigger.TriggerInstance());

        @Override
        public void validate(CriterionValidator validator) {
        }
    }
}
