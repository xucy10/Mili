package net.minecraft.world.level.chunk;

import java.util.Arrays;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import org.jspecify.annotations.Nullable;

public class DataLayer {
    public static final int LAYER_COUNT = 16;
    public static final int LAYER_SIZE = 128;
    public static final int SIZE = 2048;
    private static final int NIBBLE_SIZE = 4;
    protected byte @Nullable [] data;
    private int defaultValue;

    public DataLayer() {
        this(0);
    }

    public DataLayer(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    public DataLayer(byte[] data) {
        this.data = data;
        this.defaultValue = 0;
        if (data.length != 2048) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("DataLayer should be 2048 bytes not: " + data.length));
        }
    }

    public int get(int x, int y, int z) {
        return this.get(getIndex(x, y, z));
    }

    public void set(int x, int y, int z, int value) {
        this.set(getIndex(x, y, z), value);
    }

    private static int getIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    private int get(int index) {
        if (this.data == null) {
            return this.defaultValue;
        } else {
            int byteIndex = getByteIndex(index);
            int nibbleIndex = getNibbleIndex(index);
            return this.data[byteIndex] >> 4 * nibbleIndex & 15;
        }
    }

    private void set(int index, int value) {
        byte[] data = this.getData();
        int byteIndex = getByteIndex(index);
        int nibbleIndex = getNibbleIndex(index);
        int i = ~(15 << 4 * nibbleIndex);
        int i1 = (value & 15) << 4 * nibbleIndex;
        data[byteIndex] = (byte)(data[byteIndex] & i | i1);
    }

    private static int getNibbleIndex(int index) {
        return index & 1;
    }

    private static int getByteIndex(int index) {
        return index >> 1;
    }

    public void fill(int defaultValue) {
        this.defaultValue = defaultValue;
        this.data = null;
    }

    private static byte packFilled(int value) {
        byte b = (byte)value;

        for (int i = 4; i < 8; i += 4) {
            b = (byte)(b | value << i);
        }

        return b;
    }

    public byte[] getData() {
        if (this.data == null) {
            this.data = new byte[2048];
            if (this.defaultValue != 0) {
                Arrays.fill(this.data, packFilled(this.defaultValue));
            }
        }

        return this.data;
    }

    public DataLayer copy() {
        return this.data == null ? new DataLayer(this.defaultValue) : new DataLayer((byte[])this.data.clone());
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < 4096; i++) {
            stringBuilder.append(Integer.toHexString(this.get(i)));
            if ((i & 15) == 15) {
                stringBuilder.append("\n");
            }

            if ((i & 0xFF) == 255) {
                stringBuilder.append("\n");
            }
        }

        return stringBuilder.toString();
    }

    @VisibleForDebug
    public String layerToString(int layer) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < 256; i++) {
            stringBuilder.append(Integer.toHexString(this.get(i)));
            if ((i & 15) == 15) {
                stringBuilder.append("\n");
            }
        }

        return stringBuilder.toString();
    }

    public boolean isDefinitelyHomogenous() {
        return this.data == null;
    }

    public boolean isDefinitelyFilledWith(int value) {
        return this.data == null && this.defaultValue == value;
    }

    public boolean isEmpty() {
        return this.data == null && this.defaultValue == 0;
    }
}
