package net.minecraft.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;

public record PngInfo(int width, int height) {
    private static final HexFormat FORMAT = HexFormat.of().withUpperCase().withPrefix("0x");
    private static final long PNG_HEADER = -8552249625308161526L;
    private static final int IHDR_TYPE = 1229472850;
    private static final int IHDR_SIZE = 13;

    public static PngInfo fromStream(InputStream stream) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(stream);
        long _long = dataInputStream.readLong();
        if (_long != -8552249625308161526L) {
            throw new IOException("Bad PNG Signature: " + FORMAT.toHexDigits(_long));
        } else {
            int _int = dataInputStream.readInt();
            if (_int != 13) {
                throw new IOException("Bad length for IHDR chunk: " + _int);
            } else {
                int _int1 = dataInputStream.readInt();
                if (_int1 != 1229472850) {
                    throw new IOException("Bad type for IHDR chunk: " + FORMAT.toHexDigits(_int1));
                } else {
                    int _int2 = dataInputStream.readInt();
                    int _int3 = dataInputStream.readInt();
                    return new PngInfo(_int2, _int3);
                }
            }
        }
    }

    public static PngInfo fromBytes(byte[] bytes) throws IOException {
        return fromStream(new ByteArrayInputStream(bytes));
    }

    public static void validateHeader(ByteBuffer buffer) throws IOException {
        ByteOrder byteOrder = buffer.order();
        buffer.order(ByteOrder.BIG_ENDIAN);
        if (buffer.limit() < 16) {
            throw new IOException("PNG header missing");
        } else if (buffer.getLong(0) != -8552249625308161526L) {
            throw new IOException("Bad PNG Signature");
        } else if (buffer.getInt(8) != 13) {
            throw new IOException("Bad length for IHDR chunk!");
        } else if (buffer.getInt(12) != 1229472850) {
            throw new IOException("Bad type for IHDR chunk!");
        } else {
            buffer.order(byteOrder);
        }
    }
}
