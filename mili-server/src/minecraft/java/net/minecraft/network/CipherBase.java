package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public class CipherBase {
    private final Cipher cipher;
    private byte[] heapIn = new byte[0];
    private byte[] heapOut = new byte[0];

    protected CipherBase(Cipher cipher) {
        this.cipher = cipher;
    }

    private byte[] bufToByte(ByteBuf buffer) {
        int i = buffer.readableBytes();
        if (this.heapIn.length < i) {
            this.heapIn = new byte[i];
        }

        buffer.readBytes(this.heapIn, 0, i);
        return this.heapIn;
    }

    protected ByteBuf decipher(ChannelHandlerContext ctx, ByteBuf buffer) throws ShortBufferException {
        int i = buffer.readableBytes();
        byte[] bytes = this.bufToByte(buffer);
        ByteBuf byteBuf = ctx.alloc().heapBuffer(this.cipher.getOutputSize(i));
        byteBuf.writerIndex(this.cipher.update(bytes, 0, i, byteBuf.array(), byteBuf.arrayOffset()));
        return byteBuf;
    }

    protected void encipher(ByteBuf input, ByteBuf out) throws ShortBufferException {
        int i = input.readableBytes();
        byte[] bytes = this.bufToByte(input);
        int outputSize = this.cipher.getOutputSize(i);
        if (this.heapOut.length < outputSize) {
            this.heapOut = new byte[outputSize];
        }

        out.writeBytes(this.heapOut, 0, this.cipher.update(bytes, 0, i, this.heapOut));
    }
}
