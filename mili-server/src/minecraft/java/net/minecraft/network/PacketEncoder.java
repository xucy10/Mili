package net.minecraft.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.slf4j.Logger;

public class PacketEncoder<T extends PacketListener> extends MessageToByteEncoder<Packet<T>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ProtocolInfo<T> protocolInfo;

    public PacketEncoder(ProtocolInfo<T> protocolInfo) {
        this.protocolInfo = protocolInfo;
    }

    static final ThreadLocal<java.util.Locale> ADVENTURE_LOCALE = ThreadLocal.withInitial(() -> null); // Paper - adventure; set player's locale
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Packet<T> packet, ByteBuf byteBuf) throws Exception {
        PacketType<? extends Packet<? super T>> packetType = packet.type();

        try {
            ADVENTURE_LOCALE.set(channelHandlerContext.channel().attr(io.papermc.paper.adventure.PaperAdventure.LOCALE_ATTRIBUTE).get()); // Paper - adventure; set player's locale
            this.protocolInfo.codec().encode(byteBuf, packet);
            int i = byteBuf.readableBytes();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    Connection.PACKET_SENT_MARKER, "OUT: [{}:{}] {} -> {} bytes", this.protocolInfo.id().id(), packetType, packet.getClass().getName(), i
                );
            }

            JvmProfiler.INSTANCE.onPacketSent(this.protocolInfo.id(), packetType, channelHandlerContext.channel().remoteAddress(), i);
        } catch (Throwable var9) {
            LOGGER.error("Error sending packet {}", packetType, var9);
            if (packet.isSkippable()) {
                throw new SkipPacketEncoderException(var9);
            }

            throw var9;
        } finally {
            // Paper start - Handle large packets disconnecting client
            int packetLength = byteBuf.readableBytes();
            if (packetLength > MAX_PACKET_SIZE || (packetLength > MAX_FINAL_PACKET_SIZE && packet.hasLargePacketFallback())) {
                throw new PacketTooLargeException(packet, packetLength);
            }
            // Paper end - Handle large packets disconnecting client
            ProtocolSwapHandler.handleOutboundTerminalPacket(channelHandlerContext, packet);
        }
    }

    // Paper start
    // packet size is encoded into 3-byte varint
    private static final int MAX_FINAL_PACKET_SIZE = (1 << 21) - 1;
    // Vanilla Max size for the encoder (before compression)
    private static final int MAX_PACKET_SIZE = 8388608;

    public static class PacketTooLargeException extends RuntimeException {
        private final Packet<?> packet;

        PacketTooLargeException(Packet<?> packet, int packetLength) {
            super("PacketTooLarge - " + packet.getClass().getSimpleName() + " is " + packetLength + ". Max is " + MAX_PACKET_SIZE);
            this.packet = packet;
        }

        public Packet<?> getPacket() {
            return this.packet;
        }
    }
    // Paper end
}
