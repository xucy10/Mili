package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SignatureUpdater;

public record SignedMessageBody(String content, Instant timeStamp, long salt, LastSeenMessages lastSeen) {
    public static final MapCodec<SignedMessageBody> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.STRING.fieldOf("content").forGetter(SignedMessageBody::content),
                ExtraCodecs.INSTANT_ISO8601.fieldOf("time_stamp").forGetter(SignedMessageBody::timeStamp),
                Codec.LONG.fieldOf("salt").forGetter(SignedMessageBody::salt),
                LastSeenMessages.CODEC.optionalFieldOf("last_seen", LastSeenMessages.EMPTY).forGetter(SignedMessageBody::lastSeen)
            )
            .apply(instance, SignedMessageBody::new)
    );

    public static SignedMessageBody unsigned(String content) {
        return new SignedMessageBody(content, Instant.now(), 0L, LastSeenMessages.EMPTY);
    }

    public void updateSignature(SignatureUpdater.Output output) throws SignatureException {
        output.update(Longs.toByteArray(this.salt));
        output.update(Longs.toByteArray(this.timeStamp.getEpochSecond()));
        byte[] bytes = this.content.getBytes(StandardCharsets.UTF_8);
        output.update(Ints.toByteArray(bytes.length));
        output.update(bytes);
        this.lastSeen.updateSignature(output);
    }

    public SignedMessageBody.Packed pack(MessageSignatureCache signatureCache) {
        return new SignedMessageBody.Packed(this.content, this.timeStamp, this.salt, this.lastSeen.pack(signatureCache));
    }

    public record Packed(String content, Instant timeStamp, long salt, LastSeenMessages.Packed lastSeen) {
        public Packed(FriendlyByteBuf buffer) {
            this(buffer.readUtf(256), buffer.readInstant(), buffer.readLong(), new LastSeenMessages.Packed(buffer));
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(this.content, 256);
            buffer.writeInstant(this.timeStamp);
            buffer.writeLong(this.salt);
            this.lastSeen.write(buffer);
        }

        public Optional<SignedMessageBody> unpack(MessageSignatureCache signatureCache) {
            return this.lastSeen
                .unpack(signatureCache)
                .map(lastSeenMessages -> new SignedMessageBody(this.content, this.timeStamp, this.salt, lastSeenMessages));
        }
    }
}
