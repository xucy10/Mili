package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.world.item.ItemStack;

public class EntityEquipment {
    public static final Codec<EntityEquipment> CODEC = Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC).xmap(map -> {
        EnumMap<EquipmentSlot, ItemStack> map1 = new EnumMap<>(EquipmentSlot.class);
        map1.putAll((Map<? extends EquipmentSlot, ? extends ItemStack>)map);
        return new EntityEquipment(map1);
    }, entityEquipment -> {
        Map<EquipmentSlot, ItemStack> map = new EnumMap<>(entityEquipment.items);
        map.values().removeIf(ItemStack::isEmpty);
        return map;
    });
    private final EnumMap<EquipmentSlot, ItemStack> items;

    private EntityEquipment(EnumMap<EquipmentSlot, ItemStack> items) {
        this.items = items;
    }

    public EntityEquipment() {
        this(new EnumMap<>(EquipmentSlot.class));
    }

    public ItemStack set(EquipmentSlot slot, ItemStack stack) {
        return Objects.requireNonNullElse(this.items.put(slot, stack), ItemStack.EMPTY);
    }

    public ItemStack get(EquipmentSlot slot) {
        return this.items.getOrDefault(slot, ItemStack.EMPTY);
    }

    public boolean isEmpty() {
        for (ItemStack itemStack : this.items.values()) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public void tick(Entity entity) {
        for (Entry<EquipmentSlot, ItemStack> entry : this.items.entrySet()) {
            ItemStack itemStack = entry.getValue();
            if (!itemStack.isEmpty()) {
                itemStack.inventoryTick(entity.level(), entity, entry.getKey());
            }
        }
    }

    public void setAll(EntityEquipment equipment) {
        this.items.clear();
        this.items.putAll(equipment.items);
    }

    public void dropAll(LivingEntity entity) {
        for (ItemStack itemStack : this.items.values()) {
            entity.drop(itemStack, true, false);
        }

        this.clear();
    }

    public void clear() {
        this.items.replaceAll((equipmentSlot, itemStack) -> ItemStack.EMPTY);
    }

    // Paper start - EntityDeathEvent
    // Needed to not set ItemStack.EMPTY to not existent slot.
    public boolean has(final EquipmentSlot slot) {
        return this.items.containsKey(slot);
    }
    // Paper end - EntityDeathEvent
}
