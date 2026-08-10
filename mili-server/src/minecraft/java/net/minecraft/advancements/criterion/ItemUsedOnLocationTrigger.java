package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;

public class ItemUsedOnLocationTrigger extends SimpleCriterionTrigger<ItemUsedOnLocationTrigger.TriggerInstance> {
    @Override
    public Codec<ItemUsedOnLocationTrigger.TriggerInstance> codec() {
        return ItemUsedOnLocationTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockPos pos, ItemStack stack) {
        ServerLevel serverLevel = player.level();
        BlockState blockState = serverLevel.getBlockState(pos);
        LootParams lootParams = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.ORIGIN, pos.getCenter())
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .withParameter(LootContextParams.BLOCK_STATE, blockState)
            .withParameter(LootContextParams.TOOL, stack)
            .create(LootContextParamSets.ADVANCEMENT_LOCATION);
        LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
        this.trigger(player, trigger -> trigger.matches(lootContext));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> location)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<ItemUsedOnLocationTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(ItemUsedOnLocationTrigger.TriggerInstance::player),
                    ContextAwarePredicate.CODEC.optionalFieldOf("location").forGetter(ItemUsedOnLocationTrigger.TriggerInstance::location)
                )
                .apply(instance, ItemUsedOnLocationTrigger.TriggerInstance::new)
        );

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlock(Block block) {
            ContextAwarePredicate contextAwarePredicate = ContextAwarePredicate.create(
                LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).build()
            );
            return CriteriaTriggers.PLACED_BLOCK
                .createCriterion(new ItemUsedOnLocationTrigger.TriggerInstance(Optional.empty(), Optional.of(contextAwarePredicate)));
        }

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlock(LootItemCondition.Builder... conditions) {
            ContextAwarePredicate contextAwarePredicate = ContextAwarePredicate.create(
                Arrays.stream(conditions).map(LootItemCondition.Builder::build).toArray(LootItemCondition[]::new)
            );
            return CriteriaTriggers.PLACED_BLOCK
                .createCriterion(new ItemUsedOnLocationTrigger.TriggerInstance(Optional.empty(), Optional.of(contextAwarePredicate)));
        }

        public static <T extends Comparable<T>> Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlockWithProperties(
            Block block, Property<T> property, String value
        ) {
            StatePropertiesPredicate.Builder builder = StatePropertiesPredicate.Builder.properties().hasProperty(property, value);
            ContextAwarePredicate contextAwarePredicate = ContextAwarePredicate.create(
                LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).setProperties(builder).build()
            );
            return CriteriaTriggers.PLACED_BLOCK
                .createCriterion(new ItemUsedOnLocationTrigger.TriggerInstance(Optional.empty(), Optional.of(contextAwarePredicate)));
        }

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlockWithProperties(Block block, Property<Boolean> property, boolean value) {
            return placedBlockWithProperties(block, property, String.valueOf(value));
        }

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlockWithProperties(Block block, Property<Integer> property, int value) {
            return placedBlockWithProperties(block, property, String.valueOf(value));
        }

        public static <T extends Comparable<T> & StringRepresentable> Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placedBlockWithProperties(
            Block block, Property<T> property, T value
        ) {
            return placedBlockWithProperties(block, property, value.getSerializedName());
        }

        private static ItemUsedOnLocationTrigger.TriggerInstance itemUsedOnLocation(LocationPredicate.Builder location, ItemPredicate.Builder tool) {
            ContextAwarePredicate contextAwarePredicate = ContextAwarePredicate.create(
                LocationCheck.checkLocation(location).build(), MatchTool.toolMatches(tool).build()
            );
            return new ItemUsedOnLocationTrigger.TriggerInstance(Optional.empty(), Optional.of(contextAwarePredicate));
        }

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> itemUsedOnBlock(LocationPredicate.Builder location, ItemPredicate.Builder tool) {
            return CriteriaTriggers.ITEM_USED_ON_BLOCK.createCriterion(itemUsedOnLocation(location, tool));
        }

        public static Criterion<ItemUsedOnLocationTrigger.TriggerInstance> allayDropItemOnBlock(LocationPredicate.Builder location, ItemPredicate.Builder tool) {
            return CriteriaTriggers.ALLAY_DROP_ITEM_ON_BLOCK.createCriterion(itemUsedOnLocation(location, tool));
        }

        public boolean matches(LootContext context) {
            return this.location.isEmpty() || this.location.get().matches(context);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            this.location.ifPresent(contextAwarePredicate -> validator.validate(contextAwarePredicate, LootContextParamSets.ADVANCEMENT_LOCATION, "location"));
        }
    }
}
