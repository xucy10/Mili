package net.minecraft.world.level.storage;

import net.minecraft.network.chat.Component;

public class LevelStorageException extends RuntimeException {
    private final Component messageComponent;

    public LevelStorageException(Component messageComponent) {
        super(messageComponent.getString());
        this.messageComponent = messageComponent;
    }

    public Component getMessageComponent() {
        return this.messageComponent;
    }
}
