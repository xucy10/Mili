package net.minecraft.server.rcon;

import java.nio.charset.StandardCharsets;

public class PktUtils {
    public static final int MAX_PACKET_SIZE = 1460;
    public static final char[] HEX_CHAR = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String stringFromByteArray(byte[] input, int offset, int length) {
        int i = length - 1;
        int i1 = offset > i ? i : offset;

        while (0 != input[i1] && i1 < i) {
            i1++;
        }

        return new String(input, offset, i1 - offset, StandardCharsets.UTF_8);
    }

    public static int intFromByteArray(byte[] input, int offset) {
        return intFromByteArray(input, offset, input.length);
    }

    public static int intFromByteArray(byte[] input, int offset, int length) {
        return 0 > length - offset - 4
            ? 0
            : input[offset + 3] << 24 | (input[offset + 2] & 0xFF) << 16 | (input[offset + 1] & 0xFF) << 8 | input[offset] & 0xFF;
    }

    public static int intFromNetworkByteArray(byte[] input, int offset, int length) {
        return 0 > length - offset - 4
            ? 0
            : input[offset] << 24 | (input[offset + 1] & 0xFF) << 16 | (input[offset + 2] & 0xFF) << 8 | input[offset + 3] & 0xFF;
    }

    public static String toHexString(byte input) {
        return "" + HEX_CHAR[(input & 240) >>> 4] + HEX_CHAR[input & 15];
    }
}
