package net.minecraft.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;

public class PacketBundleUnpacker extends MessageToMessageEncoder<Packet<?>> {
    private final BundlerInfo bundlerInfo;

    public PacketBundleUnpacker(BundlerInfo bundlerInfo) {
        this.bundlerInfo = bundlerInfo;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet<?> packet, List<Object> list) throws Exception {
        this.bundlerInfo.unbundlePacket(packet, list::add);
        if (packet.isTerminal()) {
            ctx.pipeline().remove(ctx.name());
        }
    }
}
