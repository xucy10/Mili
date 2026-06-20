package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.BossEvent;

public class ClientboundBossEventPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundBossEventPacket> STREAM_CODEC = Packet.codec(
        ClientboundBossEventPacket::write, ClientboundBossEventPacket::new
    );
    private static final int FLAG_DARKEN = 1;
    private static final int FLAG_MUSIC = 2;
    private static final int FLAG_FOG = 4;
    private final UUID id;
    private final ClientboundBossEventPacket.Operation operation;
    static final ClientboundBossEventPacket.Operation REMOVE_OPERATION = new ClientboundBossEventPacket.Operation() {
        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.REMOVE;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.remove(id);
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer) {
        }
    };

    private ClientboundBossEventPacket(UUID id, ClientboundBossEventPacket.Operation operation) {
        this.id = id;
        this.operation = operation;
    }

    private ClientboundBossEventPacket(RegistryFriendlyByteBuf buffer) {
        this.id = buffer.readUUID();
        ClientboundBossEventPacket.OperationType operationType = buffer.readEnum(ClientboundBossEventPacket.OperationType.class);
        this.operation = operationType.reader.decode(buffer);
    }

    public static ClientboundBossEventPacket createAddPacket(BossEvent event) {
        return new ClientboundBossEventPacket(event.getId(), new ClientboundBossEventPacket.AddOperation(event));
    }

    public static ClientboundBossEventPacket createRemovePacket(UUID id) {
        return new ClientboundBossEventPacket(id, REMOVE_OPERATION);
    }

    public static ClientboundBossEventPacket createUpdateProgressPacket(BossEvent event) {
        return new ClientboundBossEventPacket(event.getId(), new ClientboundBossEventPacket.UpdateProgressOperation(event.getProgress()));
    }

    public static ClientboundBossEventPacket createUpdateNamePacket(BossEvent event) {
        return new ClientboundBossEventPacket(event.getId(), new ClientboundBossEventPacket.UpdateNameOperation(event.getName()));
    }

    public static ClientboundBossEventPacket createUpdateStylePacket(BossEvent event) {
        return new ClientboundBossEventPacket(event.getId(), new ClientboundBossEventPacket.UpdateStyleOperation(event.getColor(), event.getOverlay()));
    }

    public static ClientboundBossEventPacket createUpdatePropertiesPacket(BossEvent event) {
        return new ClientboundBossEventPacket(
            event.getId(),
            new ClientboundBossEventPacket.UpdatePropertiesOperation(event.shouldDarkenScreen(), event.shouldPlayBossMusic(), event.shouldCreateWorldFog())
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.id);
        buffer.writeEnum(this.operation.getType());
        this.operation.write(buffer);
    }

    static int encodeProperties(boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
        int i = 0;
        if (darkenScreen) {
            i |= 1;
        }

        if (playMusic) {
            i |= 2;
        }

        if (createWorldFog) {
            i |= 4;
        }

        return i;
    }

    @Override
    public PacketType<ClientboundBossEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_BOSS_EVENT;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleBossUpdate(this);
    }

    public void dispatch(ClientboundBossEventPacket.Handler handler) {
        this.operation.dispatch(this.id, handler);
    }

    static class AddOperation implements ClientboundBossEventPacket.Operation {
        private final Component name;
        private final float progress;
        private final BossEvent.BossBarColor color;
        private final BossEvent.BossBarOverlay overlay;
        private final boolean darkenScreen;
        private final boolean playMusic;
        private final boolean createWorldFog;

        AddOperation(BossEvent event) {
            this.name = event.getName();
            this.progress = event.getProgress();
            this.color = event.getColor();
            this.overlay = event.getOverlay();
            this.darkenScreen = event.shouldDarkenScreen();
            this.playMusic = event.shouldPlayBossMusic();
            this.createWorldFog = event.shouldCreateWorldFog();
        }

        private AddOperation(RegistryFriendlyByteBuf buffer) {
            this.name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
            this.progress = buffer.readFloat();
            this.color = buffer.readEnum(BossEvent.BossBarColor.class);
            this.overlay = buffer.readEnum(BossEvent.BossBarOverlay.class);
            int unsignedByte = buffer.readUnsignedByte();
            this.darkenScreen = (unsignedByte & 1) > 0;
            this.playMusic = (unsignedByte & 2) > 0;
            this.createWorldFog = (unsignedByte & 4) > 0;
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.ADD;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.add(id, this.name, this.progress, this.color, this.overlay, this.darkenScreen, this.playMusic, this.createWorldFog);
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.name);
            buffer.writeFloat(this.progress);
            buffer.writeEnum(this.color);
            buffer.writeEnum(this.overlay);
            buffer.writeByte(ClientboundBossEventPacket.encodeProperties(this.darkenScreen, this.playMusic, this.createWorldFog));
        }
    }

    public interface Handler {
        default void add(
            UUID id,
            Component name,
            float progress,
            BossEvent.BossBarColor color,
            BossEvent.BossBarOverlay overlay,
            boolean darkenScreen,
            boolean playMusic,
            boolean createWorldFog
        ) {
        }

        default void remove(UUID id) {
        }

        default void updateProgress(UUID id, float progress) {
        }

        default void updateName(UUID id, Component name) {
        }

        default void updateStyle(UUID id, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        }

        default void updateProperties(UUID id, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
        }
    }

    interface Operation {
        ClientboundBossEventPacket.OperationType getType();

        void dispatch(UUID id, ClientboundBossEventPacket.Handler handler);

        void write(RegistryFriendlyByteBuf buffer);
    }

    static enum OperationType {
        ADD(ClientboundBossEventPacket.AddOperation::new),
        REMOVE(buffer -> ClientboundBossEventPacket.REMOVE_OPERATION),
        UPDATE_PROGRESS(ClientboundBossEventPacket.UpdateProgressOperation::new),
        UPDATE_NAME(ClientboundBossEventPacket.UpdateNameOperation::new),
        UPDATE_STYLE(ClientboundBossEventPacket.UpdateStyleOperation::new),
        UPDATE_PROPERTIES(ClientboundBossEventPacket.UpdatePropertiesOperation::new);

        final StreamDecoder<RegistryFriendlyByteBuf, ClientboundBossEventPacket.Operation> reader;

        private OperationType(final StreamDecoder<RegistryFriendlyByteBuf, ClientboundBossEventPacket.Operation> reader) {
            this.reader = reader;
        }
    }

    record UpdateNameOperation(Component name) implements ClientboundBossEventPacket.Operation {
        private UpdateNameOperation(RegistryFriendlyByteBuf name) {
            this(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(name));
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.UPDATE_NAME;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.updateName(id, this.name);
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.name);
        }
    }

    record UpdateProgressOperation(float progress) implements ClientboundBossEventPacket.Operation {
        private UpdateProgressOperation(RegistryFriendlyByteBuf progress) {
            this(progress.readFloat());
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.UPDATE_PROGRESS;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.updateProgress(id, this.progress);
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeFloat(this.progress);
        }
    }

    static class UpdatePropertiesOperation implements ClientboundBossEventPacket.Operation {
        private final boolean darkenScreen;
        private final boolean playMusic;
        private final boolean createWorldFog;

        UpdatePropertiesOperation(boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
            this.darkenScreen = darkenScreen;
            this.playMusic = playMusic;
            this.createWorldFog = createWorldFog;
        }

        private UpdatePropertiesOperation(RegistryFriendlyByteBuf buffer) {
            int unsignedByte = buffer.readUnsignedByte();
            this.darkenScreen = (unsignedByte & 1) > 0;
            this.playMusic = (unsignedByte & 2) > 0;
            this.createWorldFog = (unsignedByte & 4) > 0;
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.UPDATE_PROPERTIES;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.updateProperties(id, this.darkenScreen, this.playMusic, this.createWorldFog);
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeByte(ClientboundBossEventPacket.encodeProperties(this.darkenScreen, this.playMusic, this.createWorldFog));
        }
    }

    static class UpdateStyleOperation implements ClientboundBossEventPacket.Operation {
        private final BossEvent.BossBarColor color;
        private final BossEvent.BossBarOverlay overlay;

        UpdateStyleOperation(BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
            this.color = color;
            this.overlay = overlay;
        }

        private UpdateStyleOperation(RegistryFriendlyByteBuf buffer) {
            this.color = buffer.readEnum(BossEvent.BossBarColor.class);
            this.overlay = buffer.readEnum(BossEvent.BossBarOverlay.class);
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.UPDATE_STYLE;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.updateStyle(id, this.color, this.overlay);
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(this.color);
            buffer.writeEnum(this.overlay);
        }
    }
}
