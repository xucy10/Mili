package net.minecraft.network.protocol.common.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record DiscardedPayload(Identifier id, byte[] data) implements CustomPacketPayload { // Paper - store data
    public static <T extends FriendlyByteBuf> StreamCodec<T, DiscardedPayload> codec(Identifier id, int maxSize) {
        return CustomPacketPayload.codec((value, output) -> {
            // Paper start
            // Always write data
            output.writeBytes(value.data);
        }, buffer -> {
            int i = buffer.readableBytes();
            if (i >= 0 && i <= maxSize) {
                final byte[] data = new byte[i];
                buffer.readBytes(data);
                return new DiscardedPayload(id, data);
                // Paper end
            } else {
                throw new IllegalArgumentException("Payload may not be larger than " + maxSize + " bytes");
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<DiscardedPayload> type() {
        return new CustomPacketPayload.Type<>(this.id);
    }
}
