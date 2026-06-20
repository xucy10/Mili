package net.minecraft.server.dedicated;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.slf4j.Logger;

public class DedicatedPlayerList extends PlayerList {
    private static final Logger LOGGER = LogUtils.getLogger();

    public DedicatedPlayerList(DedicatedServer server, LayeredRegistryAccess<RegistryLayer> registries, PlayerDataStorage playerIo) {
        super(server, registries, playerIo, server.notificationManager());
        this.setViewDistance(server.viewDistance());
        this.setSimulationDistance(server.simulationDistance());
        // Paper start - fix converting txt to json file; moved from constructor
    }
    @Override
    public void loadAndSaveFiles() {
        // Paper end - fix converting txt to json file
        this.loadUserBanList();
        this.saveUserBanList();
        this.loadIpBanList();
        this.saveIpBanList();
        this.loadOps();
        this.loadWhiteList();
        this.saveOps();
        if (!this.getWhiteList().getFile().exists()) {
            this.saveWhiteList();
        }
    }

    @Override
    public void reloadWhiteList() {
        this.loadWhiteList();
    }

    private void saveIpBanList() {
        try {
            this.getIpBans().save();
        } catch (IOException var2) {
            LOGGER.warn("Failed to save ip banlist: ", (Throwable)var2);
        }
    }

    private void saveUserBanList() {
        try {
            this.getBans().save();
        } catch (IOException var2) {
            LOGGER.warn("Failed to save user banlist: ", (Throwable)var2);
        }
    }

    private void loadIpBanList() {
        try {
            this.getIpBans().load();
        } catch (IOException var2) {
            LOGGER.warn("Failed to load ip banlist: ", (Throwable)var2);
        }
    }

    private void loadUserBanList() {
        try {
            this.getBans().load();
        } catch (IOException var2) {
            LOGGER.warn("Failed to load user banlist: ", (Throwable)var2);
        }
    }

    private void loadOps() {
        try {
            this.getOps().load();
        } catch (Exception var2) {
            LOGGER.warn("Failed to load operators list: ", (Throwable)var2);
        }
    }

    private void saveOps() {
        try {
            this.getOps().save();
        } catch (Exception var2) {
            LOGGER.warn("Failed to save operators list: ", (Throwable)var2);
        }
    }

    private void loadWhiteList() {
        try {
            this.getWhiteList().load();
        } catch (Exception var2) {
            LOGGER.warn("Failed to load white-list: ", (Throwable)var2);
        }
    }

    private void saveWhiteList() {
        try {
            this.getWhiteList().save();
        } catch (Exception var2) {
            LOGGER.warn("Failed to save white-list: ", (Throwable)var2);
        }
    }

    @Override
    public boolean isWhiteListed(NameAndId nameAndId) {
        return !this.isUsingWhitelist() || this.isOp(nameAndId) || this.getWhiteList().isWhiteListed(nameAndId);
    }

    @Override
    public DedicatedServer getServer() {
        return (DedicatedServer)super.getServer();
    }

    @Override
    public boolean canBypassPlayerLimit(NameAndId nameAndId) {
        return this.getOps().canBypassPlayerLimit(nameAndId);
    }
}
