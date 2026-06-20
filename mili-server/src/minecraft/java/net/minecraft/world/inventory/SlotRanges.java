package net.minecraft.world.inventory;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

public class SlotRanges {
    private static final List<SlotRange> SLOTS = Util.make(new ArrayList<>(), list -> {
        addSingleSlot(list, "contents", 0);
        addSlotRange(list, "container.", 0, 54);
        addSlotRange(list, "hotbar.", 0, 9);
        addSlotRange(list, "inventory.", 9, 27);
        addSlotRange(list, "enderchest.", 200, 27);
        addSlotRange(list, "villager.", 300, 8);
        addSlotRange(list, "horse.", 500, 15);
        int index = EquipmentSlot.MAINHAND.getIndex(98);
        int index1 = EquipmentSlot.OFFHAND.getIndex(98);
        addSingleSlot(list, "weapon", index);
        addSingleSlot(list, "weapon.mainhand", index);
        addSingleSlot(list, "weapon.offhand", index1);
        addSlots(list, "weapon.*", index, index1);
        index = EquipmentSlot.HEAD.getIndex(100);
        index1 = EquipmentSlot.CHEST.getIndex(100);
        int index2 = EquipmentSlot.LEGS.getIndex(100);
        int index3 = EquipmentSlot.FEET.getIndex(100);
        int index4 = EquipmentSlot.BODY.getIndex(105);
        addSingleSlot(list, "armor.head", index);
        addSingleSlot(list, "armor.chest", index1);
        addSingleSlot(list, "armor.legs", index2);
        addSingleSlot(list, "armor.feet", index3);
        addSingleSlot(list, "armor.body", index4);
        addSlots(list, "armor.*", index, index1, index2, index3, index4);
        addSingleSlot(list, "saddle", EquipmentSlot.SADDLE.getIndex(106));
        addSingleSlot(list, "horse.chest", 499);
        addSingleSlot(list, "player.cursor", 499);
        addSlotRange(list, "player.crafting.", 500, 4);
    });
    public static final Codec<SlotRange> CODEC = StringRepresentable.fromValues(() -> SLOTS.toArray(SlotRange[]::new));
    private static final Function<String, @Nullable SlotRange> NAME_LOOKUP = StringRepresentable.createNameLookup(SLOTS.toArray(SlotRange[]::new));

    private static SlotRange create(String name, int value) {
        return SlotRange.of(name, IntLists.singleton(value));
    }

    private static SlotRange create(String name, IntList values) {
        return SlotRange.of(name, IntLists.unmodifiable(values));
    }

    private static SlotRange create(String name, int... values) {
        return SlotRange.of(name, IntList.of(values));
    }

    private static void addSingleSlot(List<SlotRange> list, String name, int value) {
        list.add(create(name, value));
    }

    private static void addSlotRange(List<SlotRange> list, String prefix, int startValue, int size) {
        IntList list1 = new IntArrayList(size);

        for (int i = 0; i < size; i++) {
            int i1 = startValue + i;
            list.add(create(prefix + i, i1));
            list1.add(i1);
        }

        list.add(create(prefix + "*", list1));
    }

    private static void addSlots(List<SlotRange> list, String name, int... values) {
        list.add(create(name, values));
    }

    public static @Nullable SlotRange nameToIds(String name) {
        return NAME_LOOKUP.apply(name);
    }

    public static Stream<String> allNames() {
        return SLOTS.stream().map(StringRepresentable::getSerializedName);
    }

    public static Stream<String> singleSlotNames() {
        return SLOTS.stream().filter(slotRange -> slotRange.size() == 1).map(StringRepresentable::getSerializedName);
    }
}
