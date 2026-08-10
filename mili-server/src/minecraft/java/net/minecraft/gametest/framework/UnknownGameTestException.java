package net.minecraft.gametest.framework;

import net.minecraft.network.chat.Component;

public class UnknownGameTestException extends GameTestException {
    private final Throwable reason;

    public UnknownGameTestException(Throwable message) {
        super(message.getMessage());
        this.reason = message;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("test.error.unknown", this.reason.getMessage());
    }
}
