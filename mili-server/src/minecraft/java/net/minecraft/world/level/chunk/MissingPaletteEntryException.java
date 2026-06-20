package net.minecraft.world.level.chunk;

public class MissingPaletteEntryException extends RuntimeException {
    public MissingPaletteEntryException(int index) {
        super("Missing Palette entry for index " + index + ".");
    }
}
