package net.minecraft.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;

public class PacketBundlePacker extends MessageToMessageDecoder<Packet<?>> {
    private final BundlerInfo bundlerInfo;
    private BundlerInfo.@Nullable Bundler currentBundler;

    public PacketBundlePacker(BundlerInfo bundlerInfo) {
        this.bundlerInfo = bundlerInfo;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Packet<?> packet, List<Object> list) throws Exception {
        if (this.currentBundler != null) {
            verifyNonTerminalPacket(packet);
            Packet<?> packet1 = this.currentBundler.addPacket(packet);
            if (packet1 != null) {
                this.currentBundler = null;
                list.add(packet1);
            }
        } else {
            BundlerInfo.Bundler bundler = this.bundlerInfo.startPacketBundling(packet);
            if (bundler != null) {
                verifyNonTerminalPacket(packet);
                this.currentBundler = bundler;
            } else {
                list.add(packet);
                if (packet.isTerminal()) {
                    ctx.pipeline().remove(ctx.name());
                }
            }
        }
    }

    private static void verifyNonTerminalPacket(Packet<?> packet) {
        if (packet.isTerminal()) {
            throw new DecoderException("Terminal message received in bundle");
        }
    }
}
