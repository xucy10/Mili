package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class Varint21FrameDecoder extends ByteToMessageDecoder {
    private static final int MAX_VARINT21_BYTES = 3;
    private final ByteBuf helperBuf = Unpooled.directBuffer(3);
    private final @Nullable BandwidthDebugMonitor monitor;

    public Varint21FrameDecoder(@Nullable BandwidthDebugMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        this.helperBuf.release();
    }

    private static boolean copyVarint(ByteBuf in, ByteBuf out) {
        for (int i = 0; i < 3; i++) {
            if (!in.isReadable()) {
                return false;
            }

            byte _byte = in.readByte();
            out.writeByte(_byte);
            if (!VarInt.hasContinuationBit(_byte)) {
                return true;
            }
        }

        throw new CorruptedFrameException("length wider than 21-bit");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // Paper start - Perf: Optimize exception handling; if channel is not active just discard the packet
        if (!ctx.channel().isActive()) {
            in.skipBytes(in.readableBytes());
            return;
        }
        // Paper end - Perf: Optimize exception handling
        in.markReaderIndex();
        this.helperBuf.clear();
        if (!copyVarint(in, this.helperBuf)) {
            in.resetReaderIndex();
        } else {
            int i = VarInt.read(this.helperBuf);
            if (i == 0) {
                throw new CorruptedFrameException("Frame length cannot be zero");
            } else if (in.readableBytes() < i) {
                in.resetReaderIndex();
            } else {
                if (this.monitor != null) {
                    this.monitor.onReceive(i + VarInt.getByteSize(i));
                }

                out.add(in.readBytes(i));
            }
        }
    }
}
