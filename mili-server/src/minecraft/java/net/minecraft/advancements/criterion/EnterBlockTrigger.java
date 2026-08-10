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

public class EnterBlockTrigger extends SimpleCriterionTrigger<EnterBlockTrigger.TriggerInstance> {
    @Override
    public Codec<EnterBlockTrigger.TriggerInstance> codec() {
        return EnterBlockTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockState state) {
        this.trigger(player, trigger -> trigger.matches(state));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<Holder<Block>> block, Optional<StatePropertiesPredicate> state)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<EnterBlockTrigger.TriggerInstance> CODEC = RecordCodecBuilder.<EnterBlockTrigger.TriggerInstance>create(
                instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(EnterBlockTrigger.TriggerInstance::player),
                        BuiltInRegistries.BLOCK.holderByNameCodec().optionalFieldOf("block").forGetter(EnterBlockTrigger.TriggerInstance::block),
                        StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(EnterBlockTrigger.TriggerInstance::state)
                    )
                    .apply(instance, EnterBlockTrigger.TriggerInstance::new)
            )
            .validate(EnterBlockTrigger.TriggerInstance::validate);

        private static DataResult<EnterBlockTrigger.TriggerInstance> validate(EnterBlockTrigger.TriggerInstance triggerInstance) {
            return triggerInstance.block
                .<DataResult<EnterBlockTrigger.TriggerInstance>>flatMap(
                    holder -> triggerInstance.state
                        .<String>flatMap(statePropertiesPredicate -> statePropertiesPredicate.checkState(((Block)holder.value()).getStateDefinition()))
                        .map(string -> DataResult.error(() -> "Block" + holder + " has no property " + string))
                )
                .orElseGet(() -> DataResult.success(triggerInstance));
        }

        public static Criterion<EnterBlockTrigger.TriggerInstance> entersBlock(Block block) {
            return CriteriaTriggers.ENTER_BLOCK
                .createCriterion(new EnterBlockTrigger.TriggerInstance(Optional.empty(), Optional.of(block.builtInRegistryHolder()), Optional.empty()));
        }

        public boolean matches(BlockState state) {
            return (!this.block.isPresent() || state.is(this.block.get())) && (!this.state.isPresent() || this.state.get().matches(state));
        }
    }
}
