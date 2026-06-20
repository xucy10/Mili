package net.minecraft.network.codec;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.Mth;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public interface ByteBufCodecs {
    int MAX_INITIAL_COLLECTION_SIZE = 65536;
    StreamCodec<ByteBuf, Boolean> BOOL = new StreamCodec<ByteBuf, Boolean>() {
        @Override
        public Boolean decode(ByteBuf buffer) {
            return buffer.readBoolean();
        }

        @Override
        public void encode(ByteBuf buffer, Boolean value) {
            buffer.writeBoolean(value);
        }
    };
    StreamCodec<ByteBuf, Byte> BYTE = new StreamCodec<ByteBuf, Byte>() {
        @Override
        public Byte decode(ByteBuf buffer) {
            return buffer.readByte();
        }

        @Override
        public void encode(ByteBuf buffer, Byte value) {
            buffer.writeByte(value);
        }
    };
    StreamCodec<ByteBuf, Float> ROTATION_BYTE = BYTE.map(Mth::unpackDegrees, Mth::packDegrees);
    StreamCodec<ByteBuf, Short> SHORT = new StreamCodec<ByteBuf, Short>() {
        @Override
        public Short decode(ByteBuf buffer) {
            return buffer.readShort();
        }

        @Override
        public void encode(ByteBuf buffer, Short value) {
            buffer.writeShort(value);
        }
    };
    StreamCodec<ByteBuf, Integer> UNSIGNED_SHORT = new StreamCodec<ByteBuf, Integer>() {
        @Override
        public Integer decode(ByteBuf buffer) {
            return buffer.readUnsignedShort();
        }

        @Override
        public void encode(ByteBuf buffer, Integer value) {
            buffer.writeShort(value);
        }
    };
    StreamCodec<ByteBuf, Integer> INT = new StreamCodec<ByteBuf, Integer>() {
        @Override
        public Integer decode(ByteBuf buffer) {
            return buffer.readInt();
        }

        @Override
        public void encode(ByteBuf buffer, Integer value) {
            buffer.writeInt(value);
        }
    };
    StreamCodec<ByteBuf, Integer> VAR_INT = new StreamCodec<ByteBuf, Integer>() {
        @Override
        public Integer decode(ByteBuf buffer) {
            return VarInt.read(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, Integer value) {
            VarInt.write(buffer, value);
        }
    };
    StreamCodec<ByteBuf, OptionalInt> OPTIONAL_VAR_INT = VAR_INT.map(
        value -> value == 0 ? OptionalInt.empty() : OptionalInt.of(value - 1), optionalInt -> optionalInt.isPresent() ? optionalInt.getAsInt() + 1 : 0
    );
    StreamCodec<ByteBuf, Long> LONG = new StreamCodec<ByteBuf, Long>() {
        @Override
        public Long decode(ByteBuf buffer) {
            return buffer.readLong();
        }

        @Override
        public void encode(ByteBuf buffer, Long value) {
            buffer.writeLong(value);
        }
    };
    StreamCodec<ByteBuf, Long> VAR_LONG = new StreamCodec<ByteBuf, Long>() {
        @Override
        public Long decode(ByteBuf buffer) {
            return VarLong.read(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, Long value) {
            VarLong.write(buffer, value);
        }
    };
    StreamCodec<ByteBuf, Float> FLOAT = new StreamCodec<ByteBuf, Float>() {
        @Override
        public Float decode(ByteBuf buffer) {
            return buffer.readFloat();
        }

        @Override
        public void encode(ByteBuf buffer, Float value) {
            buffer.writeFloat(value);
        }
    };
    StreamCodec<ByteBuf, Double> DOUBLE = new StreamCodec<ByteBuf, Double>() {
        @Override
        public Double decode(ByteBuf buffer) {
            return buffer.readDouble();
        }

        @Override
        public void encode(ByteBuf buffer, Double value) {
            buffer.writeDouble(value);
        }
    };
    StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = new StreamCodec<ByteBuf, byte[]>() {
        @Override
        public byte[] decode(ByteBuf buffer) {
            return FriendlyByteBuf.readByteArray(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, byte[] data) {
            FriendlyByteBuf.writeByteArray(buffer, data);
        }
    };
    StreamCodec<ByteBuf, long[]> LONG_ARRAY = new StreamCodec<ByteBuf, long[]>() {
        @Override
        public long[] decode(ByteBuf buffer) {
            return FriendlyByteBuf.readLongArray(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, long[] data) {
            FriendlyByteBuf.writeLongArray(buffer, data);
        }
    };
    StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    StreamCodec<ByteBuf, Tag> TAG = tagCodec(NbtAccounter::defaultQuota);
    StreamCodec<ByteBuf, Tag> TRUSTED_TAG = tagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = compoundTagCodec(NbtAccounter::defaultQuota);
    StreamCodec<ByteBuf, CompoundTag> TRUSTED_COMPOUND_TAG = compoundTagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, Optional<CompoundTag>> OPTIONAL_COMPOUND_TAG = new StreamCodec<ByteBuf, Optional<CompoundTag>>() {
        @Override
        public Optional<CompoundTag> decode(ByteBuf buffer) {
            return Optional.ofNullable(FriendlyByteBuf.readNbt(buffer));
        }

        @Override
        public void encode(ByteBuf buffer, Optional<CompoundTag> value) {
            FriendlyByteBuf.writeNbt(buffer, value.orElse(null));
        }
    };
    StreamCodec<ByteBuf, Vector3fc> VECTOR3F = new StreamCodec<ByteBuf, Vector3fc>() {
        @Override
        public Vector3fc decode(ByteBuf buffer) {
            return FriendlyByteBuf.readVector3f(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, Vector3fc value) {
            FriendlyByteBuf.writeVector3f(buffer, value);
        }
    };
    StreamCodec<ByteBuf, Quaternionfc> QUATERNIONF = new StreamCodec<ByteBuf, Quaternionfc>() {
        @Override
        public Quaternionfc decode(ByteBuf buffer) {
            return FriendlyByteBuf.readQuaternion(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, Quaternionfc value) {
            FriendlyByteBuf.writeQuaternion(buffer, value);
        }
    };
    StreamCodec<ByteBuf, Integer> CONTAINER_ID = new StreamCodec<ByteBuf, Integer>() {
        @Override
        public Integer decode(ByteBuf buffer) {
            return FriendlyByteBuf.readContainerId(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, Integer value) {
            FriendlyByteBuf.writeContainerId(buffer, value);
        }
    };
    StreamCodec<ByteBuf, PropertyMap> GAME_PROFILE_PROPERTIES = new StreamCodec<ByteBuf, PropertyMap>() {
        @Override
        public PropertyMap decode(ByteBuf buffer) {
            int count = ByteBufCodecs.readCount(buffer, 16);
            Builder<String, Property> builder = ImmutableMultimap.builder();

            for (int i = 0; i < count; i++) {
                String string = Utf8String.read(buffer, 64);
                String string1 = Utf8String.read(buffer, 32767);
                String string2 = FriendlyByteBuf.readNullable(buffer, buffer1 -> Utf8String.read(buffer1, 1024));
                Property property = new Property(string, string1, string2);
                builder.put(property.name(), property);
            }

            return new PropertyMap(builder.build());
        }

        @Override
        public void encode(ByteBuf buffer, PropertyMap value) {
            ByteBufCodecs.writeCount(buffer, value.size(), 16);

            for (Property property : value.values()) {
                Utf8String.write(buffer, property.name(), 64);
                Utf8String.write(buffer, property.value(), 32767);
                FriendlyByteBuf.writeNullable(buffer, property.signature(), (buffer1, value1) -> Utf8String.write(buffer1, value1, 1024));
            }
        }
    };
    StreamCodec<ByteBuf, String> PLAYER_NAME = stringUtf8(16);
    StreamCodec<ByteBuf, GameProfile> GAME_PROFILE = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, GameProfile::id, PLAYER_NAME, GameProfile::name, GAME_PROFILE_PROPERTIES, GameProfile::properties, GameProfile::new
    );
    StreamCodec<ByteBuf, Integer> RGB_COLOR = new StreamCodec<ByteBuf, Integer>() {
        @Override
        public Integer decode(ByteBuf buffer) {
            return ARGB.color(buffer.readByte() & 0xFF, buffer.readByte() & 0xFF, buffer.readByte() & 0xFF);
        }

        @Override
        public void encode(ByteBuf buffer, Integer value) {
            buffer.writeByte(ARGB.red(value));
            buffer.writeByte(ARGB.green(value));
            buffer.writeByte(ARGB.blue(value));
        }
    };

    static StreamCodec<ByteBuf, byte[]> byteArray(final int maxSize) {
        return new StreamCodec<ByteBuf, byte[]>() {
            @Override
            public byte[] decode(ByteBuf buffer) {
                return FriendlyByteBuf.readByteArray(buffer, maxSize);
            }

            @Override
            public void encode(ByteBuf buffer, byte[] value) {
                if (value.length > maxSize) {
                    throw new EncoderException("ByteArray with size " + value.length + " is bigger than allowed " + maxSize);
                } else {
                    FriendlyByteBuf.writeByteArray(buffer, value);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, String> stringUtf8(final int maxLength) {
        return new StreamCodec<ByteBuf, String>() {
            @Override
            public String decode(ByteBuf buffer) {
                return Utf8String.read(buffer, maxLength);
            }

            @Override
            public void encode(ByteBuf buffer, String value) {
                Utf8String.write(buffer, value, maxLength);
            }
        };
    }

    static StreamCodec<ByteBuf, Optional<Tag>> optionalTagCodec(final Supplier<NbtAccounter> accounter) {
        return new StreamCodec<ByteBuf, Optional<Tag>>() {
            @Override
            public Optional<Tag> decode(ByteBuf buffer) {
                return Optional.ofNullable(FriendlyByteBuf.readNbt(buffer, accounter.get()));
            }

            @Override
            public void encode(ByteBuf buffer, Optional<Tag> value) {
                FriendlyByteBuf.writeNbt(buffer, value.orElse(null));
            }
        };
    }

    static StreamCodec<ByteBuf, Tag> tagCodec(final Supplier<NbtAccounter> accounter) {
        return new StreamCodec<ByteBuf, Tag>() {
            @Override
            public Tag decode(ByteBuf buffer) {
                Tag nbt = FriendlyByteBuf.readNbt(buffer, accounter.get());
                if (nbt == null) {
                    throw new DecoderException("Expected non-null compound tag");
                } else {
                    return nbt;
                }
            }

            @Override
            public void encode(ByteBuf buffer, Tag value) {
                if (value == EndTag.INSTANCE) {
                    throw new EncoderException("Expected non-null compound tag");
                } else {
                    FriendlyByteBuf.writeNbt(buffer, value);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, CompoundTag> compoundTagCodec(Supplier<NbtAccounter> accounterSupplier) {
        return tagCodec(accounterSupplier).map(tag -> {
            if (tag instanceof CompoundTag compoundTag) {
                return compoundTag;
            } else {
                throw new DecoderException("Not a compound tag: " + tag);
            }
        }, tag -> (Tag)tag);
    }

    static <T> StreamCodec<ByteBuf, T> fromCodecTrusted(Codec<T> codec) {
        return fromCodec(codec, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec) {
        return fromCodec(codec, NbtAccounter::defaultQuota);
    }

    static <T, B extends ByteBuf, V> StreamCodec.CodecOperation<B, T, V> fromCodec(DynamicOps<T> ops, Codec<V> codec) {
        return codec1 -> new StreamCodec<B, V>() {
            @Override
            public V decode(B buffer) {
                T object = (T)codec1.decode(buffer);
                return (V)codec.parse(ops, object).getOrThrow(string -> new DecoderException("Failed to decode: " + string + " " + object));
            }

            @Override
            public void encode(B buffer, V value) {
                T orThrow = (T)codec.encodeStart(ops, value).getOrThrow(string -> new EncoderException("Failed to encode: " + string + " " + value));
                codec1.encode(buffer, orThrow);
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec, Supplier<NbtAccounter> accounterSupplier) {
        return tagCodec(accounterSupplier).apply(fromCodec(NbtOps.INSTANCE, codec));
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistriesTrusted(Codec<T> codec) {
        return fromCodecWithRegistries(codec, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(Codec<T> codec) {
        return fromCodecWithRegistries(codec, NbtAccounter::defaultQuota);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(final Codec<T> codec, Supplier<NbtAccounter> accounterSupplier) {
        final StreamCodec<ByteBuf, Tag> streamCodec = tagCodec(accounterSupplier);
        return new StreamCodec<RegistryFriendlyByteBuf, T>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buffer) {
                Tag tag = streamCodec.decode(buffer);
                RegistryOps<Tag> registryOps = buffer.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                return codec.parse(registryOps, tag).getOrThrow(string -> new DecoderException("Failed to decode: " + string + " " + tag));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, T value) {
                RegistryOps<Tag> registryOps = buffer.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                Tag tag = codec.encodeStart(registryOps, value).getOrThrow(string -> new EncoderException("Failed to encode: " + string + " " + value));
                streamCodec.encode(buffer, tag);
            }
        };
    }

    // Paper start - Track codec depth
    static <B extends FriendlyByteBuf, V> StreamCodec<B, V> trackDepth(final StreamCodec<B, V> codec) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                buffer.trackCodecDepth = true;
                try {
                    return codec.decode(buffer);
                } finally {
                    buffer.trackCodecDepth = false;
                    buffer.codecDepth = 0;
                }
            }

            @Override
            public void encode(B buffer, V value) {
                codec.encode(buffer, value);
            }
        };
    }

    static <B extends FriendlyByteBuf, V> StreamCodec<B, V> increaseDepth(final StreamCodec<B, V> codec) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                if (!buffer.trackCodecDepth) {
                    return codec.decode(buffer);
                }
                if (++buffer.codecDepth > 64) {
                    throw new DecoderException("Too deep");
                }
                return codec.decode(buffer);
            }

            @Override
            public void encode(B buffer, V value) {
                codec.encode(buffer, value);
            }
        };
    }
    // Paper end - Track codec depth

    static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(final StreamCodec<? super B, V> codec) {
        return new StreamCodec<B, Optional<V>>() {
            @Override
            public Optional<V> decode(B buffer) {
                return buffer.readBoolean() ? Optional.of(codec.decode(buffer)) : Optional.empty();
            }

            @Override
            public void encode(B buffer, Optional<V> value) {
                if (value.isPresent()) {
                    buffer.writeBoolean(true);
                    codec.encode(buffer, value.get());
                } else {
                    buffer.writeBoolean(false);
                }
            }
        };
    }

    static int readCount(ByteBuf buffer, int maxSize) {
        int i = VarInt.read(buffer);
        if (i > maxSize) {
            throw new DecoderException(i + " elements exceeded max size of: " + maxSize);
        } else {
            return i;
        }
    }

    static void writeCount(ByteBuf buffer, int count, int maxSize) {
        if (count > maxSize) {
            throw new EncoderException(count + " elements exceeded max size of: " + maxSize);
        } else {
            VarInt.write(buffer, count);
        }
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> factory, StreamCodec<? super B, V> codec) {
        return collection(factory, codec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(
        final IntFunction<C> factory, final StreamCodec<? super B, V> codec, final int maxSize
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                int count = ByteBufCodecs.readCount(buffer, maxSize);
                C collection = factory.apply(Math.min(count, 65536));

                for (int i = 0; i < count; i++) {
                    collection.add(codec.decode(buffer));
                }

                return collection;
            }

            @Override
            public void encode(B buffer, C value) {
                ByteBufCodecs.writeCount(buffer, value.size(), maxSize);

                for (V object : value) {
                    codec.encode(buffer, object);
                }
            }
        };
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec.CodecOperation<B, V, C> collection(IntFunction<C> factory) {
        return codec -> collection(factory, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return codec -> collection(ArrayList::new, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int maxSize) {
        return codec -> collection(ArrayList::new, codec, maxSize);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
        IntFunction<? extends M> factory, StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec
    ) {
        return map(factory, keyCodec, valueCodec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
        final IntFunction<? extends M> factory, final StreamCodec<? super B, K> keyCodec, final StreamCodec<? super B, V> valueCodec, final int maxSize
    ) {
        return new StreamCodec<B, M>() {
            @Override
            public void encode(B buffer, M value) {
                ByteBufCodecs.writeCount(buffer, value.size(), maxSize);
                value.forEach((object, object1) -> {
                    keyCodec.encode(buffer, (K)object);
                    valueCodec.encode(buffer, (V)object1);
                });
            }

            @Override
            public M decode(B buffer) {
                int count = ByteBufCodecs.readCount(buffer, maxSize);
                M map = (M)factory.apply(Math.min(count, 65536));

                for (int i = 0; i < count; i++) {
                    K object = keyCodec.decode(buffer);
                    V object1 = valueCodec.decode(buffer);
                    map.put(object, object1);
                }

                return map;
            }
        };
    }

    static <B extends ByteBuf, L, R> StreamCodec<B, Either<L, R>> either(final StreamCodec<? super B, L> leftCodec, final StreamCodec<? super B, R> rightCodec) {
        return new StreamCodec<B, Either<L, R>>() {
            @Override
            public Either<L, R> decode(B buffer) {
                return buffer.readBoolean() ? Either.left(leftCodec.decode(buffer)) : Either.right(rightCodec.decode(buffer));
            }

            @Override
            public void encode(B buffer, Either<L, R> value) {
                value.ifLeft(object -> {
                    buffer.writeBoolean(true);
                    leftCodec.encode(buffer, (L)object);
                }).ifRight(object -> {
                    buffer.writeBoolean(false);
                    rightCodec.encode(buffer, (R)object);
                });
            }
        };
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, V> lengthPrefixed(int maxLength, BiFunction<B, ByteBuf, B> function) {
        return codec -> new StreamCodec<B, V>() {
            @Override
            public V decode(B buffer) {
                int i = VarInt.read(buffer);
                if (i > maxLength) {
                    throw new DecoderException("Buffer size " + i + " is larger than allowed limit of " + maxLength);
                } else {
                    int i1 = buffer.readerIndex();
                    B byteBuf = (B)((ByteBuf)function.apply(buffer, buffer.slice(i1, i)));
                    buffer.readerIndex(i1 + i);
                    return (V)codec.decode(byteBuf);
                }
            }

            @Override
            public void encode(B buffer, V value) {
                B byteBuf = (B)((ByteBuf)function.apply(buffer, buffer.alloc().buffer()));

                try {
                    codec.encode(byteBuf, value);
                    int i = byteBuf.readableBytes();
                    if (i > maxLength) {
                        throw new EncoderException("Buffer size " + i + " is  larger than allowed limit of " + maxLength);
                    }

                    VarInt.write(buffer, i);
                    buffer.writeBytes(byteBuf);
                } finally {
                    byteBuf.release();
                }
            }
        };
    }

    static <V> StreamCodec.CodecOperation<ByteBuf, V, V> lengthPrefixed(int length) {
        return lengthPrefixed(length, (byteBuf, byteBuf1) -> byteBuf1);
    }

    static <V> StreamCodec.CodecOperation<RegistryFriendlyByteBuf, V, V> registryFriendlyLengthPrefixed(int length) {
        return lengthPrefixed(length, (registryFriendlyByteBuf, byteBuf) -> new RegistryFriendlyByteBuf(byteBuf, registryFriendlyByteBuf.registryAccess()));
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(final IntFunction<T> idLookup, final ToIntFunction<T> idGetter) {
        return new StreamCodec<ByteBuf, T>() {
            @Override
            public T decode(ByteBuf buffer) {
                int i = VarInt.read(buffer);
                return idLookup.apply(i);
            }

            @Override
            public void encode(ByteBuf buffer, T value) {
                int i = idGetter.applyAsInt(value);
                VarInt.write(buffer, i);
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(IdMap<T> idMap) {
        return idMapper(idMap::byIdOrThrow, idMap::getIdOrThrow);
    }

    private static <T, R> StreamCodec<RegistryFriendlyByteBuf, R> registry(
        final ResourceKey<? extends Registry<T>> registryKey, final Function<Registry<T>, IdMap<R>> idGetter
    ) {
        return new StreamCodec<RegistryFriendlyByteBuf, R>() {
            private IdMap<R> getRegistryOrThrow(RegistryFriendlyByteBuf buffer) {
                return idGetter.apply(buffer.registryAccess().lookupOrThrow(registryKey));
            }

            @Override
            public R decode(RegistryFriendlyByteBuf buffer) {
                int i = VarInt.read(buffer);
                return (R)this.getRegistryOrThrow(buffer).byIdOrThrow(i);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, R value) {
                int idOrThrow = this.getRegistryOrThrow(buffer).getIdOrThrow(value);
                VarInt.write(buffer, idOrThrow);
            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> registry(ResourceKey<? extends Registry<T>> registryKey) {
        return registry(registryKey, registry -> registry);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        return registry(registryKey, Registry::asHolderIdMap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holder(
        final ResourceKey<? extends Registry<T>> registryKey, final StreamCodec<? super RegistryFriendlyByteBuf, T> codec
    ) {
        return new StreamCodec<RegistryFriendlyByteBuf, Holder<T>>() {
            private static final int DIRECT_HOLDER_ID = 0;

            private IdMap<Holder<T>> getRegistryOrThrow(RegistryFriendlyByteBuf buffer) {
                return buffer.registryAccess().lookupOrThrow(registryKey).asHolderIdMap();
            }

            @Override
            public Holder<T> decode(RegistryFriendlyByteBuf buffer) {
                int i = VarInt.read(buffer);
                return i == 0 ? Holder.direct(codec.decode(buffer)) : (Holder)this.getRegistryOrThrow(buffer).byIdOrThrow(i - 1);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Holder<T> value) {
                switch (value.kind()) {
                    case REFERENCE:
                        int idOrThrow = this.getRegistryOrThrow(buffer).getIdOrThrow(value);
                        VarInt.write(buffer, idOrThrow + 1);
                        break;
                    case DIRECT:
                        VarInt.write(buffer, 0);
                        codec.encode(buffer, value.value());
                }
            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>> holderSet(final ResourceKey<? extends Registry<T>> registryKey) {
        return new StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>>() {
            private static final int NAMED_SET = -1;
            private final StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderCodec = ByteBufCodecs.holderRegistry(registryKey);

            @Override
            public HolderSet<T> decode(RegistryFriendlyByteBuf buffer) {
                int i = VarInt.read(buffer) - 1;
                if (i == -1) {
                    Registry<T> registry = buffer.registryAccess().lookupOrThrow(registryKey);
                    return registry.get(TagKey.create(registryKey, Identifier.STREAM_CODEC.decode(buffer))).orElseThrow();
                } else {
                    List<Holder<T>> list = new ArrayList<>(Math.min(i, 65536));

                    for (int i1 = 0; i1 < i; i1++) {
                        list.add(this.holderCodec.decode(buffer));
                    }

                    return HolderSet.direct(list);
                }
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, HolderSet<T> value) {
                Optional<TagKey<T>> optional = value.unwrapKey();
                if (optional.isPresent()) {
                    VarInt.write(buffer, 0);
                    Identifier.STREAM_CODEC.encode(buffer, optional.get().location());
                } else {
                    VarInt.write(buffer, value.size() + 1);

                    for (Holder<T> holder : value) {
                        this.holderCodec.encode(buffer, holder);
                    }
                }
            }
        };
    }

    static StreamCodec<ByteBuf, JsonElement> lenientJson(final int maxLength) {
        return new StreamCodec<ByteBuf, JsonElement>() {
            private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

            @Override
            public JsonElement decode(ByteBuf buffer) {
                String string = Utf8String.read(buffer, maxLength);

                try {
                    return LenientJsonParser.parse(string);
                } catch (JsonSyntaxException var4) {
                    throw new DecoderException("Failed to parse JSON", var4);
                }
            }

            @Override
            public void encode(ByteBuf buffer, JsonElement value) {
                String string = GSON.toJson(value);
                Utf8String.write(buffer, string, maxLength);
            }
        };
    }
}
