package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class CompressionDecoder extends ByteToMessageDecoder {
    public static final int MAXIMUM_COMPRESSED_LENGTH = 2097152;
    public static final int MAXIMUM_UNCOMPRESSED_LENGTH = 8388608;
    private com.velocitypowered.natives.compression.VelocityCompressor compressor; // Paper - Use Velocity cipher
    private Inflater inflater;
    private int threshold;
    private boolean validateDecompressed;

    // Paper start - Use Velocity cipher
    @io.papermc.paper.annotation.DoNotUse
    public CompressionDecoder(int threshold, boolean validateDecompressed) {
        this(null, threshold, validateDecompressed);
    }
    public CompressionDecoder(com.velocitypowered.natives.compression.VelocityCompressor compressor, int threshold, boolean validateDecompressed) {
        this.threshold = threshold;
        this.validateDecompressed = validateDecompressed;
        this.inflater = compressor == null ? new Inflater() : null;
        this.compressor = compressor;
        // Paper end - Use Velocity cipher
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int i = VarInt.read(in);
        if (i == 0) {
            out.add(in.readBytes(in.readableBytes()));
        } else {
            if (this.validateDecompressed) {
                if (i < this.threshold) {
                    throw new DecoderException("Badly compressed packet - size of " + i + " is below server threshold of " + this.threshold);
                }

                if (i > 8388608) {
                    throw new DecoderException("Badly compressed packet - size of " + i + " is larger than protocol maximum of 8388608");
                }
            }

            if (inflater != null) { // Paper - Use Velocity cipher; fallback to vanilla inflater
            this.setupInflaterInput(in);
            ByteBuf byteBuf = this.inflate(ctx, i);
            this.inflater.reset();
            out.add(byteBuf);
            return; // Paper - Use Velocity cipher
            } // Paper - use velocity compression

            // Paper start - Use Velocity cipher
            int claimedUncompressedSize = i; // OBFHELPER
            ByteBuf compatibleIn = com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible(ctx.alloc(), this.compressor, in);
            ByteBuf uncompressed = com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer(ctx.alloc(), this.compressor, claimedUncompressedSize);
            try {
                this.compressor.inflate(compatibleIn, uncompressed, claimedUncompressedSize);
                out.add(uncompressed);
                in.clear();
            } catch (Exception e) {
                uncompressed.release();
                throw e;
            } finally {
                compatibleIn.release();
            }
            // Paper end - Use Velocity cipher
        }
    }

    // Paper start - Use Velocity cipher
    @Override
    public void handlerRemoved0(ChannelHandlerContext ctx) {
        if (this.compressor != null) {
            this.compressor.close();
        }
    }
    // Paper end - Use Velocity cipher

    private void setupInflaterInput(ByteBuf buffer) {
        ByteBuffer byteBuffer;
        if (buffer.nioBufferCount() > 0) {
            byteBuffer = buffer.nioBuffer();
            buffer.skipBytes(buffer.readableBytes());
        } else {
            byteBuffer = ByteBuffer.allocateDirect(buffer.readableBytes());
            buffer.readBytes(byteBuffer);
            byteBuffer.flip();
        }

        this.inflater.setInput(byteBuffer);
    }

    private ByteBuf inflate(ChannelHandlerContext ctx, int size) throws DataFormatException {
        ByteBuf byteBuf = ctx.alloc().directBuffer(size);

        try {
            ByteBuffer byteBuffer = byteBuf.internalNioBuffer(0, size);
            int position = byteBuffer.position();
            this.inflater.inflate(byteBuffer);
            int i = byteBuffer.position() - position;
            if (i != size) {
                throw new DecoderException("Badly compressed packet - actual length of uncompressed payload " + i + " is does not match declared size " + size);
            } else {
                byteBuf.writerIndex(byteBuf.writerIndex() + i);
                return byteBuf;
            }
        } catch (Exception var7) {
            byteBuf.release();
            throw var7;
        }
    }

    // Paper start - Use Velocity cipher
    public void setThreshold(com.velocitypowered.natives.compression.VelocityCompressor compressor, int threshold, boolean validateDecompressed) {
        if (this.compressor == null && compressor != null) { // Only re-configure once. Re-reconfiguring would require closing the native compressor.
            this.compressor = compressor;
            this.inflater = null;
        }
        // Paper end - Use Velocity cipher
        this.threshold = threshold;
        this.validateDecompressed = validateDecompressed;
    }
}
