package net.minecraft.server.notifications;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.gamerules.GameRule;

public class EmptyNotificationService implements NotificationService {
    @Override
    public void playerJoined(ServerPlayer player) {
    }

    @Override
    public void playerLeft(ServerPlayer player) {
    }

    @Override
    public void serverStarted() {
    }

    @Override
    public void serverShuttingDown() {
    }

    @Override
    public void serverSaveStarted() {
    }

    @Override
    public void serverSaveCompleted() {
    }

    @Override
    public void serverActivityOccured() {
    }

    @Override
    public void playerOped(ServerOpListEntry entry) {
    }

    @Override
    public void playerDeoped(ServerOpListEntry entry) {
    }

    @Override
    public void playerAddedToAllowlist(NameAndId nameAndId) {
    }

    @Override
    public void playerRemovedFromAllowlist(NameAndId nameAndId) {
    }

    @Override
    public void ipBanned(IpBanListEntry entry) {
    }

    @Override
    public void ipUnbanned(String ip) {
    }

    @Override
    public void playerBanned(UserBanListEntry entry) {
    }

    @Override
    public void playerUnbanned(NameAndId nameAndId) {
    }

    @Override
    public <T> void onGameRuleChanged(net.minecraft.server.level.ServerLevel level, GameRule<T> rule, T value) { // Paper - per-world game rules (add level param)
    }

    @Override
    public void statusHeartbeat() {
    }
}
