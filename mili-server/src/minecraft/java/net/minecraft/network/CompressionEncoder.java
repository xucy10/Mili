package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.Deflater;

public class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {
    @javax.annotation.Nullable private final byte[] encodeBuf; // Paper - Use Velocity cipher
    @javax.annotation.Nullable // Paper - Use Velocity cipher
    private final Deflater deflater;
    @javax.annotation.Nullable private final com.velocitypowered.natives.compression.VelocityCompressor compressor; // Paper - Use Velocity cipher
    private int threshold;

    // Paper start - Use Velocity cipher
    public CompressionEncoder(int threshold) {
        this(null, threshold);
    }
    public CompressionEncoder(@javax.annotation.Nullable com.velocitypowered.natives.compression.VelocityCompressor compressor, int threshold) {
        this.threshold = threshold;
        if (compressor == null) {
            this.encodeBuf = new byte[8192];
            this.deflater = new Deflater();
        } else {
            this.encodeBuf = null;
            this.deflater = null;
        }
        this.compressor = compressor;
        // Paper end - Use Velocity cipher
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf encodingByteBuf, ByteBuf byteBuf) throws Exception { // Paper - Use Velocity cipher
        int i = encodingByteBuf.readableBytes();
        if (i > 8388608) {
            throw new IllegalArgumentException("Packet too big (is " + i + ", should be less than 8388608)");
        } else {
            if (i < this.threshold) {
                VarInt.write(byteBuf, 0);
                byteBuf.writeBytes(encodingByteBuf);
            } else {
                if (this.deflater != null) { // Paper - Use Velocity cipher
                byte[] bytes = new byte[i];
                encodingByteBuf.readBytes(bytes);
                VarInt.write(byteBuf, bytes.length);
                this.deflater.setInput(bytes, 0, i);
                this.deflater.finish();

                while (!this.deflater.finished()) {
                    int i1 = this.deflater.deflate(this.encodeBuf);
                    byteBuf.writeBytes(this.encodeBuf, 0, i1);
                }

                this.deflater.reset();
                    // Paper start - Use Velocity cipher
                    return;
                }

                VarInt.write(byteBuf, i);
                final ByteBuf compatibleIn = com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible(ctx.alloc(), this.compressor, encodingByteBuf);
                try {
                    this.compressor.deflate(compatibleIn, byteBuf);
                } finally {
                    compatibleIn.release();
                }
            }
        }
    }

    public int getThreshold() {
        return this.threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    // Paper start - Use Velocity cipher
    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) throws Exception {
        if (this.compressor != null) {
            // We allocate bytes to be compressed plus 1 byte. This covers two cases:
            //
            // - Compression
            //    According to https://github.com/ebiggers/libdeflate/blob/master/libdeflate.h#L103,
            //    if the data compresses well (and we do not have some pathological case) then the maximum
            //    size the compressed size will ever be is the input size minus one.
            // - Uncompressed
            //    This is fairly obvious - we will then have one more than the uncompressed size.
            final int initialBufferSize = msg.readableBytes() + 1;
            return com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer(ctx.alloc(), this.compressor, initialBufferSize);
        }

        return super.allocateBuffer(ctx, msg, preferDirect);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (this.compressor != null) {
            this.compressor.close();
        }
    }
    // Paper end - Use Velocity cipher
}
