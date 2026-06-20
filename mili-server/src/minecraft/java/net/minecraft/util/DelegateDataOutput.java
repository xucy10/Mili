package net.minecraft.util;

import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.SuppressForbidden;

public class DelegateDataOutput implements DataOutput {
    private final DataOutput parent;

    public DelegateDataOutput(DataOutput parent) {
        this.parent = parent;
    }

    @Override
    public void write(int value) throws IOException {
        this.parent.write(value);
    }

    @Override
    public void write(byte[] data) throws IOException {
        this.parent.write(data);
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        this.parent.write(data, offset, length);
    }

    @Override
    public void writeBoolean(boolean value) throws IOException {
        this.parent.writeBoolean(value);
    }

    @Override
    public void writeByte(int value) throws IOException {
        this.parent.writeByte(value);
    }

    @Override
    public void writeShort(int value) throws IOException {
        this.parent.writeShort(value);
    }

    @Override
    public void writeChar(int value) throws IOException {
        this.parent.writeChar(value);
    }

    @Override
    public void writeInt(int value) throws IOException {
        this.parent.writeInt(value);
    }

    @Override
    public void writeLong(long value) throws IOException {
        this.parent.writeLong(value);
    }

    @Override
    public void writeFloat(float value) throws IOException {
        this.parent.writeFloat(value);
    }

    @Override
    public void writeDouble(double value) throws IOException {
        this.parent.writeDouble(value);
    }

    @SuppressForbidden(reason = "Delegation is not use")
    @Override
    public void writeBytes(String value) throws IOException {
        this.parent.writeBytes(value);
    }

    @Override
    public void writeChars(String value) throws IOException {
        this.parent.writeChars(value);
    }

    @Override
    public void writeUTF(String value) throws IOException {
        this.parent.writeUTF(value);
    }
}
