package net.minecraft.world.item.slot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.LootContext;

public interface SlotSources {
    Codec<SlotSource> TYPED_CODEC = BuiltInRegistries.SLOT_SOURCE_TYPE.byNameCodec().dispatch(SlotSource::codec, mapCodec -> mapCodec);
    Codec<SlotSource> CODEC = Codec.lazyInitialized(() -> Codec.withAlternative(TYPED_CODEC, GroupSlotSource.INLINE_CODEC));

    static MapCodec<? extends SlotSource> bootstrap(Registry<MapCodec<? extends SlotSource>> registry) {
        Registry.register(registry, "group", GroupSlotSource.MAP_CODEC);
        Registry.register(registry, "filtered", FilteredSlotSource.MAP_CODEC);
        Registry.register(registry, "limit_slots", LimitSlotSource.MAP_CODEC);
        Registry.register(registry, "slot_range", RangeSlotSource.MAP_CODEC);
        Registry.register(registry, "contents", ContentsSlotSource.MAP_CODEC);
        return Registry.register(registry, "empty", EmptySlotSource.MAP_CODEC);
    }

    static Function<LootContext, SlotCollection> group(Collection<? extends SlotSource> terms) {
        List<SlotSource> list = List.copyOf(terms);

        return switch (list.size()) {
            case 0 -> lootContext -> SlotCollection.EMPTY;
            case 1 -> list.getFirst()::provide;
            case 2 -> {
                SlotSource slotSource = list.get(0);
                SlotSource slotSource1 = list.get(1);
                yield lootContext -> SlotCollection.concat(slotSource.provide(lootContext), slotSource1.provide(lootContext));
            }
            default -> lootContext -> {
                List<SlotCollection> list1 = new ArrayList<>();

                for (SlotSource slotSource2 : list) {
                    list1.add(slotSource2.provide(lootContext));
                }

                return SlotCollection.concat(list1);
            };
        };
    }
}
