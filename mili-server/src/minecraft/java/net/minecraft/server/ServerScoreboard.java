package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardSaveData;
import org.jspecify.annotations.Nullable;

public class ServerScoreboard extends Scoreboard {
    private final MinecraftServer server;
    private final Set<Objective> trackedObjectives = Sets.newHashSet();
    private boolean dirty;

    public ServerScoreboard(MinecraftServer server) {
        this.server = server;
    }

    public void load(ScoreboardSaveData.Packed packed) {
        packed.objectives().forEach(packed1 -> this.loadObjective(packed1));
        packed.scores().forEach(packedScore -> this.loadPlayerScore(packedScore));
        packed.displaySlots().forEach((displaySlot, string) -> {
            Objective objective = this.getObjective(string);
            this.setDisplayObjective(displaySlot, objective);
        });
        packed.teams().forEach(packed1 -> this.loadPlayerTeam(packed1));
    }

    private ScoreboardSaveData.Packed store() {
        return new ScoreboardSaveData.Packed(this.packObjectives(), this.packPlayerScores(), this.packDisplaySlots(), this.packPlayerTeams());
    }

    @Override
    protected void onScoreChanged(ScoreHolder scoreHolder, Objective objective, Score score) {
        super.onScoreChanged(scoreHolder, objective, score);
        if (this.trackedObjectives.contains(objective)) {
            this.broadcastAll( // CraftBukkit
                    new ClientboundSetScorePacket(
                        scoreHolder.getScoreboardName(),
                        objective.getName(),
                        score.value(),
                        Optional.ofNullable(score.display()),
                        Optional.ofNullable(score.numberFormat())
                    )
                );
        }

        this.setDirty();
    }

    @Override
    protected void onScoreLockChanged(ScoreHolder scoreHolder, Objective objective) {
        super.onScoreLockChanged(scoreHolder, objective);
        this.setDirty();
    }

    @Override
    public void onPlayerRemoved(ScoreHolder scoreHolder) {
        super.onPlayerRemoved(scoreHolder);
        this.broadcastAll(new ClientboundResetScorePacket(scoreHolder.getScoreboardName(), null)); // CraftBukkit
        this.setDirty();
    }

    @Override
    public void onPlayerScoreRemoved(ScoreHolder scoreHolder, Objective objective) {
        super.onPlayerScoreRemoved(scoreHolder, objective);
        if (this.trackedObjectives.contains(objective)) {
            this.broadcastAll(new ClientboundResetScorePacket(scoreHolder.getScoreboardName(), objective.getName())); // CraftBukkit
        }

        this.setDirty();
    }

    @Override
    public void setDisplayObjective(DisplaySlot slot, @Nullable Objective objective) {
        Objective displayObjective = this.getDisplayObjective(slot);
        super.setDisplayObjective(slot, objective);
        if (displayObjective != objective && displayObjective != null) {
            if (this.getObjectiveDisplaySlotCount(displayObjective) > 0) {
                this.broadcastAll(new ClientboundSetDisplayObjectivePacket(slot, objective)); // CraftBukkit
            } else {
                this.stopTrackingObjective(displayObjective);
            }
        }

        if (objective != null) {
            if (this.trackedObjectives.contains(objective)) {
                this.broadcastAll(new ClientboundSetDisplayObjectivePacket(slot, objective)); // CraftBukkit
            } else {
                this.startTrackingObjective(objective);
            }
        }

        this.setDirty();
    }

