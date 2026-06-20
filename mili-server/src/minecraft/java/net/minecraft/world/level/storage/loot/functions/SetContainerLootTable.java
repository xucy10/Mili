package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetContainerLootTable extends LootItemConditionalFunction {
    public static final MapCodec<SetContainerLootTable> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    LootTable.KEY_CODEC.fieldOf("name").forGetter(setContainerLootTable -> setContainerLootTable.name),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(setContainerLootTable -> setContainerLootTable.seed),
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.holderByNameCodec().fieldOf("type").forGetter(setContainerLootTable -> setContainerLootTable.type)
                )
            )
            .apply(instance, SetContainerLootTable::new)
    );
    private final ResourceKey<LootTable> name;
    private final long seed;
    private final Holder<BlockEntityType<?>> type;

    private SetContainerLootTable(List<LootItemCondition> predicates, ResourceKey<LootTable> name, long seed, Holder<BlockEntityType<?>> type) {
        super(predicates);
        this.name = name;
        this.seed = seed;
        this.type = type;
    }

    @Override
    public LootItemFunctionType<SetContainerLootTable> getType() {
        return LootItemFunctions.SET_LOOT_TABLE;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isEmpty()) {
            return stack;
        } else {
            stack.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(this.name, this.seed));
            return stack;
        }
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        if (!context.allowsReferences()) {
            context.reportProblem(new ValidationContext.ReferenceNotAllowedProblem(this.name));
        } else {
            if (context.resolver().get(this.name).isEmpty()) {
                context.reportProblem(new ValidationContext.MissingReferenceProblem(this.name));
            }
        }
    }

    public static LootItemConditionalFunction.Builder<?> withLootTable(BlockEntityType<?> type, ResourceKey<LootTable> lootTable) {
        return simpleBuilder(list -> new SetContainerLootTable(list, lootTable, 0L, type.builtInRegistryHolder()));
    }

    public static LootItemConditionalFunction.Builder<?> withLootTable(BlockEntityType<?> type, ResourceKey<LootTable> lootTable, long seed) {
        return simpleBuilder(list -> new SetContainerLootTable(list, lootTable, seed, type.builtInRegistryHolder()));
    }
}
