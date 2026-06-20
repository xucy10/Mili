package net.minecraft.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.slf4j.Logger;

public class PacketDecoder<T extends PacketListener> extends ByteToMessageDecoder implements ProtocolSwapHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ProtocolInfo<T> protocolInfo;

    public PacketDecoder(ProtocolInfo<T> protocolInfo) {
        this.protocolInfo = protocolInfo;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int i = in.readableBytes();

        Packet<? super T> packet;
        try {
            packet = this.protocolInfo.codec().decode(in);
        } catch (Exception var7) {
            if (var7 instanceof SkipPacketException) {
                in.skipBytes(in.readableBytes());
            }

            throw var7;
        }

        PacketType<? extends Packet<? super T>> packetType = packet.type();
        JvmProfiler.INSTANCE.onPacketReceived(this.protocolInfo.id(), packetType, ctx.channel().remoteAddress(), i);
        if (in.readableBytes() > 0) {
            throw new IOException(
                "Packet "
                    + this.protocolInfo.id().id()
                    + "/"
                    + packetType
                    + " ("
                    + packet.getClass().getSimpleName()
                    + ") was larger than I expected, found "
                    + in.readableBytes()
                    + " bytes extra whilst reading packet "
                    + packetType
            );
        } else {
            out.add(packet);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    Connection.PACKET_RECEIVED_MARKER, " IN: [{}:{}] {} -> {} bytes", this.protocolInfo.id().id(), packetType, packet.getClass().getName(), i
                );
            }

            ProtocolSwapHandler.handleInboundTerminalPacket(ctx, packet);
        }
    }
}
