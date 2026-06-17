/*
 * This file is part of Leaves (https://github.com/LeavesMC/Leaves)
 *
 * Leaves is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Leaves is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Leaves. If not, see <https://www.gnu.org/licenses/>.
 */

package org.leavesmc.leaves.bot;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.function.FakeplayerConfig;
import fun.bm.mili.config.modules.function.OldFeatureConfig;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.profile.MutablePropertyMap;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.scheduler.FoliaGlobalRegionScheduler;
import io.papermc.paper.util.MCUtil;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.event.bot.*;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class BotList {

    public static BotList INSTANCE;

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;

    public final List<ServerBot> bots = new CopyOnWriteArrayList<>();
    private final BotDataStorage manualSaveDataStorage;
    private final BotDataStorage resumeDataStorage;

    private final Map<UUID, ServerBot> botsByUUID = new ConcurrentHashMap<>();
    private final Map<String, ServerBot> botsByName = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> botsNameByWorldUuid = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> legacyBotsNameByWorldUuid = new ConcurrentHashMap<>();

    public boolean forceShutdown = false;

    public BotList(@NotNull MinecraftServer server) {
        this.server = server;
        this.manualSaveDataStorage = new BotDataStorage(server.storageSource, "fakeplayerdata", "fakeplayer.dat");
        this.resumeDataStorage = new BotDataStorage(server.storageSource, "resume_fakeplayerdata", "resume_fakeplayer.dat");
        INSTANCE = this;
    }

    public void saveAllResumeBots(final int interval) {
        MCUtil.ensureMain("Save Bots", () -> {
            final long now = System.currentTimeMillis() / 50;
            for (ServerBot bot : bots) {
                if (interval == -1 || now - bot.lastSave >= interval) {
                    this.resumeDataStorage.save(bot);
                    bot.lastSave = now;
                }
            }
            return null;
        });
    }

    public void saveAllResumeBots() {
        if (!FakeplayerConfig.enable || !FakeplayerConfig.canResident) {
            return;
        }
        for (ServerBot bot : this.bots) {
            this.resumeDataStorage.save(bot);
        }
    }

    public ServerBot createNewBot(@NotNull BotCreateState state) {
        BotCreateEvent event = new BotCreateEvent(state.fullName(), state.skinName(), state.location(), state.createReason(), state.creator());
        event.setCancelled(!BotUtil.isCreateLegal(state.fullName()));
        this.server.server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return null;
        }

        Location location = event.getCreateLocation();
        ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();

        GameProfile profile = createBotProfile(BotUtil.getBotUUID(state), state.fullName(), state.skin());
        ServerBot bot = new ServerBot(this.server, world, profile);
        bot.createState = state;
        if (event.getCreator() instanceof org.bukkit.entity.Player player) {
            bot.createPlayer = player.getUniqueId();
        }

        return this.placeNewBot(bot, world, location, null);
    }

    public ServerBot loadNewManualSavedBot(String fullName) {
        return this.loadNewBot(fullName, this.manualSaveDataStorage);
    }

    public ServerBot loadNewResumeBot(String fullName) {
        return this.loadNewBot(fullName, this.resumeDataStorage);
    }

    public ServerBot loadNewBot(String fullName, BotDataStorage storage) {
        if (botsByName.containsKey(fullName)) {
            return null;
        }
        try {
            UUID uuid = BotUtil.getBotUUID(fullName);

            BotLoadEvent event = new BotLoadEvent(fullName, uuid);
            this.server.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return null;
            }

            ServerBot bot = new ServerBot(this.server, this.server.getLevel(Level.OVERWORLD), new GameProfile(uuid, fullName));
            bot.connection = new ServerBotPacketListenerImpl(this.server, bot);
            Optional<ValueInput> optional;
            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(bot.problemPath(), LOGGER)) {
                optional = storage.load(bot, scopedCollector);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (optional.isEmpty()) {
                return null;
            }
            ValueInput nbt = optional.get();

            ResourceKey<Level> resourcekey = null;
            if (nbt.getLong("WorldUUIDMost").isPresent() && nbt.getLong("WorldUUIDLeast").isPresent()) {
                org.bukkit.World bWorld = Bukkit.getServer().getWorld(new UUID(nbt.getLong("WorldUUIDMost").orElseThrow(), nbt.getLong("WorldUUIDLeast").orElseThrow()));
                if (bWorld != null) {
                    resourcekey = ((CraftWorld) bWorld).getHandle().dimension();
                }
            }
            if (resourcekey == null) {
                return null;
            }

            ServerLevel world = this.server.getLevel(resourcekey);
            return this.placeNewBot(bot, world, bot.getLocation(), nbt);
        } catch (Exception e) {
            LOGGER.error("Failed to load bot {}", fullName, e);
            return null;
        }
    }

    public ServerBot placeNewBot(@NotNull ServerBot bot, ServerLevel world, Location location, ValueInput save) {
        Optional<ValueInput> optional = Optional.ofNullable(save);

        bot.isRealPlayer = true;
        bot.loginTime = System.currentTimeMillis();
        bot.connection = new ServerBotPacketListenerImpl(this.server, bot);
        bot.connection.markClientLoaded();
        bot.setServerLevel(world);

        BotSpawnLocationEvent event = new BotSpawnLocationEvent(bot.getBukkitEntity(), location);
        this.server.server.getPluginManager().callEvent(event);
        location = event.getSpawnLocation();
        final Location spawnLocation = location;

        bot.spawnIn(world);
        bot.gameMode.setLevel(bot.level());

        bot.setPosRaw(location.getX(), location.getY(), location.getZ());
        bot.setRot(location.getYaw(), location.getPitch());

        bot.connection.teleport(bot.getX(), bot.getY(), bot.getZ(), bot.getYRot(), bot.getXRot());

        this.bots.add(bot);
        this.botsByName.put(bot.getScoreboardName().toLowerCase(Locale.ROOT), bot);
        this.botsByUUID.put(bot.getUUID(), bot);

        bot.suppressTrackerForLogin = true;

        Runnable task = () -> {
            // mili - Folia safety: double-check we're on the right region thread
            if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(world, spawnLocation.blockX() >> 4, spawnLocation.blockZ() >> 4)) {
                MiliBotUtil.warnThreadMismatch("placeNewBot", world, spawnLocation);
            }
            optional.ifPresent(nbt -> {
                bot.loadAndSpawnEnderPearls(nbt);
                bot.loadAndSpawnParentVehicle(nbt);
            });
            // mili - Folia safety: check world still loaded
            if (world.getCurrentWorldData() == null) {
                BotList.LOGGER.warn("Bot {} failed to spawn: world data is null, cleaning up maps", bot.getName().getString());
                // Clean up the maps that were populated before the task was dispatched
                this.bots.remove(bot);
                this.botsByName.remove(bot.getScoreboardName().toLowerCase(Locale.ROOT));
                this.botsByUUID.remove(bot.getUUID());
                return;
            }
            world.getCurrentWorldData().connections.add(bot.connection.connection);
            world.addNewPlayer(bot);
            BotJoinEvent event1 = new BotJoinEvent(bot.getBukkitEntity(), PaperAdventure.asAdventure(Component.translatable("multiplayer.player.joined", bot.getDisplayName())).style(Style.style(NamedTextColor.YELLOW)));
            this.server.server.getPluginManager().callEvent(event1);

            net.kyori.adventure.text.Component joinMessage = event1.joinMessage();
            if (joinMessage != null && !joinMessage.equals(net.kyori.adventure.text.Component.empty())) {
                this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(joinMessage), false);
            }

            bot.renderInfo();
            bot.suppressTrackerForLogin = false;

            bot.level().getChunkSource().chunkMap.addEntity(bot);
            bot.renderData();
            bot.initInventoryMenu();
            botsNameByWorldUuid
                    .computeIfAbsent(bot.level().uuid.toString(), (k) -> ConcurrentHashMap.newKeySet())
                    .add(bot.getBukkitEntity().getName());
            BotList.LOGGER.info("{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", bot.getName().getString(), "Local", bot.getId(), bot.level().serverLevelData.getLevelName(), bot.getX(), bot.getY(), bot.getZ());
        };
        if (TickThread.isTickThreadFor(world, location.blockX() >> 4, location.blockZ() >> 4)) {
            task.run();
        } else {
            RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                    world, location.getBlockX() >> 4, location.blockZ() >> 4,
                    task);
        }

        return bot;
    }

    /*
     * return true if async
     */
    public boolean removeBot(@NotNull ServerBot bot, @NotNull BotRemoveEvent.RemoveReason reason, @Nullable CommandSender remover, boolean save, boolean resume, boolean async) {
        // mili - Folia safety: check world/level availability BEFORE any TickThread call to avoid NPE
        if (bot.level() == null || bot.level().getCurrentWorldData() == null) {
            return this.forceRemoveBot(bot);
        }
        if (async && !TickThread.isTickThreadFor(bot.level(), bot.getX(), bot.getZ())) {
            bot.getBukkitEntity().taskScheduler.schedule((Entity unused) -> this.removeBot(bot, reason, remover, save, resume), null, 1L);
            return true; // async always return true
        }
        return this.removeBot(bot, remover, reason, save, resume);
    }

    /**
     * mili - Folia safety: best-effort cleanup when the bot's region is
     * no longer available (server shutdown, world unload, etc.).
     * Removes the bot from the internal maps and cancels any pending tasks,
     * but does not touch region-owned state.
     */
    private boolean forceRemoveBot(@NotNull ServerBot bot) {
        try {
            if (bot.removeTaskId != -1) {
                ((FoliaGlobalRegionScheduler) Bukkit.getGlobalRegionScheduler()).cancelTask(bot.removeTaskId);
                bot.removeTaskId = -1;
            }
            this.bots.remove(bot);
            this.botsByName.remove(bot.getScoreboardName().toLowerCase(Locale.ROOT));
            this.botsByUUID.remove(bot.getUUID());
            return true;
        } catch (Exception e) {
            LOGGER.error("forceRemoveBot failed for {}", bot.getName().getString(), e);
            return false;
        }
    }

    public boolean removeBot(@NotNull ServerBot bot, @NotNull BotRemoveEvent.RemoveReason reason, @Nullable CommandSender remover, boolean save, boolean resume) {
        return this.removeBot(bot, reason, remover, save, resume, true);
    }

    public boolean removeBot(@NotNull ServerBot bot, @Nullable CommandSender remover, @NotNull BotRemoveEvent.RemoveReason reason, boolean save, boolean resume) {
        // mili - Folia safety: do not attempt region-owned operations if the world
        // has already been unloaded (e.g. server shutdown). Fall back to a safe
        // cleanup that only touches the internal bot maps.
        if (bot.level() == null || bot.level().getCurrentWorldData() == null) {
            return forceRemoveBot(bot);
        }
        BotRemoveEvent event = new BotRemoveEvent(bot.getBukkitEntity(), reason, remover, PaperAdventure.asAdventure(Component.translatable("multiplayer.player.left", bot.getDisplayName())).style(Style.style(NamedTextColor.YELLOW)), save);
        this.server.server.getPluginManager().callEvent(event);

        if (event.isCancelled() && event.getReason() != BotRemoveEvent.RemoveReason.INTERNAL) {
            return false;
        }

        if (bot.removeTaskId != -1) {
            ((FoliaGlobalRegionScheduler) Bukkit.getGlobalRegionScheduler()).cancelTask(bot.removeTaskId);
            bot.removeTaskId = -1;
        }

        bot.disconnect();

        this.resumeDataStorage.removeSavedData(bot);
        if (event.shouldSave()) {
            if (resume) {
                this.resumeDataStorage.save(bot);
            } else {
                this.manualSaveDataStorage.save(bot);
            }
        } else {
            bot.dropAll(true);
            botsNameByWorldUuid.getOrDefault(bot.level().uuid.toString(), Set.of()).remove(bot.getBukkitEntity().getName());
        }

        if (bot.isPassenger() && event.shouldSave()) {
            Entity entity = bot.getRootVehicle();
            if (entity.hasExactlyOnePlayerPassenger()) {
                bot.stopRiding();
                entity.getPassengersAndSelf().forEach((entity1) -> {
                    if (!OldFeatureConfig.villagerVoidTrade && entity1 instanceof AbstractVillager villager) {
                        final Player human = villager.getTradingPlayer();
                        if (human != null) {
                            villager.setTradingPlayer(null);
                        }
                    }
                    entity1.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                });
            }
        }

        bot.unRide();
        for (ThrownEnderpearl thrownEnderpearl : bot.getEnderPearls()) {
            if (!thrownEnderpearl.level().paperConfig().misc.legacyEnderPearlBehavior) {
                thrownEnderpearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, EntityRemoveEvent.Cause.PLAYER_QUIT);
            } else {
                thrownEnderpearl.setOwner(null);
            }
        }

        bot.level().getCurrentWorldData().connections.remove(bot.connection.connection);
        bot.level().removePlayerImmediately(bot, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        bot.retireScheduler();

        this.bots.remove(bot);
        this.botsByName.remove(bot.getScoreboardName().toLowerCase(Locale.ROOT));

        UUID uuid = bot.getUUID();
        ServerBot bot1 = this.botsByUUID.get(uuid);
        if (bot1 == bot) {
            this.botsByUUID.remove(uuid);
        }

        bot.removeTab();
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(bot.getId());
        for (ServerPlayer player : bot.level().players()) {
            if (!(player instanceof ServerBot)) {
                player.connection.send(packet);
            }
        }

        net.kyori.adventure.text.Component removeMessage = event.removeMessage();
        if (removeMessage != null && !removeMessage.equals(net.kyori.adventure.text.Component.empty())) {
            this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(removeMessage), false);
        }
        return true;
    }

    public void removeAllIn(String worldUuid) {
        for (String fullName : new ArrayList<>(this.botsNameByWorldUuid.getOrDefault(worldUuid, Set.of()))) {
            ServerBot bot = this.getBotByName(fullName);
            if (bot != null) {
                this.removeBot(bot, BotRemoveEvent.RemoveReason.INTERNAL, null, FakeplayerConfig.canResident, FakeplayerConfig.canResident);
            }
        }
    }

    public boolean removeAll() {
        boolean finished = true;
        AtomicInteger check = new AtomicInteger();
        AtomicInteger received = new AtomicInteger();
        for (ServerBot bot : this.bots) {
            bot.resume = FakeplayerConfig.canResident;
            // mili - Folia safety: if the bot's region is gone, fall back to safe cleanup
            if (bot.level() == null || bot.level().getCurrentWorldData() == null) {
                forceRemoveBot(bot);
                continue;
            }
            if (TickThread.isTickThreadFor(bot.level(), bot.getX(), bot.getZ())) {
                this.removeBot(bot, BotRemoveEvent.RemoveReason.INTERNAL, null, FakeplayerConfig.canResident, FakeplayerConfig.canResident);
            } else {
                finished = false;
                check.getAndIncrement();
                this.removeBot(bot, check, received, new AtomicInteger());
            }
        }
        return finished;
    }

    private void removeBot(ServerBot bot, AtomicInteger check, AtomicInteger received, AtomicInteger counter) {
        bot.getBukkitEntity().taskScheduler.schedule((Entity unused) -> {
            // mili - Folia safety: cap retry count to prevent infinite loops
            if (counter.get() >= 20) {
                BotList.LOGGER.warn("Giving up removing bot {} after 20 retries (likely world unloaded or region scheduler exhausted). Falling back to force remove.", bot.getName().getString());
                forceRemoveBot(bot);
                received.getAndIncrement();
                if (received.get() >= check.get()) {
                    this.forceShutdown = true;
                    MinecraftServer.getServer().stopServer();
                }
                return;
            }
            counter.getAndIncrement();
            try {
                // mili - Folia safety: check region availability before touching
                if (bot.level() == null || bot.level().getCurrentWorldData() == null) {
                    forceRemoveBot(bot);
                } else {
                    this.removeBot(bot, BotRemoveEvent.RemoveReason.INTERNAL, null, FakeplayerConfig.canResident, FakeplayerConfig.canResident);
                }
                received.getAndIncrement();
            } catch (Exception e) {
                LOGGER.warn("Failed to remove bot {} on retry {}: {}", bot.getName().getString(), counter.get(), e.getMessage());
                this.removeBot(bot, check, received, counter);
            }
            if (received.get() >= check.get()) {
                this.forceShutdown = true;
                MinecraftServer.getServer().stopServer();
            }
        }, null, 1L);
    }

    public void loadResumeBotInfo() {
        if (!FakeplayerConfig.enable || !FakeplayerConfig.canResident) {
            return;
        }
        CompoundTag savedBotList = this.getResumeBotList().copy();
        for (String fullName : savedBotList.keySet()) {
            UUID levelUuid = BotUtil.getBotLevel(fullName, this.resumeDataStorage);
            if (levelUuid == null) {
                LOGGER.warn("Bot {} has no world UUID, skipping loading.", fullName);
                continue;
            }
            this.botsNameByWorldUuid
                    .computeIfAbsent(levelUuid.toString(), (k) -> ConcurrentHashMap.newKeySet())
                    .add(fullName);
        }
        loadLegacyResumeBotInfo();
    }

    private void loadLegacyResumeBotInfo() {
        CompoundTag savedBotList = this.getManualSavedBotList().copy();
        for (String fullName : savedBotList.keySet()) {
            CompoundTag nbt = savedBotList.getCompound(fullName).orElseThrow();
            if (!nbt.getBoolean("resume").orElse(false)) {
                continue;
            }
            UUID levelUuid = BotUtil.getBotLevel(fullName, this.manualSaveDataStorage);
            if (levelUuid == null) {
                LOGGER.warn("Bot {} has no world UUID, skipping loading.", fullName);
                continue;
            }
            this.legacyBotsNameByWorldUuid
                    .computeIfAbsent(levelUuid.toString(), (k) -> ConcurrentHashMap.newKeySet())
                    .add(fullName);
        }
    }

    public void loadResume(String worldUuid) {
        if (!FakeplayerConfig.enable || !FakeplayerConfig.canResident) {
            return;
        }
        new ArrayList<>(this.botsNameByWorldUuid.getOrDefault(worldUuid, Set.of())).forEach(this::loadNewResumeBot);
        new ArrayList<>(this.legacyBotsNameByWorldUuid.getOrDefault(worldUuid, Set.of())).forEach(this::loadNewManualSavedBot);
    }

    public void updateBotLevel(@NotNull ServerBot bot, @NotNull ServerLevel level) {
        String prevUuid = bot.level().uuid.toString();
        String newUuid = level.uuid.toString();
        this.botsNameByWorldUuid
                .computeIfAbsent(newUuid, (k) -> ConcurrentHashMap.newKeySet())
                .add(bot.getBukkitEntity().getName());
        this.botsNameByWorldUuid
                .computeIfAbsent(prevUuid, (k) -> ConcurrentHashMap.newKeySet())
                .remove(bot.getBukkitEntity().getName());
    }

    public void networkTick() {
        this.bots.forEach(ServerBot::networkTick);
    }

    @Nullable
    public ServerBot getBot(@NotNull UUID uuid) {
        return this.botsByUUID.get(uuid);
    }

    @Nullable
    public ServerBot getBotByName(@NotNull String name) {
        return this.botsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public CompoundTag getManualSavedBotList() {
        return this.getSavedBotList(this.manualSaveDataStorage);
    }

    public CompoundTag getResumeBotList() {
        return this.getSavedBotList(this.resumeDataStorage);
    }

    public CompoundTag getSavedBotList(@NotNull BotDataStorage storage) {
        return storage.getSavedBotList();
    }

    @Contract("_, _, _ -> new")
    public static @NotNull GameProfile createBotProfile(UUID uuid, String name, String[] skin) {
        GameProfile profile = new GameProfile(uuid, name, new MutablePropertyMap());
        profile.properties().put("is_bot", new Property("is_bot", "true"));
        if (skin != null) {
            profile.properties().put("textures", new Property("textures", skin[0], skin[1]));
        }
        return profile;
    }
}
