package net.minecraft.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ByteProcessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class FriendlyByteBuf extends ByteBuf {
    private final ByteBuf source;
    public final java.util.@Nullable Locale adventure$locale; // Paper - track player's locale for server-side translations
    public static final short MAX_STRING_LENGTH = 32767;
    public static final int MAX_COMPONENT_STRING_LENGTH = 262144;
    private static final int PUBLIC_KEY_SIZE = 256;
    private static final int MAX_PUBLIC_KEY_HEADER_SIZE = 256;
    private static final int MAX_PUBLIC_KEY_LENGTH = 512;
    private static final Gson GSON = new Gson();
    // Paper start - Track codec depth
    public boolean trackCodecDepth;
    public byte codecDepth;
    // Paper end - Track codec depth

    public FriendlyByteBuf(ByteBuf source) {
        this.adventure$locale = PacketEncoder.ADVENTURE_LOCALE.get(); // Paper - track player's locale for server-side translations
        this.source = source;
    }

    @Deprecated
    public <T> T readWithCodecTrusted(DynamicOps<Tag> ops, Codec<T> codec) {
        return this.readWithCodec(ops, codec, NbtAccounter.unlimitedHeap());
    }

    @Deprecated
    public <T> T readWithCodec(DynamicOps<Tag> ops, Codec<T> codec, NbtAccounter nbtAccounter) {
        Tag nbt = this.readNbt(nbtAccounter);
        return codec.parse(ops, nbt).getOrThrow(exception -> new DecoderException("Failed to decode: " + exception + " " + nbt));
    }

    @Deprecated
    public <T> FriendlyByteBuf writeWithCodec(DynamicOps<Tag> ops, Codec<T> codec, T value) {
        Tag tag = codec.encodeStart(ops, value).getOrThrow(exception -> new EncoderException("Failed to encode: " + exception + " " + value));
        this.writeNbt(tag);
        return this;
    }

    public <T> T readLenientJsonWithCodec(Codec<T> codec) {
        JsonElement jsonElement = LenientJsonParser.parse(this.readUtf());
        DataResult<T> dataResult = codec.parse(JsonOps.INSTANCE, jsonElement);
        return dataResult.getOrThrow(string -> new DecoderException("Failed to decode JSON: " + string));
    }

    public <T> void writeJsonWithCodec(Codec<T> codec, T value) {
        // Paper start - Adventure; add max length parameter
        this.writeJsonWithCodec(codec, value, MAX_STRING_LENGTH);
    }
    public <T> void writeJsonWithCodec(Codec<T> codec, T value, int maxLength) {
        // Paper end - Adventure; add max length parameter
        DataResult<JsonElement> dataResult = codec.encodeStart(JsonOps.INSTANCE, value);
        this.writeUtf(GSON.toJson(dataResult.getOrThrow(exception -> new EncoderException("Failed to encode: " + exception + " " + value))), maxLength); // Paper - Adventure; add max length parameter
    }

    public static <T> IntFunction<T> limitValue(IntFunction<T> function, int limit) {
        return value -> {
            if (value > limit) {
                throw new DecoderException("Value " + value + " is larger than limit " + limit);
            } else {
                return function.apply(value);
            }
        };
    }

    public <T, C extends Collection<T>> C readCollection(IntFunction<C> collectionFactory, StreamDecoder<? super FriendlyByteBuf, T> elementReader) {
        int varInt = this.readVarInt();
        C collection = (C)collectionFactory.apply(varInt);

        for (int i = 0; i < varInt; i++) {
            collection.add(elementReader.decode(this));
        }

        return collection;
    }

    public <T> void writeCollection(Collection<T> collection, StreamEncoder<? super FriendlyByteBuf, T> elementWriter) {
        this.writeVarInt(collection.size());

        for (T object : collection) {
            elementWriter.encode(this, object);
        }
    }

    public <T> List<T> readList(StreamDecoder<? super FriendlyByteBuf, T> elementReader) {
        return this.readCollection(Lists::newArrayListWithCapacity, elementReader);
    }

    public IntList readIntIdList() {
        int varInt = this.readVarInt();
        IntList list = new IntArrayList();

        for (int i = 0; i < varInt; i++) {
            list.add(this.readVarInt());
        }

        return list;
    }

    public void writeIntIdList(IntList itIdList) {
        this.writeVarInt(itIdList.size());
        itIdList.forEach(this::writeVarInt);
    }

    public <K, V, M extends Map<K, V>> M readMap(
        IntFunction<M> mapFactory, StreamDecoder<? super FriendlyByteBuf, K> keyReader, StreamDecoder<? super FriendlyByteBuf, V> valueReader
    ) {
        int varInt = this.readVarInt();
        M map = (M)mapFactory.apply(varInt);

        for (int i = 0; i < varInt; i++) {
            K object = keyReader.decode(this);
            V object1 = valueReader.decode(this);
            map.put(object, object1);
        }

        return map;
    }

    public <K, V> Map<K, V> readMap(StreamDecoder<? super FriendlyByteBuf, K> keyReader, StreamDecoder<? super FriendlyByteBuf, V> valueReader) {
        return this.readMap(Maps::newHashMapWithExpectedSize, keyReader, valueReader);
    }

    public <K, V> void writeMap(Map<K, V> map, StreamEncoder<? super FriendlyByteBuf, K> keyWriter, StreamEncoder<? super FriendlyByteBuf, V> valueWriter) {
        this.writeVarInt(map.size());
        map.forEach((key, value) -> {
            keyWriter.encode(this, (K)key);
            valueWriter.encode(this, (V)value);
        });
    }

    public void readWithCount(Consumer<FriendlyByteBuf> reader) {
        int varInt = this.readVarInt();

        for (int i = 0; i < varInt; i++) {
            reader.accept(this);
        }
    }

    public <E extends Enum<E>> void writeEnumSet(EnumSet<E> enumSet, Class<E> enumClass) {
        E[] enums = (E[])enumClass.getEnumConstants();
        BitSet bitSet = new BitSet(enums.length);

        for (int i = 0; i < enums.length; i++) {
            bitSet.set(i, enumSet.contains(enums[i]));
        }

        this.writeFixedBitSet(bitSet, enums.length);
    }

    public <E extends Enum<E>> EnumSet<E> readEnumSet(Class<E> enumClass) {
        E[] enums = (E[])enumClass.getEnumConstants();
        BitSet fixedBitSet = this.readFixedBitSet(enums.length);
        EnumSet<E> set = EnumSet.noneOf(enumClass);

        for (int i = 0; i < enums.length; i++) {
            if (fixedBitSet.get(i)) {
                set.add(enums[i]);
            }
        }

        return set;
    }

    public <T> void writeOptional(Optional<T> optional, StreamEncoder<? super FriendlyByteBuf, T> writer) {
        if (optional.isPresent()) {
            this.writeBoolean(true);
            writer.encode(this, optional.get());
        } else {
            this.writeBoolean(false);
        }
    }

    public <T> Optional<T> readOptional(StreamDecoder<? super FriendlyByteBuf, T> reader) {
        return this.readBoolean() ? Optional.of(reader.decode(this)) : Optional.empty();
    }

    public <L, R> void writeEither(
        Either<L, R> either, StreamEncoder<? super FriendlyByteBuf, L> leftWriter, StreamEncoder<? super FriendlyByteBuf, R> rightWriter
    ) {
        either.ifLeft(value -> {
            this.writeBoolean(true);
            leftWriter.encode(this, (L)value);
        }).ifRight(value -> {
            this.writeBoolean(false);
            rightWriter.encode(this, (R)value);
        });
    }

    public <L, R> Either<L, R> readEither(StreamDecoder<? super FriendlyByteBuf, L> leftReader, StreamDecoder<? super FriendlyByteBuf, R> rightReader) {
        return this.readBoolean() ? Either.left(leftReader.decode(this)) : Either.right(rightReader.decode(this));
    }

    public <T> @Nullable T readNullable(StreamDecoder<? super FriendlyByteBuf, T> reader) {
        return readNullable(this, reader);
    }

    public static <T, B extends ByteBuf> @Nullable T readNullable(B buffer, StreamDecoder<? super B, T> reader) {
        return buffer.readBoolean() ? reader.decode(buffer) : null;
    }

    public <T> void writeNullable(@Nullable T value, StreamEncoder<? super FriendlyByteBuf, T> writer) {
        writeNullable(this, value, writer);
    }

    public static <T, B extends ByteBuf> void writeNullable(B buffer, @Nullable T value, StreamEncoder<? super B, T> writer) {
        if (value != null) {
            buffer.writeBoolean(true);
            writer.encode(buffer, value);
        } else {
            buffer.writeBoolean(false);
        }
    }

    public byte[] readByteArray() {
        return readByteArray(this);
    }

    public static byte[] readByteArray(ByteBuf buffer) {
        return readByteArray(buffer, buffer.readableBytes());
    }

    public FriendlyByteBuf writeByteArray(byte[] array) {
        writeByteArray(this, array);
        return this;
    }

    public static void writeByteArray(ByteBuf buffer, byte[] array) {
        VarInt.write(buffer, array.length);
        buffer.writeBytes(array);
    }

    public byte[] readByteArray(int maxLength) {
        return readByteArray(this, maxLength);
    }

    public static byte[] readByteArray(ByteBuf buffer, int maxSize) {
        int i = VarInt.read(buffer);
        if (i > maxSize) {
            throw new DecoderException("ByteArray with size " + i + " is bigger than allowed " + maxSize);
        } else {
            byte[] bytes = new byte[i];
            buffer.readBytes(bytes);
            return bytes;
        }
    }

    public FriendlyByteBuf writeVarIntArray(int[] array) {
        this.writeVarInt(array.length);

        for (int i : array) {
            this.writeVarInt(i);
        }

        return this;
    }

    public int[] readVarIntArray() {
        return this.readVarIntArray(this.readableBytes());
    }

    public int[] readVarIntArray(int maxLength) {
        int varInt = this.readVarInt();
        if (varInt > maxLength) {
            throw new DecoderException("VarIntArray with size " + varInt + " is bigger than allowed " + maxLength);
        } else {
            int[] ints = new int[varInt];

            for (int i = 0; i < ints.length; i++) {
                ints[i] = this.readVarInt();
            }

            return ints;
        }
    }

    public FriendlyByteBuf writeLongArray(long[] array) {
        writeLongArray(this, array);
        return this;
    }

    public static void writeLongArray(ByteBuf buffer, long[] array) {
        VarInt.write(buffer, array.length);
        writeFixedSizeLongArray(buffer, array);
    }

    public FriendlyByteBuf writeFixedSizeLongArray(long[] array) {
        writeFixedSizeLongArray(this, array);
        return this;
    }

    public static void writeFixedSizeLongArray(ByteBuf buffer, long[] array) {
        for (long l : array) {
            buffer.writeLong(l);
        }
    }

    public long[] readLongArray() {
        return readLongArray(this);
    }

    public long[] readFixedSizeLongArray(long[] output) {
        return readFixedSizeLongArray(this, output);
    }

    public static long[] readLongArray(ByteBuf buffer) {
        int i = VarInt.read(buffer);
        int i1 = buffer.readableBytes() / 8;
        if (i > i1) {
            throw new DecoderException("LongArray with size " + i + " is bigger than allowed " + i1);
        } else {
            return readFixedSizeLongArray(buffer, new long[i]);
        }
    }

    public static long[] readFixedSizeLongArray(ByteBuf buffer, long[] output) {
        for (int i = 0; i < output.length; i++) {
            output[i] = buffer.readLong();
        }

        return output;
    }

    public BlockPos readBlockPos() {
        return readBlockPos(this);
    }

    public static BlockPos readBlockPos(ByteBuf buffer) {
        return BlockPos.of(buffer.readLong());
    }

    public FriendlyByteBuf writeBlockPos(BlockPos pos) {
        writeBlockPos(this, pos);
        return this;
    }

    public static void writeBlockPos(ByteBuf buffer, BlockPos pos) {
        buffer.writeLong(pos.asLong());
    }

    public ChunkPos readChunkPos() {
        return new ChunkPos(this.readLong());
    }

    public FriendlyByteBuf writeChunkPos(ChunkPos chunkPos) {
        this.writeLong(chunkPos.toLong());
        return this;
    }

    public static ChunkPos readChunkPos(ByteBuf buffer) {
        return new ChunkPos(buffer.readLong());
    }

    public static void writeChunkPos(ByteBuf buffer, ChunkPos chunkPos) {
        buffer.writeLong(chunkPos.toLong());
    }

    public GlobalPos readGlobalPos() {
        ResourceKey<Level> resourceKey = this.readResourceKey(Registries.DIMENSION);
        BlockPos blockPos = this.readBlockPos();
        return GlobalPos.of(resourceKey, blockPos);
    }

    public void writeGlobalPos(GlobalPos pos) {
        this.writeResourceKey(pos.dimension());
        this.writeBlockPos(pos.pos());
    }

    public Vector3f readVector3f() {
        return readVector3f(this);
    }

    public static Vector3f readVector3f(ByteBuf buffer) {
        return new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public void writeVector3f(Vector3f vector3f) {
        writeVector3f(this, vector3f);
    }

    public static void writeVector3f(ByteBuf buffer, Vector3fc vector3f) {
        buffer.writeFloat(vector3f.x());
        buffer.writeFloat(vector3f.y());
        buffer.writeFloat(vector3f.z());
    }

    public Quaternionf readQuaternion() {
        return readQuaternion(this);
    }

    public static Quaternionf readQuaternion(ByteBuf buffer) {
        return new Quaternionf(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public void writeQuaternion(Quaternionf quaternion) {
        writeQuaternion(this, quaternion);
    }

    public static void writeQuaternion(ByteBuf buffer, Quaternionfc quaternion) {
        buffer.writeFloat(quaternion.x());
        buffer.writeFloat(quaternion.y());
        buffer.writeFloat(quaternion.z());
        buffer.writeFloat(quaternion.w());
    }

    public static Vec3 readVec3(ByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public Vec3 readVec3() {
        return readVec3(this);
    }

    public static void writeVec3(ByteBuf buffer, Vec3 vec3) {
        buffer.writeDouble(vec3.x());
        buffer.writeDouble(vec3.y());
        buffer.writeDouble(vec3.z());
    }

    public void writeVec3(Vec3 vec3) {
        writeVec3(this, vec3);
    }

    public Vec3 readLpVec3() {
        return LpVec3.read(this);
    }

    public void writeLpVec3(Vec3 vec3) {
        LpVec3.write(this, vec3);
    }

    public <T extends Enum<T>> T readEnum(Class<T> enumClass) {
        return enumClass.getEnumConstants()[this.readVarInt()];
    }

    public FriendlyByteBuf writeEnum(Enum<?> value) {
        return this.writeVarInt(value.ordinal());
    }

    public <T> T readById(IntFunction<T> idLookup) {
        int varInt = this.readVarInt();
        return idLookup.apply(varInt);
    }

    public <T> FriendlyByteBuf writeById(ToIntFunction<T> idGetter, T value) {
        int i = idGetter.applyAsInt(value);
        return this.writeVarInt(i);
    }

    public int readVarInt() {
        return VarInt.read(this.source);
    }

    public long readVarLong() {
        return VarLong.read(this.source);
    }

    public FriendlyByteBuf writeUUID(UUID uuid) {
        writeUUID(this, uuid);
        return this;
    }

    public static void writeUUID(ByteBuf buffer, UUID id) {
        buffer.writeLong(id.getMostSignificantBits());
        buffer.writeLong(id.getLeastSignificantBits());
    }

    public UUID readUUID() {
        return readUUID(this);
    }

    public static UUID readUUID(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    public FriendlyByteBuf writeVarInt(int input) {
        VarInt.write(this.source, input);
        return this;
    }

    public FriendlyByteBuf writeVarLong(long value) {
        VarLong.write(this.source, value);
        return this;
    }

    public FriendlyByteBuf writeNbt(@Nullable Tag tag) {
        writeNbt(this, tag);
        return this;
    }

    public static void writeNbt(ByteBuf buffer, @Nullable Tag tag) {
        if (tag == null) {
            tag = EndTag.INSTANCE;
        }

        try {
            NbtIo.writeAnyTag(tag, new ByteBufOutputStream(buffer));
        } catch (Exception var3) { // CraftBukkit - IOException -> Exception
            throw new EncoderException(var3);
        }
    }

    public @Nullable CompoundTag readNbt() {
        return readNbt(this);
    }

    public static @Nullable CompoundTag readNbt(ByteBuf buffer) {
        Tag nbt = readNbt(buffer, NbtAccounter.defaultQuota());
        if (nbt != null && !(nbt instanceof CompoundTag)) {
            throw new DecoderException("Not a compound tag: " + nbt);
        } else {
            return (CompoundTag)nbt;
        }
    }

    public static @Nullable Tag readNbt(ByteBuf buffer, NbtAccounter nbtAccounter) {
        try {
            Tag anyTag = NbtIo.readAnyTag(new ByteBufInputStream(buffer), nbtAccounter);
            return anyTag.getId() == 0 ? null : anyTag;
        } catch (IOException var3) {
            throw new EncoderException(var3);
        }
    }

    public @Nullable Tag readNbt(NbtAccounter nbtAccounter) {
        return readNbt(this, nbtAccounter);
    }

    public String readUtf() {
        return this.readUtf(MAX_STRING_LENGTH);
    }

    public String readUtf(int maxLength) {
        return Utf8String.read(this.source, maxLength);
    }

    public FriendlyByteBuf writeUtf(String string) {
        return this.writeUtf(string, MAX_STRING_LENGTH);
    }

    public FriendlyByteBuf writeUtf(String string, int maxLength) {
        Utf8String.write(this.source, string, maxLength);
        return this;
    }

    public Identifier readIdentifier() {
        return Identifier.parse(this.readUtf(MAX_STRING_LENGTH));
    }

    public FriendlyByteBuf writeIdentifier(Identifier id) {
        this.writeUtf(id.toString());
        return this;
    }

    public <T> ResourceKey<T> readResourceKey(ResourceKey<? extends Registry<T>> registryKey) {
        Identifier identifier = this.readIdentifier();
        return ResourceKey.create(registryKey, identifier);
    }

    public void writeResourceKey(ResourceKey<?> resourceKey) {
        this.writeIdentifier(resourceKey.identifier());
    }

    public <T> ResourceKey<? extends Registry<T>> readRegistryKey() {
        Identifier identifier = this.readIdentifier();
        return ResourceKey.createRegistryKey(identifier);
    }

    public Instant readInstant() {
        return Instant.ofEpochMilli(this.readLong());
    }

    public void writeInstant(Instant instant) {
        this.writeLong(instant.toEpochMilli());
    }

    public PublicKey readPublicKey() {
        try {
            return Crypt.byteToPublicKey(this.readByteArray(512));
        } catch (CryptException var2) {
            throw new DecoderException("Malformed public key bytes", var2);
        }
    }

    public FriendlyByteBuf writePublicKey(PublicKey publicKey) {
        this.writeByteArray(publicKey.getEncoded());
        return this;
    }

    public BlockHitResult readBlockHitResult() {
        BlockPos blockPos = this.readBlockPos();
        Direction direction = this.readEnum(Direction.class);
        float _float = this.readFloat();
        float _float1 = this.readFloat();
        float _float2 = this.readFloat();
        boolean _boolean = this.readBoolean();
        boolean _boolean1 = this.readBoolean();
        return new BlockHitResult(
            new Vec3((double)blockPos.getX() + _float, (double)blockPos.getY() + _float1, (double)blockPos.getZ() + _float2),
            direction,
            blockPos,
            _boolean,
            _boolean1
        );
    }

    public void writeBlockHitResult(BlockHitResult result) {
        BlockPos blockPos = result.getBlockPos();
        this.writeBlockPos(blockPos);
        this.writeEnum(result.getDirection());
        Vec3 location = result.getLocation();
        this.writeFloat((float)(location.x - blockPos.getX()));
        this.writeFloat((float)(location.y - blockPos.getY()));
        this.writeFloat((float)(location.z - blockPos.getZ()));
        this.writeBoolean(result.isInside());
        this.writeBoolean(result.isWorldBorderHit());
    }

    public BitSet readBitSet() {
        return BitSet.valueOf(this.readLongArray());
    }

    public void writeBitSet(BitSet bitSet) {
        this.writeLongArray(bitSet.toLongArray());
    }

    public BitSet readFixedBitSet(int size) {
        byte[] bytes = new byte[Mth.positiveCeilDiv(size, 8)];
        this.readBytes(bytes);
        return BitSet.valueOf(bytes);
    }

    public void writeFixedBitSet(BitSet bitSet, int size) {
        if (bitSet.length() > size) {
            throw new EncoderException("BitSet is larger than expected size (" + bitSet.length() + ">" + size + ")");
        } else {
            byte[] bytes = bitSet.toByteArray();
            this.writeBytes(Arrays.copyOf(bytes, Mth.positiveCeilDiv(size, 8)));
        }
    }

    public static int readContainerId(ByteBuf buffer) {
        return VarInt.read(buffer);
    }

    public int readContainerId() {
        return readContainerId(this.source);
    }

    public static void writeContainerId(ByteBuf buffer, int containerId) {
        VarInt.write(buffer, containerId);
    }

    public void writeContainerId(int containerId) {
        writeContainerId(this.source, containerId);
    }

    @Override
    public boolean isContiguous() {
        return this.source.isContiguous();
    }

    @Override
    public int maxFastWritableBytes() {
        return this.source.maxFastWritableBytes();
    }

    @Override
    public int capacity() {
        return this.source.capacity();
    }

    @Override
    public FriendlyByteBuf capacity(int newCapacity) {
        this.source.capacity(newCapacity);
        return this;
    }

    @Override
    public int maxCapacity() {
        return this.source.maxCapacity();
    }

    @Override
    public ByteBufAllocator alloc() {
        return this.source.alloc();
    }

    @Override
    public ByteOrder order() {
        return this.source.order();
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        return this.source.order(endianness);
    }

    @Override
    public ByteBuf unwrap() {
        return this.source;
    }

    @Override
    public boolean isDirect() {
        return this.source.isDirect();
    }

    @Override
    public boolean isReadOnly() {
        return this.source.isReadOnly();
    }

    @Override
    public ByteBuf asReadOnly() {
        return this.source.asReadOnly();
    }

    @Override
    public int readerIndex() {
        return this.source.readerIndex();
    }

    @Override
    public FriendlyByteBuf readerIndex(int readerIndex) {
        this.source.readerIndex(readerIndex);
        return this;
    }

    @Override
    public int writerIndex() {
        return this.source.writerIndex();
    }

    @Override
    public FriendlyByteBuf writerIndex(int writerIndex) {
        this.source.writerIndex(writerIndex);
        return this;
    }

    @Override
    public FriendlyByteBuf setIndex(int readerIndex, int writerIndex) {
        this.source.setIndex(readerIndex, writerIndex);
        return this;
    }

    @Override
    public int readableBytes() {
        return this.source.readableBytes();
    }

    @Override
    public int writableBytes() {
        return this.source.writableBytes();
    }

    @Override
    public int maxWritableBytes() {
        return this.source.maxWritableBytes();
    }

    @Override
    public boolean isReadable() {
        return this.source.isReadable();
    }

    @Override
    public boolean isReadable(int size) {
        return this.source.isReadable(size);
    }

    @Override
    public boolean isWritable() {
        return this.source.isWritable();
    }

    @Override
    public boolean isWritable(int size) {
        return this.source.isWritable(size);
    }

    @Override
    public FriendlyByteBuf clear() {
        this.source.clear();
        return this;
    }

    @Override
    public FriendlyByteBuf markReaderIndex() {
        this.source.markReaderIndex();
        return this;
    }

    @Override
    public FriendlyByteBuf resetReaderIndex() {
        this.source.resetReaderIndex();
        return this;
    }

    @Override
    public FriendlyByteBuf markWriterIndex() {
        this.source.markWriterIndex();
        return this;
    }

    @Override
    public FriendlyByteBuf resetWriterIndex() {
        this.source.resetWriterIndex();
        return this;
    }

    @Override
    public FriendlyByteBuf discardReadBytes() {
        this.source.discardReadBytes();
        return this;
    }

    @Override
    public FriendlyByteBuf discardSomeReadBytes() {
        this.source.discardSomeReadBytes();
        return this;
    }

    @Override
    public FriendlyByteBuf ensureWritable(int size) {
        this.source.ensureWritable(size);
        return this;
    }

    @Override
    public int ensureWritable(int size, boolean force) {
        return this.source.ensureWritable(size, force);
    }

    @Override
    public boolean getBoolean(int index) {
        return this.source.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        return this.source.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        return this.source.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        return this.source.getShort(index);
    }

    @Override
    public short getShortLE(int index) {
        return this.source.getShortLE(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        return this.source.getUnsignedShort(index);
    }

    @Override
    public int getUnsignedShortLE(int index) {
        return this.source.getUnsignedShortLE(index);
    }

    @Override
    public int getMedium(int index) {
        return this.source.getMedium(index);
    }

    @Override
    public int getMediumLE(int index) {
        return this.source.getMediumLE(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return this.source.getUnsignedMedium(index);
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        return this.source.getUnsignedMediumLE(index);
    }

    @Override
    public int getInt(int index) {
        return this.source.getInt(index);
    }

    @Override
    public int getIntLE(int index) {
        return this.source.getIntLE(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        return this.source.getUnsignedInt(index);
    }

    @Override
    public long getUnsignedIntLE(int index) {
        return this.source.getUnsignedIntLE(index);
    }

    @Override
    public long getLong(int index) {
        return this.source.getLong(index);
    }

    @Override
    public long getLongLE(int index) {
        return this.source.getLongLE(index);
    }

    @Override
    public char getChar(int index) {
        return this.source.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        return this.source.getFloat(index);
    }

    @Override
    public double getDouble(int index) {
        return this.source.getDouble(index);
    }

    @Override
    public FriendlyByteBuf getBytes(int index, ByteBuf destination) {
        this.source.getBytes(index, destination);
        return this;
    }

    @Override
    public FriendlyByteBuf getBytes(int index, ByteBuf destination, int length) {
        this.source.getBytes(index, destination, length);
        return this;
    }

    @Override
    public FriendlyByteBuf getBytes(int index, ByteBuf destination, int destinationIndex, int length) {
        this.source.getBytes(index, destination, destinationIndex, length);
        return this;
    }

    @Override
    public FriendlyByteBuf getBytes(int index, byte[] destination) {
        this.source.getBytes(index, destination);
        return this;
    }

    @Override
    public FriendlyByteBuf getBytes(int index, byte[] destination, int destinationIndex, int length) {
        this.source.getBytes(index, destination, destinationIndex, length);
        return this;
    }

    @Override
    public FriendlyByteBuf getBytes(int index, ByteBuffer destination) {
        this.source.getBytes(index, destination);
        return this;
    }

    @Override
    public FriendlyByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        this.source.getBytes(index, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return this.source.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return this.source.getBytes(index, out, position, length);
    }

    @Override
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        return this.source.getCharSequence(index, length, charset);
    }

    @Override
    public FriendlyByteBuf setBoolean(int index, boolean value) {
        this.source.setBoolean(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setByte(int index, int value) {
        this.source.setByte(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setShort(int index, int value) {
        this.source.setShort(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setShortLE(int index, int value) {
        this.source.setShortLE(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setMedium(int index, int value) {
        this.source.setMedium(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setMediumLE(int index, int value) {
        this.source.setMediumLE(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setInt(int index, int value) {
        this.source.setInt(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setIntLE(int index, int value) {
        this.source.setIntLE(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setLong(int index, long value) {
        this.source.setLong(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setLongLE(int index, long value) {
        this.source.setLongLE(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setChar(int index, int value) {
        this.source.setChar(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setFloat(int index, float value) {
        this.source.setFloat(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setDouble(int index, double value) {
        this.source.setDouble(index, value);
        return this;
    }

    @Override
    public FriendlyByteBuf setBytes(int index, ByteBuf source) {
        this.source.setBytes(index, source);
        return this;
    }

    @Override
    public FriendlyByteBuf setBytes(int index, ByteBuf source, int length) {
        this.source.setBytes(index, source, length);
        return this;
    }

    @Override
    public FriendlyByteBuf setBytes(int index, ByteBuf source, int sourceIndex, int length) {
        this.source.setBytes(index, source, sourceIndex, length);
        return this;
    }

    @Override
    public FriendlyByteBuf setBytes(int index, byte[] source) {
        this.source.setBytes(index, source);
        return this;
    }

    @Override
    public FriendlyByteBuf setBytes(int index, byte[] source, int sourceIndex, int length) {
        this.source.setBytes(index, source, sourceIndex, length);
        return this;
    }

    @Override
    public FriendlyByteBuf setBytes(int index, ByteBuffer source) {
        this.source.setBytes(index, source);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return this.source.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return this.source.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        return this.source.setBytes(index, in, position, length);
    }

    @Override
    public FriendlyByteBuf setZero(int index, int length) {
        this.source.setZero(index, length);
        return this;
    }

    @Override
    public int setCharSequence(int index, CharSequence charSequence, Charset charset) {
        return this.source.setCharSequence(index, charSequence, charset);
    }

    @Override
    public boolean readBoolean() {
        return this.source.readBoolean();
    }

    @Override
    public byte readByte() {
        return this.source.readByte();
    }

    @Override
    public short readUnsignedByte() {
        return this.source.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return this.source.readShort();
    }

    @Override
    public short readShortLE() {
        return this.source.readShortLE();
    }

    @Override
    public int readUnsignedShort() {
        return this.source.readUnsignedShort();
    }

    @Override
    public int readUnsignedShortLE() {
        return this.source.readUnsignedShortLE();
    }

    @Override
    public int readMedium() {
        return this.source.readMedium();
    }

    @Override
    public int readMediumLE() {
        return this.source.readMediumLE();
    }

    @Override
    public int readUnsignedMedium() {
        return this.source.readUnsignedMedium();
    }

    @Override
    public int readUnsignedMediumLE() {
        return this.source.readUnsignedMediumLE();
    }

    @Override
    public int readInt() {
        return this.source.readInt();
    }

    @Override
    public int readIntLE() {
        return this.source.readIntLE();
    }

    @Override
    public long readUnsignedInt() {
        return this.source.readUnsignedInt();
    }

    @Override
    public long readUnsignedIntLE() {
        return this.source.readUnsignedIntLE();
    }

    @Override
    public long readLong() {
        return this.source.readLong();
    }

    @Override
    public long readLongLE() {
        return this.source.readLongLE();
    }

    @Override
    public char readChar() {
        return this.source.readChar();
    }

    @Override
    public float readFloat() {
        return this.source.readFloat();
    }

    @Override
    public double readDouble() {
        return this.source.readDouble();
    }

    @Override
    public ByteBuf readBytes(int length) {
        return this.source.readBytes(length);
    }

    @Override
    public ByteBuf readSlice(int length) {
        return this.source.readSlice(length);
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        return this.source.readRetainedSlice(length);
    }

    @Override
    public FriendlyByteBuf readBytes(ByteBuf destination) {
        this.source.readBytes(destination);
        return this;
    }

    @Override
    public FriendlyByteBuf readBytes(ByteBuf destination, int length) {
        this.source.readBytes(destination, length);
        return this;
    }

    @Override
    public FriendlyByteBuf readBytes(ByteBuf destination, int destinationIndex, int length) {
        this.source.readBytes(destination, destinationIndex, length);
        return this;
    }

    @Override
    public FriendlyByteBuf readBytes(byte[] destination) {
        this.source.readBytes(destination);
        return this;
    }

    @Override
    public FriendlyByteBuf readBytes(byte[] destination, int destinationIndex, int length) {
        this.source.readBytes(destination, destinationIndex, length);
        return this;
    }

    @Override
    public FriendlyByteBuf readBytes(ByteBuffer destination) {
        this.source.readBytes(destination);
        return this;
    }

    @Override
    public FriendlyByteBuf readBytes(OutputStream out, int length) throws IOException {
        this.source.readBytes(out, length);
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return this.source.readBytes(out, length);
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        return this.source.readCharSequence(length, charset);
    }

    @Override
    public String readString(int length, Charset charset) {
        return this.source.readString(length, charset);
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        return this.source.readBytes(out, position, length);
    }

    @Override
    public FriendlyByteBuf skipBytes(int length) {
        this.source.skipBytes(length);
        return this;
    }

    @Override
    public FriendlyByteBuf writeBoolean(boolean value) {
        this.source.writeBoolean(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeByte(int value) {
        this.source.writeByte(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeShort(int value) {
        this.source.writeShort(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeShortLE(int value) {
        this.source.writeShortLE(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeMedium(int value) {
        this.source.writeMedium(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeMediumLE(int value) {
        this.source.writeMediumLE(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeInt(int value) {
        this.source.writeInt(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeIntLE(int value) {
        this.source.writeIntLE(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeLong(long value) {
        this.source.writeLong(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeLongLE(long value) {
        this.source.writeLongLE(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeChar(int value) {
        this.source.writeChar(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeFloat(float value) {
        this.source.writeFloat(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeDouble(double value) {
        this.source.writeDouble(value);
        return this;
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuf source) {
        this.source.writeBytes(source);
        return this;
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuf source, int length) {
        this.source.writeBytes(source, length);
        return this;
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuf source, int sourceIndex, int length) {
        this.source.writeBytes(source, sourceIndex, length);
        return this;
    }

    @Override
    public FriendlyByteBuf writeBytes(byte[] source) {
        this.source.writeBytes(source);
        return this;
    }

    @Override
    public FriendlyByteBuf writeBytes(byte[] source, int sourceIndex, int length) {
        this.source.writeBytes(source, sourceIndex, length);
        return this;
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuffer source) {
        this.source.writeBytes(source);
        return this;
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        return this.source.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        return this.source.writeBytes(in, length);
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) throws IOException {
        return this.source.writeBytes(in, position, length);
    }

    @Override
    public FriendlyByteBuf writeZero(int length) {
        this.source.writeZero(length);
        return this;
    }

    @Override
    public int writeCharSequence(CharSequence charSequence, Charset charset) {
        return this.source.writeCharSequence(charSequence, charset);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return this.source.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return this.source.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return this.source.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        return this.source.bytesBefore(index, length, value);
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        return this.source.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        return this.source.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        return this.source.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        return this.source.forEachByteDesc(index, length, processor);
    }

    @Override
    public ByteBuf copy() {
        return this.source.copy();
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return this.source.copy(index, length);
    }

    @Override
    public ByteBuf slice() {
        return this.source.slice();
    }

    @Override
    public ByteBuf retainedSlice() {
        return this.source.retainedSlice();
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return this.source.slice(index, length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        return this.source.retainedSlice(index, length);
    }

    @Override
    public ByteBuf duplicate() {
        return this.source.duplicate();
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return this.source.retainedDuplicate();
    }

    @Override
    public int nioBufferCount() {
        return this.source.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        return this.source.nioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return this.source.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return this.source.internalNioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return this.source.nioBuffers();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return this.source.nioBuffers(index, length);
    }

    @Override
    public boolean hasArray() {
        return this.source.hasArray();
    }

    @Override
    public byte[] array() {
        return this.source.array();
    }

    @Override
    public int arrayOffset() {
        return this.source.arrayOffset();
    }

    @Override
    public boolean hasMemoryAddress() {
        return this.source.hasMemoryAddress();
    }

    @Override
    public long memoryAddress() {
        return this.source.memoryAddress();
    }

    @Override
    public String toString(Charset charset) {
        return this.source.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return this.source.toString(index, length, charset);
    }

    @Override
    public int hashCode() {
        return this.source.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return this.source.equals(other);
    }

    @Override
    public int compareTo(ByteBuf other) {
        return this.source.compareTo(other);
    }

    @Override
    public String toString() {
        return this.source.toString();
    }

    @Override
    public FriendlyByteBuf retain(int increment) {
        this.source.retain(increment);
        return this;
    }

    @Override
    public FriendlyByteBuf retain() {
        this.source.retain();
        return this;
    }

    @Override
    public FriendlyByteBuf touch() {
        this.source.touch();
        return this;
    }

    @Override
    public FriendlyByteBuf touch(Object hint) {
        this.source.touch(hint);
        return this;
    }

    @Override
    public int refCnt() {
        return this.source.refCnt();
    }

    @Override
    public boolean release() {
        return this.source.release();
    }

    @Override
    public boolean release(int decrement) {
        return this.source.release(decrement);
    }
}
