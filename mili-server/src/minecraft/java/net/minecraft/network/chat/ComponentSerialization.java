package net.minecraft.network.chat;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;

public class ComponentSerialization {
    public static final Codec<Component> CODEC = Codec.recursive("Component", ComponentSerialization::createCodec);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC = createTranslationAware(net.minecraft.nbt.NbtAccounter::defaultQuota); // Paper - adventure
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs::optional);
    // Paper start - adventure; use locale from bytebuf for translation
    public static final ThreadLocal<Boolean> DONT_RENDER_TRANSLATABLES = ThreadLocal.withInitial(() -> false);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> TRUSTED_STREAM_CODEC = createTranslationAware(net.minecraft.nbt.NbtAccounter::unlimitedHeap);
    private static StreamCodec<RegistryFriendlyByteBuf, Component> createTranslationAware(final java.util.function.Supplier<net.minecraft.nbt.NbtAccounter> sizeTracker) {
        return new StreamCodec<>() {
            final StreamCodec<ByteBuf, net.minecraft.nbt.Tag> streamCodec = ByteBufCodecs.tagCodec(sizeTracker);
            @Override
            public Component decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
                net.minecraft.nbt.Tag tag = this.streamCodec.decode(registryFriendlyByteBuf);
                RegistryOps<net.minecraft.nbt.Tag> registryOps = registryFriendlyByteBuf.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                return CODEC.parse(registryOps, tag).getOrThrow(error -> new io.netty.handler.codec.DecoderException("Failed to decode: " + error + " " + tag));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, Component object) {
                RegistryOps<net.minecraft.nbt.Tag> registryOps = registryFriendlyByteBuf.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                net.minecraft.nbt.Tag tag = (DONT_RENDER_TRANSLATABLES.get() ? CODEC : ComponentSerialization.localizedCodec(registryFriendlyByteBuf.adventure$locale))
                    .encodeStart(registryOps, object).getOrThrow(error -> new io.netty.handler.codec.EncoderException("Failed to encode: " + error + " " + object));
                this.streamCodec.encode(registryFriendlyByteBuf, tag);
            }
        };
    }
    // Paper end - adventure; use locale from bytebuf for translation
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> TRUSTED_OPTIONAL_STREAM_CODEC = TRUSTED_STREAM_CODEC.apply(
        ByteBufCodecs::optional
    );
    public static final StreamCodec<ByteBuf, Component> TRUSTED_CONTEXT_FREE_STREAM_CODEC = ByteBufCodecs.fromCodecTrusted(CODEC);

    public static Codec<Component> flatRestrictedCodec(final int maxSize) {
        return new Codec<Component>() {
            @Override
            public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
                return ComponentSerialization.CODEC
                    .decode(ops, input)
                    .flatMap(
                        pair -> this.isTooLarge(ops, pair.getFirst())
                            ? DataResult.error(() -> "Component was too large: greater than max size " + maxSize)
                            : DataResult.success((Pair<Component, T>)pair)
                    );
            }

            @Override
            public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T value) {
                return ComponentSerialization.CODEC.encodeStart(ops, input);
            }

            private <T> boolean isTooLarge(DynamicOps<T> ops, Component component) {
                DataResult<JsonElement> dataResult = ComponentSerialization.CODEC.encodeStart(asJsonOps(ops), component);
                return dataResult.isSuccess() && GsonHelper.encodesLongerThan(dataResult.getOrThrow(), maxSize);
            }

            private static <T> DynamicOps<JsonElement> asJsonOps(DynamicOps<T> ops) {
                return (DynamicOps<JsonElement>)(ops instanceof RegistryOps<T> registryOps ? registryOps.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE);
            }
        };
    }

    private static MutableComponent createFromList(List<Component> components) {
        MutableComponent mutableComponent = components.get(0).copy();

        for (int i = 1; i < components.size(); i++) {
            mutableComponent.append(components.get(i));
        }

        return mutableComponent;
    }

    public static <T> MapCodec<T> createLegacyComponentMatcher(
        ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends T>> idMapper, Function<T, MapCodec<? extends T>> codecGetter, String typeFieldName
    ) {
        MapCodec<T> mapCodec = new ComponentSerialization.FuzzyCodec<>(idMapper.values(), codecGetter);
        MapCodec<T> mapCodec1 = idMapper.codec(Codec.STRING).dispatchMap(typeFieldName, codecGetter, mapCodec3 -> mapCodec3);
        MapCodec<T> mapCodec2 = new ComponentSerialization.StrictEither<>(typeFieldName, mapCodec1, mapCodec);
        return ExtraCodecs.orCompressed(mapCodec2, mapCodec1);
    }

    // Paper start - adventure; create separate codec for each locale
    private static final java.util.Map<java.util.Locale, Codec<Component>> LOCALIZED_CODECS = new java.util.concurrent.ConcurrentHashMap<>();

    public static Codec<Component> localizedCodec(final java.util.@org.checkerframework.checker.nullness.qual.Nullable Locale locale) {
        if (locale == null) {
            return CODEC;
        }
        return LOCALIZED_CODECS.computeIfAbsent(locale,
            loc -> Codec.recursive("Component", selfCodec -> createCodec(selfCodec, loc)));
    }
    // Paper end - adventure; create separate codec for each locale

    private static Codec<Component> createCodec(Codec<Component> codec) {
        // Paper start - adventure; create separate codec for each locale
        return createCodec(codec, null);
    }

    private static Codec<Component> createCodec(Codec<Component> codec, @javax.annotation.Nullable java.util.Locale locale) {
        // Paper end - adventure; create separate codec for each locale
        ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends ComponentContents>> lateBoundIdMapper = new ExtraCodecs.LateBoundIdMapper<>();
        bootstrap(lateBoundIdMapper);
        MapCodec<ComponentContents> mapCodec = createLegacyComponentMatcher(lateBoundIdMapper, ComponentContents::codec, "type");
        Codec<Component> codec1 = RecordCodecBuilder.create(
            instance -> instance.group(
                    mapCodec.forGetter(Component::getContents),
                    ExtraCodecs.nonEmptyList(codec.listOf()).optionalFieldOf("extra", List.of()).forGetter(Component::getSiblings),
                    Style.Serializer.MAP_CODEC.forGetter(Component::getStyle)
                )
                .apply(instance, MutableComponent::new)
        );
        // Paper start - adventure; create separate codec for each locale
        final Codec<Component> origCodec = codec1;
        codec1 = new Codec<>() {
            @Override
            public <T> DataResult<com.mojang.datafixers.util.Pair<Component, T>> decode(final DynamicOps<T> ops, final T input) {
                return origCodec.decode(ops, input);
            }

            @Override
            public <T> DataResult<T> encode(final Component input, final DynamicOps<T> ops, final T prefix) {
                final net.kyori.adventure.text.Component adventureComponent;
                if (input instanceof io.papermc.paper.adventure.AdventureComponent adv) {
                    adventureComponent = adv.adventure$component();
                } else if (locale != null && input.getContents() instanceof TranslatableContents && io.papermc.paper.adventure.PaperAdventure.hasAnyTranslations()) {
                    adventureComponent = io.papermc.paper.adventure.PaperAdventure.asAdventure(input);
                } else {
                    return origCodec.encode(input, ops, prefix);
                }
                return io.papermc.paper.adventure.PaperAdventure.localizedCodec(locale)
                    .encode(adventureComponent, ops, prefix);
            }

            @Override
            public String toString() {
                return origCodec.toString() + "[AdventureComponentAware]";
            }
        };
        // Paper end - adventure; create separate codec for each locale
        return Codec.either(Codec.either(Codec.STRING, ExtraCodecs.nonEmptyList(codec.listOf())), codec1)
            .xmap(
                either -> either.map(either1 -> either1.map(Component::literal, ComponentSerialization::createFromList), component -> (Component)component),
                component -> {
                    String string = component.tryCollapseToString();
                    return string != null ? Either.left(Either.left(string)) : Either.right(component);
                }
            );
    }

    private static void bootstrap(ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends ComponentContents>> idMapper) {
        idMapper.put("text", PlainTextContents.MAP_CODEC);
        idMapper.put("translatable", TranslatableContents.MAP_CODEC);
        idMapper.put("keybind", KeybindContents.MAP_CODEC);
        idMapper.put("score", ScoreContents.MAP_CODEC);
        idMapper.put("selector", SelectorContents.MAP_CODEC);
        idMapper.put("nbt", NbtContents.MAP_CODEC);
        idMapper.put("object", ObjectContents.MAP_CODEC);
    }

    static class FuzzyCodec<T> extends MapCodec<T> {
        private final Collection<MapCodec<? extends T>> codecs;
        private final Function<T, ? extends MapEncoder<? extends T>> encoderGetter;

        public FuzzyCodec(Collection<MapCodec<? extends T>> codecs, Function<T, ? extends MapEncoder<? extends T>> encoderGetter) {
            this.codecs = codecs;
            this.encoderGetter = encoderGetter;
        }

        @Override
        public <S> DataResult<T> decode(DynamicOps<S> ops, MapLike<S> input) {
            for (MapDecoder<? extends T> mapDecoder : this.codecs) {
                DataResult<? extends T> dataResult = mapDecoder.decode(ops, input);
                if (dataResult.result().isPresent()) {
                    return (DataResult<T>)dataResult;
                }
            }

            return DataResult.error(() -> "No matching codec found");
        }

        @Override
        public <S> RecordBuilder<S> encode(T input, DynamicOps<S> ops, RecordBuilder<S> prefix) {
            MapEncoder<T> mapEncoder = (MapEncoder<T>)this.encoderGetter.apply(input);
            return mapEncoder.encode(input, ops, prefix);
        }

        @Override
        public <S> Stream<S> keys(DynamicOps<S> ops) {
            return this.codecs.stream().flatMap(mapCodec -> mapCodec.keys(ops)).distinct();
        }

        @Override
        public String toString() {
            return "FuzzyCodec[" + this.codecs + "]";
        }
    }

    static class StrictEither<T> extends MapCodec<T> {
        private final String typeFieldName;
        private final MapCodec<T> typed;
        private final MapCodec<T> fuzzy;

        public StrictEither(String typeFieldName, MapCodec<T> typed, MapCodec<T> fuzzy) {
            this.typeFieldName = typeFieldName;
            this.typed = typed;
            this.fuzzy = fuzzy;
        }

        @Override
        public <O> DataResult<T> decode(DynamicOps<O> ops, MapLike<O> input) {
            return input.get(this.typeFieldName) != null ? this.typed.decode(ops, input) : this.fuzzy.decode(ops, input);
        }

        @Override
        public <O> RecordBuilder<O> encode(T input, DynamicOps<O> ops, RecordBuilder<O> prefix) {
            return this.fuzzy.encode(input, ops, prefix);
        }

        @Override
        public <T1> Stream<T1> keys(DynamicOps<T1> ops) {
            return Stream.concat(this.typed.keys(ops), this.fuzzy.keys(ops)).distinct();
        }
    }
}
