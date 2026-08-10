package net.minecraft.world.entity;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

public record EquipmentTable(ResourceKey<LootTable> lootTable, Map<EquipmentSlot, Float> slotDropChances) {
    public static final Codec<Map<EquipmentSlot, Float>> DROP_CHANCES_CODEC = Codec.either(Codec.FLOAT, Codec.unboundedMap(EquipmentSlot.CODEC, Codec.FLOAT))
        .xmap(either -> either.map(EquipmentTable::createForAllSlots, Function.identity()), map -> {
            boolean flag = map.values().stream().distinct().count() == 1L;
            boolean flag1 = map.keySet().containsAll(EquipmentSlot.VALUES);
            return flag && flag1 ? Either.left(map.values().stream().findFirst().orElse(0.0F)) : Either.right((Map<EquipmentSlot, Float>)map);
        });
    public static final Codec<EquipmentTable> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                LootTable.KEY_CODEC.fieldOf("loot_table").forGetter(EquipmentTable::lootTable),
                DROP_CHANCES_CODEC.optionalFieldOf("slot_drop_chances", Map.of()).forGetter(EquipmentTable::slotDropChances)
            )
            .apply(instance, EquipmentTable::new)
    );

    public EquipmentTable(ResourceKey<LootTable> lootTable, float dropChance) {
        this(lootTable, createForAllSlots(dropChance));
    }

    private static Map<EquipmentSlot, Float> createForAllSlots(float dropChance) {
        return createForAllSlots(List.of(EquipmentSlot.values()), dropChance);
    }

    private static Map<EquipmentSlot, Float> createForAllSlots(List<EquipmentSlot> equipmentSlots, float dropChance) {
        Map<EquipmentSlot, Float> map = Maps.newHashMap();

        for (EquipmentSlot equipmentSlot : equipmentSlots) {
            map.put(equipmentSlot, dropChance);
        }

        return map;
    }
}
