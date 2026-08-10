package net.minecraft.server.players;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class SleepStatus {
    private int activePlayers;
    private int sleepingPlayers;

    public boolean areEnoughSleeping(int requiredSleepPercentage) {
        return this.sleepingPlayers >= this.sleepersNeeded(requiredSleepPercentage);
    }

    public boolean areEnoughDeepSleeping(int requiredSleepPercentage, List<ServerPlayer> sleepingPlayers) {
        // CraftBukkit start
        int i = (int) sleepingPlayers.stream().filter(player -> player.isSleepingLongEnough() || player.fauxSleeping).count();
        boolean anyDeepSleep = sleepingPlayers.stream().anyMatch(Player::isSleepingLongEnough);
        return anyDeepSleep && i >= this.sleepersNeeded(requiredSleepPercentage);
        // CraftBukkit end
    }

    public int sleepersNeeded(int requiredSleepPercentage) {
        return Math.max(1, Mth.ceil(this.activePlayers * requiredSleepPercentage / 100.0F));
    }

    public void removeAllSleepers() {
        this.sleepingPlayers = 0;
    }

    public int amountSleeping() {
        return this.sleepingPlayers;
    }

    public boolean update(List<ServerPlayer> players) {
        int i = this.activePlayers;
        int i1 = this.sleepingPlayers;
        this.activePlayers = 0;
        this.sleepingPlayers = 0;
        boolean anySleep = false; // CraftBukkit

        for (ServerPlayer serverPlayer : players) {
            if (!serverPlayer.isSpectator()) {
                this.activePlayers++;
                if (serverPlayer.isSleeping() || serverPlayer.fauxSleeping) { // CraftBukkit
                    this.sleepingPlayers++;
                }
                // CraftBukkit start
                if (serverPlayer.isSleeping()) {
                    anySleep = true;
                }
                // CraftBukkit end
            }
        }

        return anySleep && (i1 > 0 || this.sleepingPlayers > 0) && (i != this.activePlayers || i1 != this.sleepingPlayers); // CraftBukkit
    }
}
