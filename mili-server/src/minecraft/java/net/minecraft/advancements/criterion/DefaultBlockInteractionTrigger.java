package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class DefaultBlockInteractionTrigger extends SimpleCriterionTrigger<DefaultBlockInteractionTrigger.TriggerInstance> {
    @Override
    public Codec<DefaultBlockInteractionTrigger.TriggerInstance> codec() {
        return DefaultBlockInteractionTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockPos pos) {
        ServerLevel serverLevel = player.level();
        BlockState blockState = serverLevel.getBlockState(pos);
        LootParams lootParams = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.ORIGIN, pos.getCenter())
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .withParameter(LootContextParams.BLOCK_STATE, blockState)
            .create(LootContextParamSets.BLOCK_USE);
        LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
        this.trigger(player, triggerInstance -> triggerInstance.matches(lootContext));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> location)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<DefaultBlockInteractionTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(DefaultBlockInteractionTrigger.TriggerInstance::player),
                    ContextAwarePredicate.CODEC.optionalFieldOf("location").forGetter(DefaultBlockInteractionTrigger.TriggerInstance::location)
                )
                .apply(instance, DefaultBlockInteractionTrigger.TriggerInstance::new)
        );

        public boolean matches(LootContext context) {
            return this.location.isEmpty() || this.location.get().matches(context);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            this.location.ifPresent(contextAwarePredicate -> validator.validate(contextAwarePredicate, LootContextParamSets.BLOCK_USE, "location"));
        }
    }
}
