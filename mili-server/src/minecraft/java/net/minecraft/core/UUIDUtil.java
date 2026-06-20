package net.minecraft.core;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import com.mojang.util.UndashedUuid;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Util;

public final class UUIDUtil {
    public static final Codec<UUID> CODEC = Codec.INT_STREAM
        .comapFlatMap(uuids -> Util.fixedSize(uuids, 4).map(UUIDUtil::uuidFromIntArray), uuid -> Arrays.stream(uuidToIntArray(uuid)));
    public static final Codec<Set<UUID>> CODEC_SET = Codec.list(CODEC).xmap(Sets::newHashSet, Lists::newArrayList);
    public static final Codec<Set<UUID>> CODEC_LINKED_SET = Codec.list(CODEC).xmap(Sets::newLinkedHashSet, Lists::newArrayList);
    public static final Codec<UUID> STRING_CODEC = Codec.STRING.comapFlatMap(uuid -> {
        try {
            return DataResult.success(UUID.fromString(uuid), Lifecycle.stable());
        } catch (IllegalArgumentException var2) {
            return DataResult.error(() -> "Invalid UUID " + uuid + ": " + var2.getMessage());
        }
    }, UUID::toString);
    public static final Codec<UUID> AUTHLIB_CODEC = Codec.withAlternative(Codec.STRING.comapFlatMap(uuid -> {
        try {
            return DataResult.success(UndashedUuid.fromStringLenient(uuid), Lifecycle.stable());
        } catch (IllegalArgumentException var2) {
            return DataResult.error(() -> "Invalid UUID " + uuid + ": " + var2.getMessage());
        }
    }, UndashedUuid::toString), CODEC);
    public static final Codec<UUID> LENIENT_CODEC = Codec.withAlternative(CODEC, STRING_CODEC);
    public static final StreamCodec<ByteBuf, UUID> STREAM_CODEC = new StreamCodec<ByteBuf, UUID>() {
        @Override
        public UUID decode(ByteBuf buffer) {
            return FriendlyByteBuf.readUUID(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, UUID value) {
            FriendlyByteBuf.writeUUID(buffer, value);
        }
    };
    public static final int UUID_BYTES = 16;
    private static final String UUID_PREFIX_OFFLINE_PLAYER = "OfflinePlayer:";

    private UUIDUtil() {
    }

    public static UUID uuidFromIntArray(int[] bits) {
        return new UUID((long)bits[0] << 32 | bits[1] & 4294967295L, (long)bits[2] << 32 | bits[3] & 4294967295L);
    }

    public static int[] uuidToIntArray(UUID uuid) {
        long mostSignificantBits = uuid.getMostSignificantBits();
        long leastSignificantBits = uuid.getLeastSignificantBits();
        return leastMostToIntArray(mostSignificantBits, leastSignificantBits);
    }

    private static int[] leastMostToIntArray(long most, long least) {
        return new int[]{(int)(most >> 32), (int)most, (int)(least >> 32), (int)least};
    }

    public static byte[] uuidToByteArray(UUID uuid) {
        byte[] bytes = new byte[16];
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        return bytes;
    }

    public static UUID readUUID(Dynamic<?> dynamic) {
        int[] ints = dynamic.asIntStream().toArray();
        if (ints.length != 4) {
            throw new IllegalArgumentException("Could not read UUID. Expected int-array of length 4, got " + ints.length + ".");
        } else {
            return uuidFromIntArray(ints);
        }
    }

    public static UUID createOfflinePlayerUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public static GameProfile createOfflineProfile(String username) {
        UUID uuid = createOfflinePlayerUUID(username);
        return new GameProfile(uuid, username);
    }
}
