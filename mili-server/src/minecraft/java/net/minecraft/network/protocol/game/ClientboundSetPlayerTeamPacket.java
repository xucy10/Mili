package net.minecraft.network.protocol.game;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

public class ClientboundSetPlayerTeamPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetPlayerTeamPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetPlayerTeamPacket::write, ClientboundSetPlayerTeamPacket::new
    );
    private static final int METHOD_ADD = 0;
    private static final int METHOD_REMOVE = 1;
    private static final int METHOD_CHANGE = 2;
    private static final int METHOD_JOIN = 3;
    private static final int METHOD_LEAVE = 4;
    private static final int MAX_VISIBILITY_LENGTH = 40;
    private static final int MAX_COLLISION_LENGTH = 40;
    private final int method;
    private final String name;
    private final Collection<String> players;
    private final Optional<ClientboundSetPlayerTeamPacket.Parameters> parameters;

    private ClientboundSetPlayerTeamPacket(String name, int method, Optional<ClientboundSetPlayerTeamPacket.Parameters> parameters, Collection<String> players) {
        this.name = name;
        this.method = method;
        this.parameters = parameters;
        this.players = ImmutableList.copyOf(players);
    }

    public static ClientboundSetPlayerTeamPacket createAddOrModifyPacket(PlayerTeam team, boolean useAdd) {
        return new ClientboundSetPlayerTeamPacket(
            team.getName(),
            useAdd ? METHOD_ADD : METHOD_CHANGE,
            Optional.of(new ClientboundSetPlayerTeamPacket.Parameters(team)),
            (Collection<String>)(useAdd ? team.getPlayers() : ImmutableList.of())
        );
    }

    public static ClientboundSetPlayerTeamPacket createRemovePacket(PlayerTeam team) {
        return new ClientboundSetPlayerTeamPacket(team.getName(), METHOD_REMOVE, Optional.empty(), ImmutableList.of());
    }

    public static ClientboundSetPlayerTeamPacket createPlayerPacket(PlayerTeam team, String playerName, ClientboundSetPlayerTeamPacket.Action action) {
        return new ClientboundSetPlayerTeamPacket(
            team.getName(), action == ClientboundSetPlayerTeamPacket.Action.ADD ? METHOD_JOIN : METHOD_LEAVE, Optional.empty(), ImmutableList.of(playerName)
        );
    }

    // Paper start - Multiple Entries with Scoreboards
    public static ClientboundSetPlayerTeamPacket createMultiplePlayerPacket(PlayerTeam team, Collection<String> players, ClientboundSetPlayerTeamPacket.Action action) {
        return new ClientboundSetPlayerTeamPacket(team.getName(), action == ClientboundSetPlayerTeamPacket.Action.ADD ? METHOD_JOIN : METHOD_LEAVE, Optional.empty(), players);
    }
    // Paper end - Multiple Entries with Scoreboards

    private ClientboundSetPlayerTeamPacket(RegistryFriendlyByteBuf buffer) {
        this.name = buffer.readUtf();
        this.method = buffer.readByte();
        if (shouldHaveParameters(this.method)) {
            this.parameters = Optional.of(new ClientboundSetPlayerTeamPacket.Parameters(buffer));
        } else {
            this.parameters = Optional.empty();
        }

        if (shouldHavePlayerList(this.method)) {
            this.players = buffer.readList(FriendlyByteBuf::readUtf);
        } else {
            this.players = ImmutableList.of();
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.name);
        buffer.writeByte(this.method);
        if (shouldHaveParameters(this.method)) {
            this.parameters.orElseThrow(() -> new IllegalStateException("Parameters not present, but method is" + this.method)).write(buffer);
        }

        if (shouldHavePlayerList(this.method)) {
            buffer.writeCollection(this.players, FriendlyByteBuf::writeUtf);
        }
    }

    private static boolean shouldHavePlayerList(int method) {
        return method == 0 || method == METHOD_JOIN || method == METHOD_LEAVE;
    }

    private static boolean shouldHaveParameters(int method) {
        return method == 0 || method == METHOD_CHANGE;
    }

    public ClientboundSetPlayerTeamPacket.@Nullable Action getPlayerAction() {
        return switch (this.method) {
            case 0, 3 -> ClientboundSetPlayerTeamPacket.Action.ADD;
            default -> null;
            case 4 -> ClientboundSetPlayerTeamPacket.Action.REMOVE;
        };
    }

    public ClientboundSetPlayerTeamPacket.@Nullable Action getTeamAction() {
        return switch (this.method) {
            case 0 -> ClientboundSetPlayerTeamPacket.Action.ADD;
            case 1 -> ClientboundSetPlayerTeamPacket.Action.REMOVE;
            default -> null;
        };
    }

    @Override
    public PacketType<ClientboundSetPlayerTeamPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_PLAYER_TEAM;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetPlayerTeamPacket(this);
    }

    public String getName() {
        return this.name;
    }

    public Collection<String> getPlayers() {
        return this.players;
    }

    public Optional<ClientboundSetPlayerTeamPacket.Parameters> getParameters() {
        return this.parameters;
    }

    public static enum Action {
        ADD,
        REMOVE;
    }

    public static class Parameters {
        private final Component displayName;
        private final Component playerPrefix;
        private final Component playerSuffix;
        private final Team.Visibility nametagVisibility;
        private final Team.CollisionRule collisionRule;
        private final ChatFormatting color;
        private final int options;

        public Parameters(PlayerTeam team) {
            this.displayName = team.getDisplayName();
            this.options = team.packOptions();
            this.nametagVisibility = team.getNameTagVisibility();
            this.collisionRule = team.getCollisionRule();
            this.color = team.getColor();
            this.playerPrefix = team.getPlayerPrefix();
            this.playerSuffix = team.getPlayerSuffix();
        }

        public Parameters(RegistryFriendlyByteBuf buffer) {
            this.displayName = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
            this.options = buffer.readByte();
            this.nametagVisibility = Team.Visibility.STREAM_CODEC.decode(buffer);
            this.collisionRule = Team.CollisionRule.STREAM_CODEC.decode(buffer);
            this.color = buffer.readEnum(ChatFormatting.class);
            this.playerPrefix = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
            this.playerSuffix = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
        }

        public Component getDisplayName() {
            return this.displayName;
        }

        public int getOptions() {
            return this.options;
        }

        public ChatFormatting getColor() {
            return this.color;
        }

        public Team.Visibility getNametagVisibility() {
            return this.nametagVisibility;
        }

        public Team.CollisionRule getCollisionRule() {
            return this.collisionRule;
        }

        public Component getPlayerPrefix() {
            return this.playerPrefix;
        }

        public Component getPlayerSuffix() {
            return this.playerSuffix;
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.displayName);
            buffer.writeByte(this.options);
            Team.Visibility.STREAM_CODEC.encode(buffer, this.nametagVisibility);
            Team.CollisionRule.STREAM_CODEC.encode(buffer, !io.papermc.paper.configuration.GlobalConfiguration.get().collisions.enablePlayerCollisions ? PlayerTeam.CollisionRule.NEVER : this.collisionRule); // Paper - Configurable player collision
            buffer.writeEnum(this.color);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.playerPrefix);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.playerSuffix);
        }
    }
}