    @Override
    public boolean addPlayerToTeam(String playerName, PlayerTeam team) {
        if (super.addPlayerToTeam(playerName, team)) {
            this.broadcastAll(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, playerName, ClientboundSetPlayerTeamPacket.Action.ADD)); // CraftBukkit
            this.updatePlayerWaypoint(playerName);
            this.setDirty();
            return true;
        } else {
            return false;
        }
    }

    // Paper start - Multiple Entries with Scoreboards
    public boolean addPlayersToTeam(java.util.Collection<String> players, PlayerTeam team) {
        boolean anyAdded = false;
        for (String playerName : players) {
            if (super.addPlayerToTeam(playerName, team)) {
                anyAdded = true;
            }
        }

        if (anyAdded) {
            this.broadcastAll(ClientboundSetPlayerTeamPacket.createMultiplePlayerPacket(team, players, ClientboundSetPlayerTeamPacket.Action.ADD));
            this.setDirty();
            return true;
        } else {
            return false;
        }
    }
    // Paper end - Multiple Entries with Scoreboards

    @Override
    public void removePlayerFromTeam(String playerName, PlayerTeam team) {
        super.removePlayerFromTeam(playerName, team);
        this.broadcastAll(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, playerName, ClientboundSetPlayerTeamPacket.Action.REMOVE)); // CraftBukkit
        this.updatePlayerWaypoint(playerName);
        this.setDirty();
    }

    // Paper start - Multiple Entries with Scoreboards
    public void removePlayersFromTeam(java.util.Collection<String> players, PlayerTeam team) {
        for (String playerName : players) {
            super.removePlayerFromTeam(playerName, team);
        }

        this.broadcastAll(ClientboundSetPlayerTeamPacket.createMultiplePlayerPacket(team, players, ClientboundSetPlayerTeamPacket.Action.REMOVE));
        this.setDirty();
    }
    // Paper end - Multiple Entries with Scoreboards

    @Override
    public void onObjectiveAdded(Objective objective) {
        super.onObjectiveAdded(objective);
        this.setDirty();
    }

    @Override
    public void onObjectiveChanged(Objective objective) {
        super.onObjectiveChanged(objective);
        if (this.trackedObjectives.contains(objective)) {
            this.broadcastAll(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_CHANGE)); // CraftBukkit
        }

        this.setDirty();
    }

    @Override
    public void onObjectiveRemoved(Objective objective) {
        super.onObjectiveRemoved(objective);
        if (this.trackedObjectives.contains(objective)) {
            this.stopTrackingObjective(objective);
        }

        this.setDirty();
    }

    @Override
    public void onTeamAdded(PlayerTeam team) {
        super.onTeamAdded(team);
        this.broadcastAll(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true)); // CraftBukkit
        this.setDirty();
    }

    @Override
    public void onTeamChanged(PlayerTeam team) {
        super.onTeamChanged(team);
        this.broadcastAll(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false)); // CraftBukkit
        this.updateTeamWaypoints(team);
        this.setDirty();
    }

    @Override
    public void onTeamRemoved(PlayerTeam team) {
        super.onTeamRemoved(team);
        this.broadcastAll(ClientboundSetPlayerTeamPacket.createRemovePacket(team)); // CraftBukkit
        this.updateTeamWaypoints(team);
        this.setDirty();
    }

    protected void setDirty() {
        this.dirty = true;
    }

    public void storeToSaveDataIfDirty(ScoreboardSaveData saveData) {
        if (this.dirty) {
            this.dirty = false;
            saveData.setData(this.store());
        }
    }

    public List<Packet<?>> getStartTrackingPackets(Objective objective) {
        List<Packet<?>> list = Lists.newArrayList();
        list.add(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displaySlot) == objective) {
                list.add(new ClientboundSetDisplayObjectivePacket(displaySlot, objective));
            }
        }

        for (PlayerScoreEntry playerScoreEntry : this.listPlayerScores(objective)) {
            list.add(
                new ClientboundSetScorePacket(
                    playerScoreEntry.owner(),
                    objective.getName(),
                    playerScoreEntry.value(),
                    Optional.ofNullable(playerScoreEntry.display()),
                    Optional.ofNullable(playerScoreEntry.numberFormatOverride())
                )
            );
        }

        return list;
    }

    public void startTrackingObjective(Objective objective) {
        List<Packet<?>> startTrackingPackets = this.getStartTrackingPackets(objective);

        for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
            if (serverPlayer.getBukkitEntity().getScoreboard().getHandle() != this) continue; // CraftBukkit - Only players on this board
            for (Packet<?> packet : startTrackingPackets) {
                serverPlayer.connection.send(packet);
            }
        }

        this.trackedObjectives.add(objective);
    }

    public List<Packet<?>> getStopTrackingPackets(Objective objective) {
        List<Packet<?>> list = Lists.newArrayList();
        list.add(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE));

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displaySlot) == objective) {
                list.add(new ClientboundSetDisplayObjectivePacket(displaySlot, objective));
            }
        }

        return list;
    }

    public void stopTrackingObjective(Objective objective) {
        List<Packet<?>> stopTrackingPackets = this.getStopTrackingPackets(objective);

        for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
            if (serverPlayer.getBukkitEntity().getScoreboard().getHandle() != this) continue; // CraftBukkit - Only players on this board
            for (Packet<?> packet : stopTrackingPackets) {
                serverPlayer.connection.send(packet);
            }
        }

        this.trackedObjectives.remove(objective);
    }

    public int getObjectiveDisplaySlotCount(Objective objective) {
        int i = 0;

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displaySlot) == objective) {
                i++;
            }
        }

        return i;
    }

    private void updatePlayerWaypoint(String playerName) {
        ServerPlayer playerByName = this.server.getPlayerList().getPlayerByName(playerName);
        if (playerByName != null) {
            playerByName.level().getWaypointManager().remakeConnections(playerByName);
        }
    }

    private void updateTeamWaypoints(PlayerTeam team) {
        for (ServerLevel serverLevel : this.server.getAllLevels()) {
            team.getPlayers()
                .stream()
                .map(string -> this.server.getPlayerList().getPlayerByName(string))
                .filter(Objects::nonNull)
                .forEach(serverPlayer -> serverLevel.getWaypointManager().remakeConnections(serverPlayer));
        }
    }
    // CraftBukkit start - Send to players
    private void broadcastAll(Packet<?> packet) {
        for (ServerPlayer serverPlayer : this.server.getPlayerList().players) {
            if (serverPlayer.getBukkitEntity().getScoreboard().getHandle() == this) {
                serverPlayer.connection.send(packet);
            }
        }
    }
    // CraftBukkit end
}
