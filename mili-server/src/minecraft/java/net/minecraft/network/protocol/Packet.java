package net.minecraft.network.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;

public interface Packet<T extends PacketListener> {
    PacketType<? extends Packet<T>> type();

    void handle(T handler);

    // Paper start
    default boolean hasLargePacketFallback() {
        return false;
    }

    /**
     * override {@link #hasLargePacketFallback()} to return true when overriding in subclasses
     */
    default boolean packetTooLarge(net.minecraft.network.Connection manager) {
        return false;
    }
    // Paper end

    default boolean isSkippable() {
        return false;
    }

    default boolean isTerminal() {
        return false;
    }

    static <B extends ByteBuf, T extends Packet<?>> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> encoder, StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }

    // Paper start
    /**
     * @param player Null if not at PLAY stage yet
     */
    default void onPacketDispatch(@javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player) {
    }

    /**
     * @param player Null if not at PLAY stage yet
     * @param future Can be null if packet was cancelled
     */
    default void onPacketDispatchFinish(@javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player, @javax.annotation.Nullable io.netty.channel.ChannelFuture future) {
    }

    default boolean hasFinishListener() {
        return false;
    }

    default boolean isReady() {
        return true;
    }

    @javax.annotation.Nullable
    default java.util.List<Packet<?>> getExtraPackets() {
        return null;
    }
    // Paper end
}
