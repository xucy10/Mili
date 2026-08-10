package net.minecraft.server.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.SocketAddress;
import java.util.Locale;
import net.minecraft.server.ServerInfo;
import org.slf4j.Logger;

public class LegacyQueryHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerInfo server;
    private ByteBuf buf; // Paper

    public LegacyQueryHandler(ServerInfo server) {
        this.server = server;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        ByteBuf byteBuf = (ByteBuf)message;
        // Paper start - Make legacy ping handler more reliable
        if (this.buf != null) {
            try {
                readLegacy1_6(ctx, byteBuf);
            } finally {
                byteBuf.release();
            }
            return;
        }
        // Paper end - Make legacy ping handler more reliable

        byteBuf.markReaderIndex();
        boolean flag = true;

        try {
            try {
                if (byteBuf.readUnsignedByte() != 254) {
                    return;
                }

                SocketAddress socketAddress = ctx.channel().remoteAddress();
                int i = byteBuf.readableBytes();
                String string = null; // Paper
                if (i == 0) {
                    LOGGER.debug("Ping: (<1.3.x) from {}", net.minecraft.server.MinecraftServer.getServer().logIPs() ? socketAddress : "<ip address withheld>"); // Paper - Respect logIPs option
                    // Paper start - Call PaperServerListPingEvent and use results
                    com.destroystokyo.paper.event.server.PaperServerListPingEvent event = com.destroystokyo.paper.network.PaperLegacyStatusClient.processRequest(net.minecraft.server.MinecraftServer.getServer(), (java.net.InetSocketAddress) socketAddress, 39, null);
                    if (event == null) {
                        ctx.close();
                        byteBuf.release();
                        flag = false;
                        return;
                    }
                    string = String.format(Locale.ROOT, "%s§%d§%d", com.destroystokyo.paper.network.PaperLegacyStatusClient.getUnformattedMotd(event), event.getNumPlayers(), event.getMaxPlayers());
                    // Paper end - Call PaperServerListPingEvent and use results
                    sendFlushAndClose(ctx, createLegacyDisconnectPacket(ctx.alloc(), string));
                } else {
                    if (byteBuf.readUnsignedByte() != 1) {
                        return;
                    }

                    if (byteBuf.isReadable()) {
                        // Paper start - Replace below
                        if (byteBuf.readUnsignedByte() != LegacyProtocolUtils.CUSTOM_PAYLOAD_PACKET_ID) {
                            string = this.readLegacy1_6(ctx, byteBuf);
                            if (string == null) {
                                return;
                            }
                        }
                        // Paper end - Replace below
                    } else {
                        LOGGER.debug("Ping: (1.4-1.5.x) from {}", net.minecraft.server.MinecraftServer.getServer().logIPs() ? socketAddress : "<ip address withheld>"); // Paper - Respect logIPs option
                    }

                    // Paper start - Call PaperServerListPingEvent and use results
                    if (string == null) {
                        com.destroystokyo.paper.event.server.PaperServerListPingEvent event = com.destroystokyo.paper.network.PaperLegacyStatusClient.processRequest(net.minecraft.server.MinecraftServer.getServer(), (java.net.InetSocketAddress) socketAddress, 127, null); // Paper
                        if (event == null) {
                            ctx.close();
                            byteBuf.release();
                            flag = false;
                            return;
                        }

                        // See createVersion1Response
                        string = String.format(
                            Locale.ROOT,
                            "§1\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d",
                            event.getProtocolVersion(), this.server.getServerVersion(),
                            event.getMotd(),
                            event.getNumPlayers(),
                            event.getMaxPlayers()
                        );
                        // Paper end - Call PaperServerListPingEvent and use results
                    }
                    sendFlushAndClose(ctx, createLegacyDisconnectPacket(ctx.alloc(), string));
                }

                byteBuf.release();
                flag = false;
            } catch (RuntimeException var11) {
            }
        } finally {
            if (flag) {
                byteBuf.resetReaderIndex();
                ctx.channel().pipeline().remove(this);
                ctx.fireChannelRead(message);
            }
        }
    }

    private static boolean readCustomPayloadPacket(ByteBuf buffer) {
        short unsignedByte = buffer.readUnsignedByte();
        if (unsignedByte != 250) {
            return false;
        } else {
            String legacyString = LegacyProtocolUtils.readLegacyString(buffer);
            if (!"MC|PingHost".equals(legacyString)) {
                return false;
            } else {
                int unsignedShort = buffer.readUnsignedShort();
                if (buffer.readableBytes() != unsignedShort) {
                    return false;
                } else {
                    short unsignedByte1 = buffer.readUnsignedByte();
                    if (unsignedByte1 < 73) {
                        return false;
                    } else {
                        String legacyString1 = LegacyProtocolUtils.readLegacyString(buffer);
                        int _int = buffer.readInt();
                        return _int <= 65535;
                    }
                }
            }
        }
    }

    private static String createVersion0Response(ServerInfo server) {
        return String.format(Locale.ROOT, "%s§%d§%d", server.getMotd(), server.getPlayerCount(), server.getMaxPlayers());
    }

    private static String createVersion1Response(ServerInfo server) {
        return String.format(
            Locale.ROOT,
            "§1\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d",
            127,
            server.getServerVersion(),
            server.getMotd(),
            server.getPlayerCount(),
            server.getMaxPlayers()
        );
    }

    // Paper start
    private static @javax.annotation.Nullable String readLegacyString(ByteBuf buf) {
        int size = buf.readShort() * Character.BYTES;
        if (!buf.isReadable(size)) {
            return null;
        }

        String result = buf.toString(buf.readerIndex(), size, java.nio.charset.StandardCharsets.UTF_16BE);
        buf.skipBytes(size); // toString doesn't increase readerIndex automatically
        return result;
    }

    private @javax.annotation.Nullable String readLegacy1_6(ChannelHandlerContext ctx, ByteBuf part) {
        ByteBuf buf = this.buf;

        if (buf == null) {
            this.buf = buf = ctx.alloc().buffer();
            buf.markReaderIndex();
        } else {
            buf.resetReaderIndex();
        }

        buf.writeBytes(part);

        if (!buf.isReadable(Short.BYTES + Short.BYTES + Byte.BYTES + Short.BYTES + Integer.BYTES)) {
            return null;
        }

        String string = readLegacyString(buf);
        if (string == null) {
            return null;
        }

        if (!string.equals(LegacyProtocolUtils.CUSTOM_PAYLOAD_PACKET_PING_CHANNEL)) {
            removeHandler(ctx);
            return null;
        }

        if (!buf.isReadable(Short.BYTES) || !buf.isReadable(buf.readShort())) {
            return null;
        }

        int protocolVersion = buf.readByte();
        String host = readLegacyString(buf);
        if (host == null) {
            removeHandler(ctx);
            return null;
        }

        int port = buf.readInt();
        if (buf.isReadable()) {
            removeHandler(ctx);
            return null;
        }

        buf.release();
        this.buf = null;

        LOGGER.debug("Ping: (1.6) from {}", net.minecraft.server.MinecraftServer.getServer().logIPs() ? ctx.channel().remoteAddress() : "<ip address withheld>"); // Paper - Respect logIPs option

        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        java.net.InetSocketAddress virtualHost = com.destroystokyo.paper.network.PaperNetworkClient.prepareVirtualHost(host, port);
        com.destroystokyo.paper.event.server.PaperServerListPingEvent event = com.destroystokyo.paper.network.PaperLegacyStatusClient.processRequest(
            server, (java.net.InetSocketAddress) ctx.channel().remoteAddress(), protocolVersion, virtualHost);
        if (event == null) {
            ctx.close();
            return null;
        }

        String response = String.format("§1\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d", event.getProtocolVersion(), event.getVersion(),
            com.destroystokyo.paper.network.PaperLegacyStatusClient.getMotd(event), event.getNumPlayers(), event.getMaxPlayers());
        return response;
    }

    private void removeHandler(ChannelHandlerContext ctx) {
        ByteBuf buf = this.buf;
        this.buf = null;

        buf.resetReaderIndex();
        ctx.pipeline().remove(this);
        ctx.fireChannelRead(buf);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (this.buf != null) {
            this.buf.release();
            this.buf = null;
        }
    }
    // Paper end

    private static void sendFlushAndClose(ChannelHandlerContext ctx, ByteBuf buffer) {
        ctx.pipeline().firstContext().writeAndFlush(buffer).addListener(ChannelFutureListener.CLOSE);
    }

    private static ByteBuf createLegacyDisconnectPacket(ByteBufAllocator bufferAllocator, String reason) {
        ByteBuf byteBuf = bufferAllocator.buffer();
        byteBuf.writeByte(255);
        LegacyProtocolUtils.writeLegacyString(byteBuf, reason);
        return byteBuf;
    }
}
