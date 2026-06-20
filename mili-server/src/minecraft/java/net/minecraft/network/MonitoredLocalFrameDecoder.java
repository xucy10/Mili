package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class MonitoredLocalFrameDecoder extends ChannelInboundHandlerAdapter {
    private final BandwidthDebugMonitor monitor;

    public MonitoredLocalFrameDecoder(BandwidthDebugMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        msg = HiddenByteBuf.unpack(msg);
        if (msg instanceof ByteBuf byteBuf) {
            this.monitor.onReceive(byteBuf.readableBytes());
        }

        ctx.fireChannelRead(msg);
    }
}
