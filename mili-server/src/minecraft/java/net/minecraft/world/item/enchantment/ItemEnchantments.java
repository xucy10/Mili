package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import org.jspecify.annotations.Nullable;

public class ItemEnchantments implements TooltipProvider {
    // Paper start - sort enchantments
    private static final java.util.Comparator<Holder<Enchantment>> ENCHANTMENT_ORDER = java.util.Comparator.comparing(Holder::getRegisteredName);
    public static final ItemEnchantments EMPTY = new ItemEnchantments(new it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap<>(ENCHANTMENT_ORDER));
    // Paper end - sort enchantments
    private static final Codec<Integer> LEVEL_CODEC = Codec.intRange(1, 255);
    public static final Codec<ItemEnchantments> CODEC = Codec.unboundedMap(Enchantment.CODEC, LEVEL_CODEC)
        .xmap(
            map -> new net.minecraft.world.item.enchantment.ItemEnchantments(net.minecraft.util.Util.make(new it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap<>(ENCHANTMENT_ORDER), m -> m.putAll(map))), // Paper - sort enchantments
            itemEnchantments -> itemEnchantments.enchantments
        );
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemEnchantments> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.map((v) -> new it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap<>(ENCHANTMENT_ORDER), Enchantment.STREAM_CODEC, ByteBufCodecs.VAR_INT), // Paper
        itemEnchantments -> itemEnchantments.enchantments,
        ItemEnchantments::new
    );
    final it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap<Holder<Enchantment>> enchantments; // Paper

    ItemEnchantments(it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap<Holder<Enchantment>> enchantments) { // Paper
        this.enchantments = enchantments;

        for (Entry<Holder<Enchantment>> entry : enchantments.object2IntEntrySet()) {
            int intValue = entry.getIntValue();
            if (intValue < 0 || intValue > 255) {
                throw new IllegalArgumentException("Enchantment " + entry.getKey() + " has invalid level " + intValue);
            }
        }
    }

    public int getLevel(Holder<Enchantment> enchantment) {
        return this.enchantments.getInt(enchantment);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        HolderLookup.Provider provider = context.registries();
        HolderSet<Enchantment> tagOrEmpty = getTagOrEmpty(provider, Registries.ENCHANTMENT, EnchantmentTags.TOOLTIP_ORDER);

        for (Holder<Enchantment> holder : tagOrEmpty) {
            int _int = this.enchantments.getInt(holder);
            if (_int > 0) {
                tooltipAdder.accept(Enchantment.getFullname(holder, _int));
            }
        }

        for (Entry<Holder<Enchantment>> entry : this.enchantments.object2IntEntrySet()) {
            Holder<Enchantment> holder1 = entry.getKey();
            if (!tagOrEmpty.contains(holder1)) {
                tooltipAdder.accept(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
            }
        }
    }

    private static <T> HolderSet<T> getTagOrEmpty(HolderLookup.@Nullable Provider registries, ResourceKey<Registry<T>> registryKey, TagKey<T> key) {
        if (registries != null) {
            Optional<HolderSet.Named<T>> optional = registries.lookupOrThrow(registryKey).get(key);
            if (optional.isPresent()) {
                return optional.get();
            }
        }

        return HolderSet.direct();
    }

    public Set<Holder<Enchantment>> keySet() {
        return Collections.unmodifiableSet(this.enchantments.keySet());
    }

    public Set<Entry<Holder<Enchantment>>> entrySet() {
        return Collections.unmodifiableSet(this.enchantments.object2IntEntrySet());
    }

    public int size() {
        return this.enchantments.size();
    }

    public boolean isEmpty() {
        return this.enchantments.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ItemEnchantments itemEnchantments && this.enchantments.equals(itemEnchantments.enchantments);
    }

    @Override
    public int hashCode() {
        return this.enchantments.hashCode();
    }

    @Override
    public String toString() {
        return "ItemEnchantments{enchantments=" + this.enchantments + "}";
    }

    public static class Mutable {
        private final it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap<Holder<Enchantment>> enchantments = new it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap<>(ENCHANTMENT_ORDER); // Paper

        public Mutable(ItemEnchantments enchantments) {
            this.enchantments.putAll(enchantments.enchantments);
        }

        public void set(Holder<Enchantment> enchantment, int level) {
            if (level <= 0) {
                this.enchantments.removeInt(enchantment);
            } else {
                this.enchantments.put(enchantment, Math.min(level, 255));
            }
        }

        public void upgrade(Holder<Enchantment> enchantment, int level) {
            if (level > 0) {
                this.enchantments.merge(enchantment, Math.min(level, 255), Integer::max);
            }
        }

        public void removeIf(Predicate<Holder<Enchantment>> predicate) {
            this.enchantments.keySet().removeIf(predicate);
        }

        public int getLevel(Holder<Enchantment> enchantment) {
            return this.enchantments.getOrDefault(enchantment, 0);
        }

        public Set<Holder<Enchantment>> keySet() {
            return this.enchantments.keySet();
        }

        public ItemEnchantments toImmutable() {
            return new ItemEnchantments(this.enchantments);
        }
    }
}
