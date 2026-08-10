package net.minecraft.network.chat.contents;

import java.util.Locale;

public class TranslatableFormatException extends IllegalArgumentException {
    public TranslatableFormatException(TranslatableContents contents, String error) {
        super(String.format(Locale.ROOT, "Error parsing: %s: %s", contents, error));
    }

    public TranslatableFormatException(TranslatableContents component, int invalidIndex) {
        super(String.format(Locale.ROOT, "Invalid index %d requested for %s", invalidIndex, component));
    }

    public TranslatableFormatException(TranslatableContents contents, Throwable cause) {
        super(String.format(Locale.ROOT, "Error while parsing: %s", contents), cause);
    }
}
