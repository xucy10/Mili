package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import javax.crypto.Cipher;

public class CipherEncoder extends io.netty.handler.codec.MessageToMessageEncoder<ByteBuf> { // Paper - Use Velocity cipher; change superclass
    private final com.velocitypowered.natives.encryption.VelocityCipher cipher; // Paper - Use Velocity cipher

    public CipherEncoder(com.velocitypowered.natives.encryption.VelocityCipher cipher) { // Paper - Use Velocity cipher
        this.cipher = cipher; // Paper - Use Velocity cipher
    }

    // Paper start - Use Velocity cipher
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf message, java.util.List<Object> list) throws Exception {
        ByteBuf compatible = com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible(ctx.alloc(), this.cipher, message);
        try {
            this.cipher.process(compatible);
            list.add(compatible);
        } catch (Exception e) {
            compatible.release(); // compatible will never be used if we throw an exception
            throw e;
        }
        // Paper end - Use Velocity cipher
    }

    // Paper start - Use Velocity cipher
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        this.cipher.close();
    }
    // Paper end - Use Velocity cipher
}
