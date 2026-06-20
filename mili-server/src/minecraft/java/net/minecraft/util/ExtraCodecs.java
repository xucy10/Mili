package net.minecraft.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.primitives.UnsignedBytes;
import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.Codec.ResultFunction;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.codecs.BaseMapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import net.minecraft.core.HolderSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

public class ExtraCodecs {
    public static final Codec<JsonElement> JSON = converter(JsonOps.INSTANCE);
    public static final Codec<Object> JAVA = converter(JavaOps.INSTANCE);
    public static final Codec<Tag> NBT = converter(NbtOps.INSTANCE);
    public static final Codec<Vector2fc> VECTOR2F = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Float>)list, 2).map(list1 -> new Vector2f(list1.get(0), list1.get(1))),
            vector2fc -> List.of(vector2fc.x(), vector2fc.y())
        );
    public static final Codec<Vector3fc> VECTOR3F = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Float>)list, 3).map(list1 -> new Vector3f(list1.get(0), list1.get(1), list1.get(2))),
            vector3fc -> List.of(vector3fc.x(), vector3fc.y(), vector3fc.z())
        );
    public static final Codec<Vector3ic> VECTOR3I = Codec.INT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Integer>)list, 3).map(list1 -> new Vector3i(list1.get(0), list1.get(1), list1.get(2))),
            vector3ic -> List.of(vector3ic.x(), vector3ic.y(), vector3ic.z())
        );
    public static final Codec<Vector4fc> VECTOR4F = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Float>)list, 4).map(list1 -> new Vector4f(list1.get(0), list1.get(1), list1.get(2), list1.get(3))),
            vector4fc -> List.of(vector4fc.x(), vector4fc.y(), vector4fc.z(), vector4fc.w())
        );
    public static final Codec<Quaternionfc> QUATERNIONF_COMPONENTS = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Float>)list, 4).map(list1 -> new Quaternionf(list1.get(0), list1.get(1), list1.get(2), list1.get(3)).normalize()),
            quaternionfc -> List.of(quaternionfc.x(), quaternionfc.y(), quaternionfc.z(), quaternionfc.w())
        );
    public static final Codec<AxisAngle4f> AXISANGLE4F = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.FLOAT.fieldOf("angle").forGetter(axisAngle4f -> axisAngle4f.angle),
                VECTOR3F.fieldOf("axis").forGetter(axisAngle4f -> new Vector3f(axisAngle4f.x, axisAngle4f.y, axisAngle4f.z))
            )
            .apply(instance, AxisAngle4f::new)
    );
    public static final Codec<Quaternionfc> QUATERNIONF = Codec.withAlternative(QUATERNIONF_COMPONENTS, AXISANGLE4F.xmap(Quaternionf::new, AxisAngle4f::new));
    public static final Codec<Matrix4fc> MATRIX4F = Codec.FLOAT.listOf().comapFlatMap(list -> Util.fixedSize((List<Float>)list, 16).map(list1 -> {
        Matrix4f matrix4f = new Matrix4f();

        for (int i = 0; i < list1.size(); i++) {
            matrix4f.setRowColumn(i >> 2, i & 3, list1.get(i));
        }

        return matrix4f.determineProperties();
    }), matrix4fc -> {
        FloatList list = new FloatArrayList(16);

        for (int i = 0; i < 16; i++) {
            list.add(matrix4fc.getRowColumn(i >> 2, i & 3));
        }

        return list;
    });
    private static final String HEX_COLOR_PREFIX = "#";
    public static final Codec<Integer> RGB_COLOR_CODEC = Codec.withAlternative(
        Codec.INT, VECTOR3F, vector3fc -> ARGB.colorFromFloat(1.0F, vector3fc.x(), vector3fc.y(), vector3fc.z())
    );
    public static final Codec<Integer> ARGB_COLOR_CODEC = Codec.withAlternative(
        Codec.INT, VECTOR4F, vector4fc -> ARGB.colorFromFloat(vector4fc.w(), vector4fc.x(), vector4fc.y(), vector4fc.z())
    );
    public static final Codec<Integer> STRING_RGB_COLOR = Codec.withAlternative(hexColor(6).xmap(ARGB::opaque, ARGB::transparent), RGB_COLOR_CODEC);
    public static final Codec<Integer> STRING_ARGB_COLOR = Codec.withAlternative(hexColor(8), ARGB_COLOR_CODEC);
    public static final Codec<Integer> UNSIGNED_BYTE = Codec.BYTE
        .flatComapMap(
            UnsignedBytes::toInt,
            integer -> integer > 255 ? DataResult.error(() -> "Unsigned byte was too large: " + integer + " > 255") : DataResult.success(integer.byteValue())
        );
    public static final Codec<Integer> NON_NEGATIVE_INT = intRangeWithMessage(0, Integer.MAX_VALUE, integer -> "Value must be non-negative: " + integer);
    public static final Codec<Integer> POSITIVE_INT = intRangeWithMessage(1, Integer.MAX_VALUE, integer -> "Value must be positive: " + integer);
    public static final Codec<Long> NON_NEGATIVE_LONG = longRangeWithMessage(0L, Long.MAX_VALUE, _long -> "Value must be non-negative: " + _long);
    public static final Codec<Long> POSITIVE_LONG = longRangeWithMessage(1L, Long.MAX_VALUE, _long -> "Value must be positive: " + _long);
    public static final Codec<Float> NON_NEGATIVE_FLOAT = floatRangeMinInclusiveWithMessage(
        0.0F, Float.MAX_VALUE, _float -> "Value must be non-negative: " + _float
    );
    public static final Codec<Float> POSITIVE_FLOAT = floatRangeMinExclusiveWithMessage(0.0F, Float.MAX_VALUE, _float -> "Value must be positive: " + _float);
    public static final Codec<Pattern> PATTERN = Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(Pattern.compile(string));
        } catch (PatternSyntaxException var2) {
            return DataResult.error(() -> "Invalid regex pattern '" + string + "': " + var2.getMessage());
        }
    }, Pattern::pattern);
    public static final Codec<Instant> INSTANT_ISO8601 = temporalCodec(DateTimeFormatter.ISO_INSTANT).xmap(Instant::from, Function.identity());
    public static final Codec<byte[]> BASE64_STRING = Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(Base64.getDecoder().decode(string));
        } catch (IllegalArgumentException var2) {
            return DataResult.error(() -> "Malformed base64 string");
        }
    }, bytes -> Base64.getEncoder().encodeToString(bytes));
    public static final Codec<String> ESCAPED_STRING = Codec.STRING
        .comapFlatMap(string -> DataResult.success(StringEscapeUtils.unescapeJava(string)), StringEscapeUtils::escapeJava);
    public static final Codec<ExtraCodecs.TagOrElementLocation> TAG_OR_ELEMENT_ID = Codec.STRING
        .comapFlatMap(
            value -> value.startsWith("#")
                ? Identifier.read(value.substring(1)).map(identifier -> new ExtraCodecs.TagOrElementLocation(identifier, true))
                : Identifier.read(value).map(identifier -> new ExtraCodecs.TagOrElementLocation(identifier, false)),
            ExtraCodecs.TagOrElementLocation::decoratedId
        );
    public static final Function<Optional<Long>, OptionalLong> toOptionalLong = optional -> optional.map(OptionalLong::of).orElseGet(OptionalLong::empty);
    public static final Function<OptionalLong, Optional<Long>> fromOptionalLong = optionalLong -> optionalLong.isPresent()
        ? Optional.of(optionalLong.getAsLong())
        : Optional.empty();
    public static final Codec<BitSet> BIT_SET = Codec.LONG_STREAM
        .xmap(longStream -> BitSet.valueOf(longStream.toArray()), bitSet -> Arrays.stream(bitSet.toLongArray()));
    public static final int MAX_PROPERTY_NAME_LENGTH = 64;
    public static final int MAX_PROPERTY_VALUE_LENGTH = 32767;
    public static final int MAX_PROPERTY_SIGNATURE_LENGTH = 1024;
    public static final int MAX_PROPERTIES = 16;
    private static final Codec<Property> PROPERTY = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.sizeLimitedString(64).fieldOf("name").forGetter(Property::name),
                Codec.sizeLimitedString(32767).fieldOf("value").forGetter(Property::value),
                Codec.sizeLimitedString(1024).optionalFieldOf("signature").forGetter(property -> Optional.ofNullable(property.signature()))
            )
            .apply(instance, (string, string1, optional) -> new Property(string, string1, optional.orElse(null)))
    );
    public static final Codec<PropertyMap> PROPERTY_MAP = Codec.either(
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
                .validate(
                    map -> map.size() > 16 ? DataResult.error(() -> "Cannot have more than 16 properties, but was " + map.size()) : DataResult.success(map)
                ),
            PROPERTY.sizeLimitedListOf(16)
        )
        .xmap(either -> {
            Builder<String, Property> builder = ImmutableMultimap.builder();
            either.ifLeft(map -> map.forEach((string, list) -> {
                for (String string1 : list) {
                    builder.put(string, new Property(string, string1));
                }
            })).ifRight(list -> {
                for (Property property : list) {
                    builder.put(property.name(), property);
                }
            });
            return new PropertyMap(builder.build());
        }, propertyMap -> Either.right(propertyMap.values().stream().toList()));
    public static final Codec<String> PLAYER_NAME = Codec.string(0, 16)
        .validate(
            value -> StringUtil.isValidPlayerName(value)
                ? DataResult.success(value)
                : DataResult.error(() -> "Player name contained disallowed characters: '" + value + "'")
        );
    public static final Codec<GameProfile> AUTHLIB_GAME_PROFILE = gameProfileCodec(UUIDUtil.AUTHLIB_CODEC).codec();
    public static final MapCodec<GameProfile> STORED_GAME_PROFILE = gameProfileCodec(UUIDUtil.CODEC);
    public static final Codec<String> NON_EMPTY_STRING = Codec.STRING
        .validate(value -> value.isEmpty() ? DataResult.error(() -> "Expected non-empty string") : DataResult.success(value));
    public static final Codec<Integer> CODEPOINT = Codec.STRING.comapFlatMap(string -> {
        int[] ints = string.codePoints().toArray();
        return ints.length != 1 ? DataResult.error(() -> "Expected one codepoint, got: " + string) : DataResult.success(ints[0]);
    }, Character::toString);
    public static final Codec<String> RESOURCE_PATH_CODEC = Codec.STRING
        .validate(
            string -> !Identifier.isValidPath(string)
                ? DataResult.error(() -> "Invalid string to use as a resource path element: " + string)
                : DataResult.success(string)
        );
    public static final Codec<URI> UNTRUSTED_URI = Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(Util.parseAndValidateUntrustedUri(string));
        } catch (URISyntaxException var2) {
            return DataResult.error(var2::getMessage);
        }
    }, URI::toString);
    public static final Codec<String> CHAT_STRING = Codec.STRING.validate(string -> {
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (!StringUtil.isAllowedChatCharacter(c)) {
                return DataResult.error(() -> "Disallowed chat character: '" + c + "'");
            }
        }

        return DataResult.success(string);
    });

    public static <T> Codec<T> converter(DynamicOps<T> ops) {
        return Codec.PASSTHROUGH.xmap(data -> data.convert(ops).getValue(), value -> new Dynamic<>(ops, (T)value));
    }

    private static Codec<Integer> hexColor(int digits) {
        long l = (1L << digits * 4) - 1L;
        return Codec.STRING.comapFlatMap(string -> {
            if (!string.startsWith("#")) {
                return DataResult.error(() -> "Hex color must begin with #");
            } else {
                int i = string.length() - "#".length();
                if (i != digits) {
                    return DataResult.error(() -> "Hex color is wrong size, expected " + digits + " digits but got " + i);
                } else {
                    try {
                        long l1 = HexFormat.fromHexDigitsToLong(string, "#".length(), string.length());
                        return l1 >= 0L && l1 <= l ? DataResult.success((int)l1) : DataResult.error(() -> "Color value out of range: " + string);
                    } catch (NumberFormatException var7) {
                        return DataResult.error(() -> "Invalid color value: " + string);
                    }
                }
            }
        }, integer -> "#" + HexFormat.of().toHexDigits(integer.intValue(), digits));
    }

    public static <P, I> Codec<I> intervalCodec(
        Codec<P> codec, String minFieldName, String maxFieldName, BiFunction<P, P, DataResult<I>> factory, Function<I, P> minGetter, Function<I, P> maxGetter
    ) {
        Codec<I> codec1 = Codec.list(codec).comapFlatMap(list -> Util.fixedSize((List<P>)list, 2).flatMap(list1 -> {
            P object = list1.get(0);
            P object1 = list1.get(1);
            return factory.apply(object, object1);
        }), object -> ImmutableList.of(minGetter.apply((I)object), maxGetter.apply((I)object)));
        Codec<I> codec2 = RecordCodecBuilder.<Pair<P, P>>create(
                instance -> instance.group(codec.fieldOf(minFieldName).forGetter(Pair::getFirst), codec.fieldOf(maxFieldName).forGetter(Pair::getSecond))
                    .apply(instance, Pair::of)
            )
            .comapFlatMap(
                pair -> factory.apply((P)pair.getFirst(), (P)pair.getSecond()), object -> Pair.of(minGetter.apply((I)object), maxGetter.apply((I)object))
            );
        Codec<I> codec3 = Codec.withAlternative(codec1, codec2);
        return Codec.either(codec, codec3).comapFlatMap(either -> either.map(object -> factory.apply((P)object, (P)object), DataResult::success), object -> {
            P object1 = minGetter.apply((I)object);
            P object2 = maxGetter.apply((I)object);
            return Objects.equals(object1, object2) ? Either.left(object1) : Either.right((I)object);
        });
    }

    public static <A> ResultFunction<A> orElsePartial(final A value) {
        return new ResultFunction<A>() {
            @Override
            public <T> DataResult<Pair<A, T>> apply(DynamicOps<T> ops, T input, DataResult<Pair<A, T>> a) {
                MutableObject<String> mutableObject = new MutableObject<>();
                Optional<Pair<A, T>> optional = a.resultOrPartial(mutableObject::setValue);
                return optional.isPresent() ? a : DataResult.error(() -> "(" + mutableObject.get() + " -> using default)", Pair.of(value, input));
            }

            @Override
            public <T> DataResult<T> coApply(DynamicOps<T> ops, A input, DataResult<T> t) {
                return t;
            }

            @Override
            public String toString() {
                return "OrElsePartial[" + value + "]";
            }
        };
    }

    public static <E> Codec<E> idResolverCodec(ToIntFunction<E> encoder, IntFunction<@Nullable E> decoder, int notFoundValue) {
        return Codec.INT
            .flatXmap(
                integer -> Optional.ofNullable(decoder.apply(integer))
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown element id: " + integer)),
                object -> {
                    int i = encoder.applyAsInt((E)object);
                    return i == notFoundValue ? DataResult.error(() -> "Element with unknown id: " + object) : DataResult.success(i);
                }
            );
    }

    public static <I, E> Codec<E> idResolverCodec(Codec<I> idCodec, Function<I, @Nullable E> idToValue, Function<E, @Nullable I> valueToId) {
        return idCodec.flatXmap(object -> {
            E object1 = idToValue.apply((I)object);
            return object1 == null ? DataResult.error(() -> "Unknown element id: " + object) : DataResult.success(object1);
        }, object -> {
            I object1 = valueToId.apply((E)object);
            return object1 == null ? DataResult.error(() -> "Element with unknown id: " + object) : DataResult.success(object1);
        });
    }

    public static <E> Codec<E> orCompressed(final Codec<E> first, final Codec<E> second) {
        return new Codec<E>() {
            @Override
            public <T> DataResult<T> encode(E input, DynamicOps<T> ops, T prefix) {
                return ops.compressMaps() ? second.encode(input, ops, prefix) : first.encode(input, ops, prefix);
            }

            @Override
            public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> ops, T input) {
                return ops.compressMaps() ? second.decode(ops, input) : first.decode(ops, input);
            }

            @Override
            public String toString() {
                return first + " orCompressed " + second;
            }
        };
    }

    public static <E> MapCodec<E> orCompressed(final MapCodec<E> first, final MapCodec<E> second) {
        return new MapCodec<E>() {
            @Override
            public <T> RecordBuilder<T> encode(E input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return ops.compressMaps() ? second.encode(input, ops, prefix) : first.encode(input, ops, prefix);
            }

            @Override
            public <T> DataResult<E> decode(DynamicOps<T> ops, MapLike<T> prefix) {
                return ops.compressMaps() ? second.decode(ops, prefix) : first.decode(ops, prefix);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return second.keys(ops);
            }

            @Override
            public String toString() {
                return first + " orCompressed " + second;
            }
        };
    }

    public static <E> Codec<E> overrideLifecycle(Codec<E> codec, final Function<E, Lifecycle> applyLifecycle, final Function<E, Lifecycle> coApplyLifecycle) {
        return codec.mapResult(new ResultFunction<E>() {
            @Override
            public <T> DataResult<Pair<E, T>> apply(DynamicOps<T> dynamicOps, T object, DataResult<Pair<E, T>> dataResult) {
                return dataResult.result().map(pair -> dataResult.setLifecycle(applyLifecycle.apply(pair.getFirst()))).orElse(dataResult);
            }

            @Override
            public <T> DataResult<T> coApply(DynamicOps<T> dynamicOps, E object, DataResult<T> dataResult) {
                return dataResult.setLifecycle(coApplyLifecycle.apply(object));
            }

            @Override
            public String toString() {
                return "WithLifecycle[" + applyLifecycle + " " + coApplyLifecycle + "]";
            }
        });
    }

    public static <E> Codec<E> overrideLifecycle(Codec<E> codec, Function<E, Lifecycle> lifecycleGetter) {
        return overrideLifecycle(codec, lifecycleGetter, lifecycleGetter);
    }

    public static <K, V> ExtraCodecs.StrictUnboundedMapCodec<K, V> strictUnboundedMap(Codec<K> key, Codec<V> value) {
        return new ExtraCodecs.StrictUnboundedMapCodec<>(key, value);
    }

    public static <E> Codec<List<E>> compactListCodec(Codec<E> elementCodec) {
        return compactListCodec(elementCodec, elementCodec.listOf());
    }

    public static <E> Codec<List<E>> compactListCodec(Codec<E> elementCodec, Codec<List<E>> listCodec) {
        return Codec.either(listCodec, elementCodec)
            .xmap(either -> either.map(list -> list, List::of), list -> list.size() == 1 ? Either.right(list.getFirst()) : Either.left((List<E>)list));
    }

    private static Codec<Integer> intRangeWithMessage(int min, int max, Function<Integer, String> errorMessage) {
        return Codec.INT
            .validate(
                integer -> integer.compareTo(min) >= 0 && integer.compareTo(max) <= 0
                    ? DataResult.success(integer)
                    : DataResult.error(() -> errorMessage.apply(integer))
            );
    }

    public static Codec<Integer> intRange(int min, int max) {
        return intRangeWithMessage(min, max, integer -> "Value must be within range [" + min + ";" + max + "]: " + integer);
    }

    private static Codec<Long> longRangeWithMessage(long min, long max, Function<Long, String> errorMessage) {
        return Codec.LONG
            .validate(
                _long -> _long.compareTo(min) >= 0L && _long.compareTo(max) <= 0L
                    ? DataResult.success(_long)
                    : DataResult.error(() -> errorMessage.apply(_long))
            );
    }

    public static Codec<Long> longRange(int min, int max) {
        return longRangeWithMessage(min, max, _long -> "Value must be within range [" + min + ";" + max + "]: " + _long);
    }

    private static Codec<Float> floatRangeMinInclusiveWithMessage(float min, float max, Function<Float, String> errorMessage) {
        return Codec.FLOAT
            .validate(
                _float -> _float.compareTo(min) >= 0 && _float.compareTo(max) <= 0
                    ? DataResult.success(_float)
                    : DataResult.error(() -> errorMessage.apply(_float))
            );
    }

    private static Codec<Float> floatRangeMinExclusiveWithMessage(float min, float max, Function<Float, String> errorMessage) {
        return Codec.FLOAT
            .validate(
                _float -> _float.compareTo(min) > 0 && _float.compareTo(max) <= 0
                    ? DataResult.success(_float)
                    : DataResult.error(() -> errorMessage.apply(_float))
            );
    }

    public static Codec<Float> floatRange(float min, float max) {
        return floatRangeMinInclusiveWithMessage(min, max, _float -> "Value must be within range [" + min + ";" + max + "]: " + _float);
    }

    public static <T> Codec<List<T>> nonEmptyList(Codec<List<T>> codec) {
        return codec.validate(list -> list.isEmpty() ? DataResult.error(() -> "List must have contents") : DataResult.success(list));
    }

    public static <T> Codec<HolderSet<T>> nonEmptyHolderSet(Codec<HolderSet<T>> codec) {
        return codec.validate(
            holderSet -> holderSet.unwrap().right().filter(List::isEmpty).isPresent()
                ? DataResult.error(() -> "List must have contents")
                : DataResult.success(holderSet)
        );
    }

    public static <M extends Map<?, ?>> Codec<M> nonEmptyMap(Codec<M> mapCodec) {
        return mapCodec.validate(map -> map.isEmpty() ? DataResult.error(() -> "Map must have contents") : DataResult.success(map));
    }

    public static <E> MapCodec<E> retrieveContext(final Function<DynamicOps<?>, DataResult<E>> retriever) {
        class ContextRetrievalCodec extends MapCodec<E> {
            @Override
            public <T> RecordBuilder<T> encode(E input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return prefix;
            }

            @Override
            public <T> DataResult<E> decode(DynamicOps<T> ops, MapLike<T> input) {
                return retriever.apply(ops);
            }

            @Override
            public String toString() {
                return "ContextRetrievalCodec[" + retriever + "]";
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.empty();
            }
        }

        return new ContextRetrievalCodec();
    }

    public static <E, L extends Collection<E>, T> Function<L, DataResult<L>> ensureHomogenous(Function<E, T> typeGetter) {
        return collection -> {
            Iterator<E> iterator = collection.iterator();
            if (iterator.hasNext()) {
                T object = typeGetter.apply(iterator.next());

                while (iterator.hasNext()) {
                    E object1 = iterator.next();
                    T object2 = typeGetter.apply(object1);
                    if (object2 != object) {
                        return DataResult.error(() -> "Mixed type list: element " + object1 + " had type " + object2 + ", but list is of type " + object);
                    }
                }
            }

            return DataResult.success(collection, Lifecycle.stable());
        };
    }

    public static <A> Codec<A> catchDecoderException(final Codec<A> codec) {
        return Codec.of(codec, new Decoder<A>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T value) {
                try {
                    return codec.decode(ops, value);
                } catch (Exception var4) {
                    return DataResult.error(() -> "Caught exception decoding " + value + ": " + var4.getMessage());
                }
            }
        });
    }

    public static Codec<TemporalAccessor> temporalCodec(DateTimeFormatter dateTimeFormatter) {
        return Codec.STRING.comapFlatMap(string -> {
            try {
                return DataResult.success(dateTimeFormatter.parse(string));
            } catch (Exception var3) {
                return DataResult.error(var3::getMessage);
            }
        }, dateTimeFormatter::format);
    }

    public static MapCodec<OptionalLong> asOptionalLong(MapCodec<Optional<Long>> codec) {
        return codec.xmap(toOptionalLong, fromOptionalLong);
    }

    private static MapCodec<GameProfile> gameProfileCodec(Codec<UUID> idCodec) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    idCodec.fieldOf("id").forGetter(GameProfile::id),
                    PLAYER_NAME.fieldOf("name").forGetter(GameProfile::name),
                    PROPERTY_MAP.optionalFieldOf("properties", PropertyMap.EMPTY).forGetter(GameProfile::properties)
                )
                .apply(instance, GameProfile::new)
        );
    }

    public static <K, V> Codec<Map<K, V>> sizeLimitedMap(Codec<Map<K, V>> mapCodec, int maxSize) {
        return mapCodec.validate(
            map -> map.size() > maxSize
                ? DataResult.error(() -> "Map is too long: " + map.size() + ", expected range [0-" + maxSize + "]")
                : DataResult.success(map)
        );
    }

    public static <T> Codec<Object2BooleanMap<T>> object2BooleanMap(Codec<T> codec) {
        return Codec.unboundedMap(codec, Codec.BOOL).xmap(Object2BooleanOpenHashMap::new, Object2ObjectOpenHashMap::new);
    }

    @Deprecated
    public static <K, V> MapCodec<V> dispatchOptionalValue(
        final String key1,
        final String key2,
        final Codec<K> codec,
        final Function<? super V, ? extends K> keyGetter,
        final Function<? super K, ? extends Codec<? extends V>> codecGetter
    ) {
        return new MapCodec<V>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(key1), ops.createString(key2));
            }

            @Override
            public <T> DataResult<V> decode(DynamicOps<T> ops, MapLike<T> input) {
                T object = input.get(key1);
                return object == null ? DataResult.error(() -> "Missing \"" + key1 + "\" in: " + input) : codec.decode(ops, object).flatMap(pair -> {
                    T object1 = Objects.requireNonNullElseGet(input.get(key2), ops::emptyMap);
                    return codecGetter.apply(pair.getFirst()).decode(ops, object1).map(Pair::getFirst);
                });
            }

            @Override
            public <T> RecordBuilder<T> encode(V input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                K object = (K)keyGetter.apply(input);
                prefix.add(key1, codec.encodeStart(ops, object));
                DataResult<T> dataResult = this.encode(codecGetter.apply(object), input, ops);
                if (dataResult.result().isEmpty() || !Objects.equals(dataResult.result().get(), ops.emptyMap())) {
                    prefix.add(key2, dataResult);
                }

                return prefix;
            }

            private <T, V2 extends V> DataResult<T> encode(Codec<V2> valueCodec, V value, DynamicOps<T> ops) {
                return valueCodec.encodeStart(ops, (V2)value);
            }
        };
    }

    public static <A> Codec<Optional<A>> optionalEmptyMap(final Codec<A> codec) {
        return new Codec<Optional<A>>() {
            @Override
            public <T> DataResult<Pair<Optional<A>, T>> decode(DynamicOps<T> ops, T input) {
                return isEmptyMap(ops, input)
                    ? DataResult.success(Pair.of(Optional.empty(), input))
                    : codec.decode(ops, input).map(pair -> pair.mapFirst(Optional::of));
            }

            private static <T> boolean isEmptyMap(DynamicOps<T> ops, T value) {
                Optional<MapLike<T>> optional = ops.getMap(value).result();
                return optional.isPresent() && optional.get().entries().findAny().isEmpty();
            }

            @Override
            public <T> DataResult<T> encode(Optional<A> input, DynamicOps<T> ops, T value) {
                return input.isEmpty() ? DataResult.success(ops.emptyMap()) : codec.encode(input.get(), ops, value);
            }
        };
    }

    @Deprecated
    public static <E extends Enum<E>> Codec<E> legacyEnum(Function<String, E> fromString) {
        return Codec.STRING.comapFlatMap(string -> {
            try {
                return DataResult.success(fromString.apply(string));
            } catch (IllegalArgumentException var3) {
                return DataResult.error(() -> "No value with id: " + string);
            }
        }, Enum::toString);
    }

    public static class LateBoundIdMapper<I, V> {
        private final BiMap<I, V> idToValue = HashBiMap.create();

        public Codec<V> codec(Codec<I> idCodec) {
            BiMap<V, I> map = this.idToValue.inverse();
            return ExtraCodecs.idResolverCodec(idCodec, this.idToValue::get, map::get);
        }

        public ExtraCodecs.LateBoundIdMapper<I, V> put(I id, V value) {
            Objects.requireNonNull(value, () -> "Value for " + id + " is null");
            this.idToValue.put(id, value);
            return this;
        }

        public Set<V> values() {
            return Collections.unmodifiableSet(this.idToValue.values());
        }
    }

    public record StrictUnboundedMapCodec<K, V>(@Override Codec<K> keyCodec, @Override Codec<V> elementCodec) implements Codec<Map<K, V>>, BaseMapCodec<K, V> {
        @Override
        public <T> DataResult<Map<K, V>> decode(DynamicOps<T> ops, MapLike<T> input) {
            com.google.common.collect.ImmutableMap.Builder<K, V> builder = ImmutableMap.builder();

            for (Pair<T, T> pair : input.entries().toList()) {
                DataResult<K> dataResult = this.keyCodec().parse(ops, pair.getFirst());
                DataResult<V> dataResult1 = this.elementCodec().parse(ops, pair.getSecond());
                DataResult<Pair<K, V>> dataResult2 = dataResult.apply2stable(Pair::of, dataResult1);
                Optional<Error<Pair<K, V>>> optional = dataResult2.error();
                if (optional.isPresent()) {
                    String string = optional.get().message();
                    return DataResult.error(() -> dataResult.result().isPresent() ? "Map entry '" + dataResult.result().get() + "' : " + string : string);
                }

                if (!dataResult2.result().isPresent()) {
                    return DataResult.error(() -> "Empty or invalid map contents are not allowed");
                }

                Pair<K, V> pair1 = dataResult2.result().get();
                builder.put(pair1.getFirst(), pair1.getSecond());
            }

            Map<K, V> map = builder.build();
            return DataResult.success(map);
        }

        @Override
        public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getMap(input)
                .setLifecycle(Lifecycle.stable())
                .flatMap(mapLike -> this.decode(ops, (MapLike<T>)mapLike))
                .map(map -> Pair.of((Map<K, V>)map, input));
        }

        @Override
        public <T> DataResult<T> encode(Map<K, V> input, DynamicOps<T> ops, T value) {
            return this.encode(input, ops, ops.mapBuilder()).build(value);
        }

        @Override
        public String toString() {
            return "StrictUnboundedMapCodec[" + this.keyCodec + " -> " + this.elementCodec + "]";
        }
    }

    public record TagOrElementLocation(Identifier id, boolean tag) {
        @Override
        public String toString() {
            return this.decoratedId();
        }

        private String decoratedId() {
            return this.tag ? "#" + this.id : this.id.toString();
        }
    }
}
