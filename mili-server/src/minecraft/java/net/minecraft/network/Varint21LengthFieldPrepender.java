package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;

@Sharable
public class Varint21LengthFieldPrepender extends MessageToByteEncoder<ByteBuf> {
    public static final int MAX_VARINT21_BYTES = 3;

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf encoder, ByteBuf decoder) {
        int i = encoder.readableBytes();
        int byteSize = VarInt.getByteSize(i);
        if (byteSize > 3) {
            throw new EncoderException("Packet too large: size " + i + " is over 8");
        } else {
            decoder.ensureWritable(byteSize + i);
            VarInt.write(decoder, i);
            decoder.writeBytes(encoder, encoder.readerIndex(), i);
        }
    }
}
