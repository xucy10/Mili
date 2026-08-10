package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BeeNestDestroyedTrigger extends SimpleCriterionTrigger<BeeNestDestroyedTrigger.TriggerInstance> {
    @Override
    public Codec<BeeNestDestroyedTrigger.TriggerInstance> codec() {
        return BeeNestDestroyedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockState state, ItemStack stack, int numBees) {
        this.trigger(player, instance -> instance.matches(state, stack, numBees));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<Holder<Block>> block, Optional<ItemPredicate> item, MinMaxBounds.Ints beesInside
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<BeeNestDestroyedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(BeeNestDestroyedTrigger.TriggerInstance::player),
                    BuiltInRegistries.BLOCK.holderByNameCodec().optionalFieldOf("block").forGetter(BeeNestDestroyedTrigger.TriggerInstance::block),
                    ItemPredicate.CODEC.optionalFieldOf("item").forGetter(BeeNestDestroyedTrigger.TriggerInstance::item),
                    MinMaxBounds.Ints.CODEC
                        .optionalFieldOf("num_bees_inside", MinMaxBounds.Ints.ANY)
                        .forGetter(BeeNestDestroyedTrigger.TriggerInstance::beesInside)
                )
                .apply(instance, BeeNestDestroyedTrigger.TriggerInstance::new)
        );

        public static Criterion<BeeNestDestroyedTrigger.TriggerInstance> destroyedBeeNest(Block block, ItemPredicate.Builder item, MinMaxBounds.Ints numBees) {
            return CriteriaTriggers.BEE_NEST_DESTROYED
                .createCriterion(
                    new BeeNestDestroyedTrigger.TriggerInstance(
                        Optional.empty(), Optional.of(block.builtInRegistryHolder()), Optional.of(item.build()), numBees
                    )
                );
        }

        public boolean matches(BlockState state, ItemStack stack, int numBees) {
            return (!this.block.isPresent() || state.is(this.block.get()))
                && (!this.item.isPresent() || this.item.get().test(stack))
                && this.beesInside.matches(numBees);
        }
    }
}
