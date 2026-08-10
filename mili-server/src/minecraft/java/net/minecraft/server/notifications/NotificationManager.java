package net.minecraft.server.notifications;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.gamerules.GameRule;

public class NotificationManager implements NotificationService {
    private final List<NotificationService> notificationServices = Lists.newArrayList();

    public void registerService(NotificationService service) {
        this.notificationServices.add(service);
    }

    @Override
    public void playerJoined(ServerPlayer player) {
        this.notificationServices.forEach(notificationService -> notificationService.playerJoined(player));
    }

    @Override
    public void playerLeft(ServerPlayer player) {
        this.notificationServices.forEach(notificationService -> notificationService.playerLeft(player));
    }

    @Override
    public void serverStarted() {
        this.notificationServices.forEach(NotificationService::serverStarted);
    }

    @Override
    public void serverShuttingDown() {
        this.notificationServices.forEach(NotificationService::serverShuttingDown);
    }

    @Override
    public void serverSaveStarted() {
        this.notificationServices.forEach(NotificationService::serverSaveStarted);
    }

    @Override
    public void serverSaveCompleted() {
        this.notificationServices.forEach(NotificationService::serverSaveCompleted);
    }

    @Override
    public void serverActivityOccured() {
        this.notificationServices.forEach(NotificationService::serverActivityOccured);
    }

    @Override
    public void playerOped(ServerOpListEntry entry) {
        this.notificationServices.forEach(notificationService -> notificationService.playerOped(entry));
    }

    @Override
    public void playerDeoped(ServerOpListEntry entry) {
        this.notificationServices.forEach(notificationService -> notificationService.playerDeoped(entry));
    }

    @Override
    public void playerAddedToAllowlist(NameAndId nameAndId) {
        this.notificationServices.forEach(notificationService -> notificationService.playerAddedToAllowlist(nameAndId));
    }

    @Override
    public void playerRemovedFromAllowlist(NameAndId nameAndId) {
        this.notificationServices.forEach(notificationService -> notificationService.playerRemovedFromAllowlist(nameAndId));
    }

    @Override
    public void ipBanned(IpBanListEntry entry) {
        this.notificationServices.forEach(notificationService -> notificationService.ipBanned(entry));
    }

    @Override
    public void ipUnbanned(String ip) {
        this.notificationServices.forEach(notificationService -> notificationService.ipUnbanned(ip));
    }

    @Override
    public void playerBanned(UserBanListEntry entry) {
        this.notificationServices.forEach(notificationService -> notificationService.playerBanned(entry));
    }

    @Override
    public void playerUnbanned(NameAndId nameAndId) {
        this.notificationServices.forEach(notificationService -> notificationService.playerUnbanned(nameAndId));
    }

    @Override
    public <T> void onGameRuleChanged(net.minecraft.server.level.ServerLevel level, GameRule<T> rule, T value) { // Paper - per-world game rules (add level param)
        this.notificationServices.forEach(notificationService -> notificationService.onGameRuleChanged(level, rule, value)); // Paper - per-world game rules (add level param)
    }

    @Override
    public void statusHeartbeat() {
        this.notificationServices.forEach(NotificationService::statusHeartbeat);
    }
}
