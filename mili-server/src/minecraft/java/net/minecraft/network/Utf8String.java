package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.nio.charset.StandardCharsets;

public class Utf8String {
    public static String read(ByteBuf buffer, int maxLength) {
        int i = ByteBufUtil.utf8MaxBytes(maxLength);
        int i1 = VarInt.read(buffer);
        if (i1 > i) {
            throw new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + i1 + " > " + i + ")");
        } else if (i1 < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        } else {
            int i2 = buffer.readableBytes();
            if (i1 > i2) {
                throw new DecoderException("Not enough bytes in buffer, expected " + i1 + ", but got " + i2);
            } else {
                String string = buffer.toString(buffer.readerIndex(), i1, StandardCharsets.UTF_8);
                buffer.readerIndex(buffer.readerIndex() + i1);
                if (string.length() > maxLength) {
                    throw new DecoderException("The received string length is longer than maximum allowed (" + string.length() + " > " + maxLength + ")");
                } else {
                    return string;
                }
            }
        }
    }

    public static void write(ByteBuf buffer, CharSequence string, int maxLength) {
        // Luminol start - Krypton optimizations
        if (true) {
            if (string.length() > maxLength) {
                throw new EncoderException("String too big (was " + string.length() + " characters, max " + maxLength + ")");
            }
            int utf8Bytes = ByteBufUtil.utf8Bytes(string);
            int maxBytesPermitted = ByteBufUtil.utf8MaxBytes(maxLength);
            if (utf8Bytes > maxBytesPermitted) {
                throw new EncoderException("String too big (was " + utf8Bytes + " bytes encoded, max " + maxBytesPermitted + ")");
            } else {
                VarInt.write(buffer, utf8Bytes);
                buffer.writeCharSequence(string, StandardCharsets.UTF_8);
            }
            return;
        }
        // Luminol end
        if (string.length() > maxLength) {
            throw new EncoderException("String too big (was " + string.length() + " characters, max " + maxLength + ")");
        } else {
            int i = ByteBufUtil.utf8MaxBytes(string);
            ByteBuf byteBuf = buffer.alloc().buffer(i);

            try {
                int i1 = ByteBufUtil.writeUtf8(byteBuf, string);
                int i2 = ByteBufUtil.utf8MaxBytes(maxLength);
                if (i1 > i2) {
                    throw new EncoderException("String too big (was " + i1 + " bytes encoded, max " + i2 + ")");
                }

                VarInt.write(buffer, i1);
                buffer.writeBytes(byteBuf);
            } finally {
                byteBuf.release();
            }
        }
    }
}
