package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.Packet;

public class UnconfiguredPipelineHandler {
    public static <T extends PacketListener> UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundProtocol(ProtocolInfo<T> protocolInfo) {
        return setupInboundHandler(new PacketDecoder<>(protocolInfo));
    }

    private static UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundHandler(ChannelInboundHandler handler) {
        return ctx -> {
            ctx.pipeline().replace(ctx.name(), "decoder", handler);
            ctx.channel().config().setAutoRead(true);
        };
    }

    public static <T extends PacketListener> UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundProtocol(ProtocolInfo<T> protocolInfo) {
        return setupOutboundHandler(new PacketEncoder<>(protocolInfo));
    }

    private static UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundHandler(ChannelOutboundHandler handler) {
        return ctx -> ctx.pipeline().replace(ctx.name(), "encoder", handler);
    }

    public static class Inbound extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) {
            if (!(message instanceof ByteBuf) && !(message instanceof Packet)) {
                ctx.fireChannelRead(message);
            } else {
                ReferenceCountUtil.release(message);
                throw new DecoderException("Pipeline has no inbound protocol configured, can't process packet " + message);
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
            if (message instanceof UnconfiguredPipelineHandler.InboundConfigurationTask inboundConfigurationTask) {
                try {
                    inboundConfigurationTask.run(ctx);
                } finally {
                    ReferenceCountUtil.release(message);
                }

                promise.setSuccess();
            } else {
                ctx.write(message, promise);
            }
        }
    }

    @FunctionalInterface
    public interface InboundConfigurationTask {
        void run(ChannelHandlerContext ctx);

        default UnconfiguredPipelineHandler.InboundConfigurationTask andThen(UnconfiguredPipelineHandler.InboundConfigurationTask task) {
            return ctx -> {
                this.run(ctx);
                task.run(ctx);
            };
        }
    }

    public static class Outbound extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
            if (message instanceof Packet) {
                ReferenceCountUtil.release(message);
                throw new EncoderException("Pipeline has no outbound protocol configured, can't process packet " + message);
            } else {
                if (message instanceof UnconfiguredPipelineHandler.OutboundConfigurationTask outboundConfigurationTask) {
                    try {
                        outboundConfigurationTask.run(ctx);
                    } finally {
                        ReferenceCountUtil.release(message);
                    }

                    promise.setSuccess();
                } else {
                    ctx.write(message, promise);
                }
            }
        }
    }

    @FunctionalInterface
    public interface OutboundConfigurationTask {
        void run(ChannelHandlerContext ctx);

        default UnconfiguredPipelineHandler.OutboundConfigurationTask andThen(UnconfiguredPipelineHandler.OutboundConfigurationTask task) {
            return ctx -> {
                this.run(ctx);
                task.run(ctx);
            };
        }
    }
}
