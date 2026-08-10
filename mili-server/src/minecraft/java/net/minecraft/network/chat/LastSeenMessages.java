package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.mojang.serialization.Codec;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.SignatureUpdater;

public record LastSeenMessages(List<MessageSignature> entries) {
    public static final Codec<LastSeenMessages> CODEC = MessageSignature.CODEC.listOf().xmap(LastSeenMessages::new, LastSeenMessages::entries);
    public static LastSeenMessages EMPTY = new LastSeenMessages(List.of());
    public static final int LAST_SEEN_MESSAGES_MAX_LENGTH = 20;

    public void updateSignature(SignatureUpdater.Output updaterOutput) throws SignatureException {
        updaterOutput.update(Ints.toByteArray(this.entries.size()));

        for (MessageSignature messageSignature : this.entries) {
            updaterOutput.update(messageSignature.bytes());
        }
    }

    public LastSeenMessages.Packed pack(MessageSignatureCache signatureCache) {
        return new LastSeenMessages.Packed(this.entries.stream().map(signature -> signature.pack(signatureCache)).toList());
    }

    public byte computeChecksum() {
        int i = 1;

        for (MessageSignature messageSignature : this.entries) {
            i = 31 * i + messageSignature.checksum();
        }

        byte b = (byte)i;
        return b == 0 ? 1 : b;
    }

    public record Packed(List<MessageSignature.Packed> entries) {
        public static final LastSeenMessages.Packed EMPTY = new LastSeenMessages.Packed(List.of());

        public Packed(FriendlyByteBuf buffer) {
            this(buffer.readCollection(FriendlyByteBuf.<List<MessageSignature.Packed>>limitValue(ArrayList::new, 20), MessageSignature.Packed::read));
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeCollection(this.entries, MessageSignature.Packed::write);
        }

        public Optional<LastSeenMessages> unpack(MessageSignatureCache signatureCache) {
            List<MessageSignature> list = new ArrayList<>(this.entries.size());

            for (MessageSignature.Packed packed : this.entries) {
                Optional<MessageSignature> optional = packed.unpack(signatureCache);
                if (optional.isEmpty()) {
                    return Optional.empty();
                }

                list.add(optional.get());
            }

            return Optional.of(new LastSeenMessages(list));
        }
    }

    public record Update(int offset, BitSet acknowledged, byte checksum) {
        public static final byte IGNORE_CHECKSUM = 0;

        public Update(FriendlyByteBuf buffer) {
            this(buffer.readVarInt(), buffer.readFixedBitSet(20), buffer.readByte());
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.offset);
            buffer.writeFixedBitSet(this.acknowledged, 20);
            buffer.writeByte(this.checksum);
        }

        public boolean verifyChecksum(LastSeenMessages messages) {
            return this.checksum == 0 || this.checksum == messages.computeChecksum();
        }
    }
}
