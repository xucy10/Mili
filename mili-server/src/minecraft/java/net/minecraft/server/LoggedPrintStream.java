package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class LoggedPrintStream extends PrintStream {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final String name;

    public LoggedPrintStream(String name, OutputStream out) {
        super(out, false, StandardCharsets.UTF_8);
        this.name = name;
    }

    @Override
    public void println(@Nullable String message) {
        this.logLine(message);
    }

    @Override
    public void println(@Nullable Object object) {
        this.logLine(String.valueOf(object));
    }

    protected void logLine(@Nullable String string) {
        LOGGER.info("[{}]: {}", this.name, string);
    }
}
