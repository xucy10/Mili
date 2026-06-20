package net.minecraft.network.chat;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SignatureUpdater;
import net.minecraft.util.SignatureValidator;
import org.jspecify.annotations.Nullable;

public record MessageSignature(byte[] bytes) {
    public net.kyori.adventure.chat.SignedMessage.Signature adventure() { return () -> this.bytes; } // Paper - adventure; support signed messages
    public static final Codec<MessageSignature> CODEC = ExtraCodecs.BASE64_STRING.xmap(MessageSignature::new, MessageSignature::bytes);
    public static final int BYTES = 256;

    public MessageSignature(byte[] bytes) {
        Preconditions.checkState(bytes.length == 256, "Invalid message signature size");
        this.bytes = bytes;
    }

    public static MessageSignature read(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[256];
        buffer.readBytes(bytes);
        return new MessageSignature(bytes);
    }

    public static void write(FriendlyByteBuf buffer, MessageSignature signature) {
        buffer.writeBytes(signature.bytes);
    }

    public boolean verify(SignatureValidator validator, SignatureUpdater updater) {
        return validator.validate(updater, this.bytes);
    }

    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(this.bytes);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MessageSignature messageSignature && Arrays.equals(this.bytes, messageSignature.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.bytes);
    }

    @Override
    public String toString() {
        return Base64.getEncoder().encodeToString(this.bytes);
    }

    public static String describe(@Nullable MessageSignature signature) {
        return signature == null ? "<no signature>" : signature.toString();
    }

    public MessageSignature.Packed pack(MessageSignatureCache signatureCache) {
        int i = signatureCache.pack(this);
        return i != -1 ? new MessageSignature.Packed(i) : new MessageSignature.Packed(this);
    }

    public int checksum() {
        return Arrays.hashCode(this.bytes);
    }

    public record Packed(int id, @Nullable MessageSignature fullSignature) {
        public static final int FULL_SIGNATURE = -1;

        public Packed(MessageSignature fullSignature) {
            this(-1, fullSignature);
        }

        public Packed(int id) {
            this(id, null);
        }

        public static MessageSignature.Packed read(FriendlyByteBuf buffer) {
            int i = buffer.readVarInt() - 1;
            return i == -1 ? new MessageSignature.Packed(MessageSignature.read(buffer)) : new MessageSignature.Packed(i);
        }

        public static void write(FriendlyByteBuf buffer, MessageSignature.Packed packed) {
            buffer.writeVarInt(packed.id() + 1);
            if (packed.fullSignature() != null) {
                MessageSignature.write(buffer, packed.fullSignature());
            }
        }

        public Optional<MessageSignature> unpack(MessageSignatureCache signatureCache) {
            return this.fullSignature != null ? Optional.of(this.fullSignature) : Optional.ofNullable(signatureCache.unpack(this.id));
        }
    }
}
