package net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

public class AppendLoot implements RuleBlockEntityModifier {
    public static final MapCodec<AppendLoot> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LootTable.KEY_CODEC.fieldOf("loot_table").forGetter(appendLoot -> appendLoot.lootTable)).apply(instance, AppendLoot::new)
    );
    private final ResourceKey<LootTable> lootTable;

    public AppendLoot(ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public CompoundTag apply(RandomSource random, @Nullable CompoundTag tag) {
        CompoundTag compoundTag = tag == null ? new CompoundTag() : tag.copy();
        compoundTag.store("LootTable", LootTable.KEY_CODEC, this.lootTable);
        compoundTag.putLong("LootTableSeed", random.nextLong());
        return compoundTag;
    }

    @Override
    public RuleBlockEntityModifierType<?> getType() {
        return RuleBlockEntityModifierType.APPEND_LOOT;
    }
}
