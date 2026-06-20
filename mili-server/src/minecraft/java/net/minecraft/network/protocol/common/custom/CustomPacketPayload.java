package net.minecraft.network.protocol.common.custom;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.resources.Identifier;

public interface CustomPacketPayload {
    CustomPacketPayload.Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> encoder, StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String id) {
        return new CustomPacketPayload.Type<>(Identifier.withDefaultNamespace(id));
    }

    static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> codec(
        final CustomPacketPayload.FallbackProvider<B> fallbackProvider, List<CustomPacketPayload.TypeAndCodec<? super B, ?>> typeAndCodecs
    ) {
        final Map<Identifier, StreamCodec<? super B, ? extends CustomPacketPayload>> map = typeAndCodecs.stream()
            .collect(Collectors.toUnmodifiableMap(typeAndCodec -> typeAndCodec.type().id(), CustomPacketPayload.TypeAndCodec::codec));
        return new StreamCodec<B, CustomPacketPayload>() {
            private StreamCodec<? super B, ? extends CustomPacketPayload> findCodec(Identifier typeId) {
                StreamCodec<? super B, ? extends CustomPacketPayload> streamCodec = map.get(typeId);
                return streamCodec != null ? streamCodec : fallbackProvider.create(typeId);
            }

            private <T extends CustomPacketPayload> void writeCap(B buffer, CustomPacketPayload.Type<T> type, CustomPacketPayload payload) {
                buffer.writeIdentifier(type.id());
                StreamCodec<B, T> streamCodec = (StreamCodec<B, T>) this.findCodec(type.id);
                streamCodec.encode(buffer, (T)payload);
            }

            @Override
            public void encode(B buffer, CustomPacketPayload value) {
                this.writeCap(buffer, value.type(), value);
            }

            @Override
            public CustomPacketPayload decode(B buffer) {
                Identifier identifier = buffer.readIdentifier();
                return (CustomPacketPayload)this.findCodec(identifier).decode(buffer);
            }
        };
    }

    public interface FallbackProvider<B extends FriendlyByteBuf> {
        StreamCodec<B, ? extends CustomPacketPayload> create(Identifier id);
    }

    public record Type<T extends CustomPacketPayload>(Identifier id) {
    }

    public record TypeAndCodec<B extends FriendlyByteBuf, T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<B, T> codec) {
    }
}
