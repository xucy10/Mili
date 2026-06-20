package net.minecraft.server.rcon;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class NetworkDataOutputStream {
    private final ByteArrayOutputStream outputStream;
    private final DataOutputStream dataOutputStream;

    public NetworkDataOutputStream(int capacity) {
        this.outputStream = new ByteArrayOutputStream(capacity);
        this.dataOutputStream = new DataOutputStream(this.outputStream);
    }

    public void writeBytes(byte[] data) throws IOException {
        this.dataOutputStream.write(data, 0, data.length);
    }

    public void writeString(String data) throws IOException {
        this.dataOutputStream.write(data.getBytes(StandardCharsets.UTF_8));
        this.dataOutputStream.write(0);
    }

    public void write(int data) throws IOException {
        this.dataOutputStream.write(data);
    }

    public void writeShort(short data) throws IOException {
        this.dataOutputStream.writeShort(Short.reverseBytes(data));
    }

    public void writeInt(int data) throws IOException {
        this.dataOutputStream.writeInt(Integer.reverseBytes(data));
    }

    public void writeFloat(float data) throws IOException {
        this.dataOutputStream.writeInt(Integer.reverseBytes(Float.floatToIntBits(data)));
    }

    public byte[] toByteArray() {
        return this.outputStream.toByteArray();
    }

    public void reset() {
        this.outputStream.reset();
    }
}
