package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class SlideDownBlockTrigger extends SimpleCriterionTrigger<SlideDownBlockTrigger.TriggerInstance> {
    @Override
    public Codec<SlideDownBlockTrigger.TriggerInstance> codec() {
        return SlideDownBlockTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockState state) {
        this.trigger(player, trigger -> trigger.matches(state));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<Holder<Block>> block, Optional<StatePropertiesPredicate> state)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<SlideDownBlockTrigger.TriggerInstance> CODEC = RecordCodecBuilder.<SlideDownBlockTrigger.TriggerInstance>create(
                instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(SlideDownBlockTrigger.TriggerInstance::player),
                        BuiltInRegistries.BLOCK.holderByNameCodec().optionalFieldOf("block").forGetter(SlideDownBlockTrigger.TriggerInstance::block),
                        StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(SlideDownBlockTrigger.TriggerInstance::state)
                    )
                    .apply(instance, SlideDownBlockTrigger.TriggerInstance::new)
            )
            .validate(SlideDownBlockTrigger.TriggerInstance::validate);

        private static DataResult<SlideDownBlockTrigger.TriggerInstance> validate(SlideDownBlockTrigger.TriggerInstance triggerInstance) {
            return triggerInstance.block
                .<DataResult<SlideDownBlockTrigger.TriggerInstance>>flatMap(
                    block -> triggerInstance.state
                        .<String>flatMap(predicate -> predicate.checkState(((Block)block.value()).getStateDefinition()))
                        .map(stateProperty -> DataResult.error(() -> "Block" + block + " has no property " + stateProperty))
                )
                .orElseGet(() -> DataResult.success(triggerInstance));
        }

        public static Criterion<SlideDownBlockTrigger.TriggerInstance> slidesDownBlock(Block block) {
            return CriteriaTriggers.HONEY_BLOCK_SLIDE
                .createCriterion(new SlideDownBlockTrigger.TriggerInstance(Optional.empty(), Optional.of(block.builtInRegistryHolder()), Optional.empty()));
        }

        public boolean matches(BlockState state) {
            return (!this.block.isPresent() || state.is(this.block.get())) && (!this.state.isPresent() || this.state.get().matches(state));
        }
    }
}
