package net.minecraft.util;

import org.jspecify.annotations.Nullable;

public class ExceptionCollector<T extends Throwable> {
    private @Nullable T result;

    public void add(T exception) {
        if (this.result == null) {
            this.result = exception;
        } else {
            this.result.addSuppressed(exception);
        }
    }

    public void throwIfPresent() throws T {
        if (this.result != null) {
            throw this.result;
        }
    }
}
