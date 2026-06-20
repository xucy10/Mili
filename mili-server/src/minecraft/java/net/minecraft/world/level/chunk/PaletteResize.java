package net.minecraft.world.level.chunk;

public interface PaletteResize<T> {
    int onResize(int bits, T addedValue);

    static <T> PaletteResize<T> noResizeExpected() {
        return (bits, addedValue) -> {
            throw new IllegalArgumentException("Unexpected palette resize, bits = " + bits + ", added value = " + addedValue);
        };
    }
}
