package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.Lifecycle;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public class PlaceholderLookupProvider implements HolderGetter.Provider {
    final HolderLookup.Provider context;
    final PlaceholderLookupProvider.UniversalLookup lookup = new PlaceholderLookupProvider.UniversalLookup();
    final Map<ResourceKey<Object>, Holder.Reference<Object>> holders = new HashMap<>();
    final Map<TagKey<Object>, HolderSet.Named<Object>> holderSets = new HashMap<>();

    public PlaceholderLookupProvider(HolderLookup.Provider context) {
        this.context = context;
    }

    @Override
    public <T> Optional<? extends HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
        return Optional.of(this.lookup.castAsLookup());
    }

    public <V> RegistryOps<V> createSerializationContext(DynamicOps<V> ops) {
        return RegistryOps.create(
            ops,
            new RegistryOps.RegistryInfoLookup() {
                @Override
                public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                    return PlaceholderLookupProvider.this.context
                        .lookup(registryKey)
                        .map(RegistryOps.RegistryInfo::fromRegistryLookup)
                        .or(
                            () -> Optional.of(
                                new RegistryOps.RegistryInfo<>(
                                    PlaceholderLookupProvider.this.lookup.castAsOwner(),
                                    PlaceholderLookupProvider.this.lookup.castAsLookup(),
                                    Lifecycle.experimental()
                                )
                            )
                        );
                }

                // Paper start add method to get the value for pre-filling builders in the reg mod API
                @Override
                public HolderLookup.Provider lookupForValueCopyViaBuilders() {
                    return PlaceholderLookupProvider.this.context;
                }
                // Paper end - add method to get the value for pre-filling builders in the reg mod API
            }
        );
    }

    public RegistryContextSwapper createSwapper() {
        return new RegistryContextSwapper() {
            @Override
            public <T> DataResult<T> swapTo(Codec<T> codec, T value, HolderLookup.Provider provider) {
                return codec.encodeStart(PlaceholderLookupProvider.this.createSerializationContext(JavaOps.INSTANCE), value)
                    .flatMap(object -> codec.parse(provider.createSerializationContext(JavaOps.INSTANCE), object));
            }
        };
    }

    public boolean hasRegisteredPlaceholders() {
        return !this.holders.isEmpty() || !this.holderSets.isEmpty();
    }

    class UniversalLookup implements HolderGetter<Object>, HolderOwner<Object> {
        @Override
        public Optional<Holder.Reference<Object>> get(ResourceKey<Object> resourceKey) {
            return Optional.of(this.getOrCreate(resourceKey));
        }

        @Override
        public Holder.Reference<Object> getOrThrow(ResourceKey<Object> resourceKey) {
            return this.getOrCreate(resourceKey);
        }

        private Holder.Reference<Object> getOrCreate(ResourceKey<Object> key) {
            return PlaceholderLookupProvider.this.holders
                .computeIfAbsent(key, resourceKey -> Holder.Reference.createStandAlone(this, (ResourceKey<Object>)resourceKey));
        }

        @Override
        public Optional<HolderSet.Named<Object>> get(TagKey<Object> tagKey) {
            return Optional.of(this.getOrCreate(tagKey));
        }

        @Override
        public HolderSet.Named<Object> getOrThrow(TagKey<Object> tagKey) {
            return this.getOrCreate(tagKey);
        }

        private HolderSet.Named<Object> getOrCreate(TagKey<Object> key) {
            return PlaceholderLookupProvider.this.holderSets.computeIfAbsent(key, tagKey -> HolderSet.emptyNamed(this, (TagKey<Object>)tagKey));
        }

        public <T> HolderGetter<T> castAsLookup() {
            return (HolderGetter<T>) this;
        }

        public <T> HolderOwner<T> castAsOwner() {
            return (HolderOwner<T>) this;
        }
    }
}
