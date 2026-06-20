package net.minecraft.util;

import java.io.IOException;
import java.io.InputStream;

public class FastBufferedInputStream extends InputStream {
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private final InputStream in;
    private final byte[] buffer;
    private int limit;
    private int position;

    public FastBufferedInputStream(InputStream in) {
        this(in, 8192);
    }

    public FastBufferedInputStream(InputStream in, int bufferSize) {
        this.in = in;
        this.buffer = new byte[bufferSize];
    }

    @Override
    public int read() throws IOException {
        if (this.position >= this.limit) {
            this.fill();
            if (this.position >= this.limit) {
                return -1;
            }
        }

        return Byte.toUnsignedInt(this.buffer[this.position++]);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int i = this.bytesInBuffer();
        if (i <= 0) {
            if (length >= this.buffer.length) {
                return this.in.read(buffer, offset, length);
            }

            this.fill();
            i = this.bytesInBuffer();
            if (i <= 0) {
                return -1;
            }
        }

        if (length > i) {
            length = i;
        }

        System.arraycopy(this.buffer, this.position, buffer, offset, length);
        this.position += length;
        return length;
    }

    @Override
    public long skip(long amount) throws IOException {
        if (amount <= 0L) {
            return 0L;
        } else {
            long l = this.bytesInBuffer();
            if (l <= 0L) {
                return this.in.skip(amount);
            } else {
                if (amount > l) {
                    amount = l;
                }

                this.position = (int)(this.position + amount);
                return amount;
            }
        }
    }

    @Override
    public int available() throws IOException {
        return this.bytesInBuffer() + this.in.available();
    }

    @Override
    public void close() throws IOException {
        this.in.close();
    }

    private int bytesInBuffer() {
        return this.limit - this.position;
    }

    private void fill() throws IOException {
        this.limit = 0;
        this.position = 0;
        int i = this.in.read(this.buffer, 0, this.buffer.length);
        if (i > 0) {
            this.limit = i;
        }
    }
}
