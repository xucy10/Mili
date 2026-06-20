package net.minecraft.resources;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;

public class HolderSetCodec<E> implements Codec<HolderSet<E>> {
    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<Holder<E>> elementCodec;
    private final Codec<List<Holder<E>>> homogenousListCodec;
    private final Codec<Either<TagKey<E>, List<Holder<E>>>> registryAwareCodec;

    private static <E> Codec<List<Holder<E>>> homogenousList(Codec<Holder<E>> holderCodec, boolean disallowInline) {
        Codec<List<Holder<E>>> codec = holderCodec.listOf().validate(ExtraCodecs.ensureHomogenous(Holder::kind));
        return disallowInline ? codec : ExtraCodecs.compactListCodec(holderCodec, codec);
    }

    public static <E> Codec<HolderSet<E>> create(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> holderCodec, boolean disallowInline) {
        return new HolderSetCodec<>(registryKey, holderCodec, disallowInline);
    }

    private HolderSetCodec(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec, boolean disallowInline) {
        this.registryKey = registryKey;
        this.elementCodec = elementCodec;
        this.homogenousListCodec = homogenousList(elementCodec, disallowInline);
        this.registryAwareCodec = Codec.either(TagKey.hashedCodec(registryKey), this.homogenousListCodec);
    }

    @Override
    public <T> DataResult<Pair<HolderSet<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<T> registryOps) {
            Optional<HolderGetter<E>> optional = registryOps.getter(this.registryKey);
            if (optional.isPresent()) {
                HolderGetter<E> holderGetter = optional.get();
                return this.registryAwareCodec
                    .decode(ops, input)
                    .flatMap(
                        pair -> {
                            DataResult<HolderSet<E>> dataResult = pair.getFirst()
                                .map(
                                    tagKey -> lookupTag(holderGetter, (TagKey<E>)tagKey),
                                    list -> DataResult.success(HolderSet.direct((List<? extends Holder<E>>)list))
                                );
                            return dataResult.map(holderSet -> Pair.of((HolderSet<E>)holderSet, (T)pair.getSecond()));
                        }
                    );
            }
        }

        return this.decodeWithoutRegistry(ops, input);
    }

    private static <E> DataResult<HolderSet<E>> lookupTag(HolderGetter<E> input, TagKey<E> tagKey) {
        return input.get(tagKey)
            .map(DataResult::<HolderSet<E>>success)
            .orElseGet(() -> DataResult.error(() -> "Missing tag: '" + tagKey.location() + "' in '" + tagKey.registry().identifier() + "'"));
    }

    @Override
    public <T> DataResult<T> encode(HolderSet<E> input, DynamicOps<T> ops, T prefix) {
        if (ops instanceof RegistryOps<T> registryOps) {
            Optional<HolderOwner<E>> optional = registryOps.owner(this.registryKey);
            if (optional.isPresent()) {
                if (!input.canSerializeIn(optional.get())) {
                    return DataResult.error(() -> "HolderSet " + input + " is not valid in current registry set");
                }

                return this.registryAwareCodec.encode(input.unwrap().mapRight(List::copyOf), ops, prefix);
            }
        }

        return this.encodeWithoutRegistry(input, ops, prefix);
    }

    private <T> DataResult<Pair<HolderSet<E>, T>> decodeWithoutRegistry(DynamicOps<T> ops, T input) {
        return this.elementCodec.listOf().decode(ops, input).flatMap(pair -> {
            List<Holder.Direct<E>> list = new ArrayList<>();

            for (Holder<E> holder : pair.getFirst()) {
                if (!(holder instanceof Holder.Direct<E> direct)) {
                    return DataResult.error(() -> "Can't decode element " + holder + " without registry");
                }

                list.add(direct);
            }

            return DataResult.success(new Pair<>(HolderSet.direct(list), pair.getSecond()));
        });
    }

    private <T> DataResult<T> encodeWithoutRegistry(HolderSet<E> input, DynamicOps<T> ops, T prefix) {
        return this.homogenousListCodec.encode(input.stream().toList(), ops, prefix);
    }
}
