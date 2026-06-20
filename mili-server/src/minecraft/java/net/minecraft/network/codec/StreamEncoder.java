package net.minecraft.network.codec;

@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O buffer, T value);
}
