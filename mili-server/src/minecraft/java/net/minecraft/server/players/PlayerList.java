package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.notifications.NotificationService;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.FileUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class PlayerList {
    public static final File USERBANLIST_FILE = new File("banned-players.json");
    public static final File IPBANLIST_FILE = new File("banned-ips.json");
    public static final File OPLIST_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    public static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
    public static final Component DUPLICATE_LOGIN_DISCONNECT_MESSAGE = Component.translatable("multiplayer.disconnect.duplicate_login");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEND_PLAYER_INFO_INTERVAL = 600;
    private static final ThreadLocal<SimpleDateFormat> BAN_DATE_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z", Locale.ROOT)); // Folia - region threading - SDF is not thread-safe
    private final MinecraftServer server;
    public final List<ServerPlayer> players = new java.util.concurrent.CopyOnWriteArrayList(); // CraftBukkit - ArrayList -> CopyOnWriteArrayList: Iterator safety
    private final Map<UUID, ServerPlayer> playersByUUID = new java.util.concurrent.ConcurrentHashMap<>(); // Folia - region threading - change to CHM - Note: we do NOT expect concurrency PER KEY!
    private final UserBanList bans;
    private final IpBanList ipBans;
    private final ServerOpList ops;
    private final UserWhiteList whitelist;
    // CraftBukkit start
    // private final Map<UUID, ServerStatsCounter> stats = Maps.newHashMap();
    // private final Map<UUID, PlayerAdvancements> advancements = Maps.newHashMap();
    // CraftBukkit end
    public final PlayerDataStorage playerIo;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    private int viewDistance;
    private int simulationDistance;
    private boolean allowCommandsForAllPlayers;
    private int sendAllPlayerInfoIn;
    public final List<ServerPlayer> realPlayers = new java.util.concurrent.CopyOnWriteArrayList(); // Leaves - replay api

    // CraftBukkit start
    private org.bukkit.craftbukkit.CraftServer cserver;
    private final Map<String,ServerPlayer> playersByName = new java.util.HashMap<>();
    public @Nullable String collideRuleTeamName; // Paper - Configurable player collision

    // Folia start - region threading
    private final Object connectionsStateLock = new Object();
    private final Map<String, Connection> connectionByName = new java.util.HashMap<>();
    private final Map<UUID, Connection> connectionById = new java.util.HashMap<>();
    private final it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<Connection> usersCountedAgainstLimit = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();

    public boolean pushPendingJoin(String userName, UUID byId, Connection conn) {
        userName = userName.toLowerCase(java.util.Locale.ROOT);
        Connection conflictingName, conflictingId;
        synchronized (this.connectionsStateLock) {
            conflictingName = this.connectionByName.get(userName);
            conflictingId = this.connectionById.get(byId);

            if (conflictingName == null && conflictingId == null) {
                // Folia start - max concurrent login
                int loggedInCount = 0;
                for (Connection value : this.connectionById.values()) {
                    if (value.getPacketListener() instanceof ServerGamePacketListenerImpl) {
                        ++loggedInCount;
                    }
                }
                if ((this.connectionById.size() - loggedInCount) >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.maxJoinsPerTick) {
                    return false;
                }
                // Folia end - max concurrent login
                this.connectionByName.put(userName, conn);
                this.connectionById.put(byId, conn);
            }
        }

        Component message = Component.translatable("multiplayer.disconnect.duplicate_login", new Object[0]);

        if (conflictingId != null || conflictingName != null) {
            if (conflictingName != null && conflictingName.isPlayerConnected()) {
                conflictingName.disconnectSafely(message, io.papermc.paper.connection.DisconnectionReason.DUPLICATE_LOGIN_MESSAGE);
            }
            if (conflictingName != conflictingId && conflictingId != null && conflictingId.isPlayerConnected()) {
                conflictingId.disconnectSafely(message, io.papermc.paper.connection.DisconnectionReason.DUPLICATE_LOGIN_MESSAGE);
            }
        }

        return conflictingName == null && conflictingId == null;
    }

    public void removeConnection(String userName, UUID byId, Connection conn) {
        userName = userName.toLowerCase(java.util.Locale.ROOT);
        synchronized (this.connectionsStateLock) {
            this.connectionByName.remove(userName, conn);
            this.connectionById.remove(byId, conn);
            this.usersCountedAgainstLimit.remove(conn);
        }
    }

    private boolean countConnection(Connection conn, int limit) {
        synchronized (this.connectionsStateLock) {
            int count = this.usersCountedAgainstLimit.size();
            if (count >= limit) {
                return false;
            }
            this.usersCountedAgainstLimit.add(conn);
            return true;
        }
    }
    // Folia end - region threading

    public PlayerList(
        MinecraftServer server, LayeredRegistryAccess<RegistryLayer> registries, PlayerDataStorage playerIo, NotificationService notificationService
    ) {
        this.cserver = server.server = new org.bukkit.craftbukkit.CraftServer((net.minecraft.server.dedicated.DedicatedServer) server, this);
        server.console = new com.destroystokyo.paper.console.TerminalConsoleCommandSender(); // Paper
        // CraftBukkit end
        this.server = server;
        this.registries = registries;
        this.playerIo = playerIo;
        this.whitelist = new UserWhiteList(WHITELIST_FILE, notificationService);
        this.ops = new ServerOpList(OPLIST_FILE, notificationService);
        this.bans = new UserBanList(USERBANLIST_FILE, notificationService);
        this.ipBans = new IpBanList(IPBANLIST_FILE, notificationService);
    }

    abstract public void loadAndSaveFiles(); // Paper - fix converting txt to json file; moved from DedicatedPlayerList constructor

    // Leaves start - replay mod api
    public void placeNewPhotographer(Connection connection, org.leavesmc.leaves.replay.ServerPhotographer player, ServerLevel worldserver) {
        player.isRealPlayer = true; // Paper
        player.loginTime = System.currentTimeMillis(); // Paper

        ServerLevel worldserver1 = worldserver;

        player.setServerLevel(worldserver1);
        player.spawnIn(worldserver1);
        player.gameMode.setLevel((ServerLevel) player.level());

        LevelData worlddata = worldserver1.getLevelData();

        //player.loadGameTypes(null); // simply fix for https://github.com/MC-XiaoHei/ISeeYou/issues/70
        ServerGamePacketListenerImpl playerconnection = new ServerGamePacketListenerImpl(this.server, connection, player, CommonListenerCookie.createInitial(player.gameProfile, false));
        GameRules gamerules = worldserver1.getGameRules();
        boolean flag = gamerules.get(GameRules.IMMEDIATE_RESPAWN);
        boolean flag1 = gamerules.get(GameRules.REDUCED_DEBUG_INFO);
        boolean flag2 = gamerules.get(GameRules.LIMITED_CRAFTING);

        playerconnection.send(new ClientboundLoginPacket(player.getId(), worlddata.isHardcore(), this.server.levelKeys(), this.getMaxPlayers(), worldserver1.getWorld().getSendViewDistance(), worldserver1.getWorld().getSimulationDistance(), flag1, !flag, flag2, player.createCommonSpawnInfo(worldserver1), this.server.enforceSecureProfile())); // Paper - replace old player chunk management
        player.getBukkitEntity().sendSupportedChannels(); // CraftBukkit
        playerconnection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        playerconnection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        playerconnection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
        RecipeManager craftingmanager = this.server.getRecipeManager();
        playerconnection.send(new ClientboundUpdateRecipesPacket(craftingmanager.getSynchronizedItemProperties(), craftingmanager.getSynchronizedStonecutterRecipes()));

        this.sendPlayerPermissionLevel(player);
        player.getStats().markAllDirty();
        player.getRecipeBook().sendInitialRecipeBook(player);
        this.updateEntireScoreboard(worldserver1.getScoreboard(), player);
        this.server.invalidateStatus();

        playerconnection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ServerStatus serverping = this.server.getStatus();

        if (serverping != null) {
            player.sendServerStatus(serverping);
        }

        this.players.add(player);
        this.playersByName.put(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT), player); // Spigot
        this.playersByUUID.put(player.getUUID(), player);

        player.suppressTrackerForLogin = true;
        worldserver1.getCurrentWorldData().connections.add(player.connection.connection);
        worldserver1.addNewPlayer(player);
        this.server.getCustomBossEvents().onPlayerConnect(player);
        org.bukkit.craftbukkit.entity.CraftPlayer bukkitPlayer = player.getBukkitEntity();

        player.containerMenu.transferTo(player.containerMenu, bukkitPlayer);
        if (!player.connection.isAcceptingMessages()) {
            return;
        }

        // org.leavesmc.leaves.protocol.core.LeavesProtocolManager.handlePlayerJoin(player); // Leaves - protocol // Mili - moved to end of placeNewPlayer

        // Leaves start - bot support
        if (fun.bm.mili.config.modules.function.FakeplayerConfig.enable) {
            org.leavesmc.leaves.bot.ServerBot bot = this.server.getBotList().getBotByName(player.getScoreboardName());
            if (bot != null) {
                this.server.getBotList().removeBot(bot, org.leavesmc.leaves.event.bot.BotRemoveEvent.RemoveReason.INTERNAL, player.getBukkitEntity(), false, false);
            }
            this.server.getBotList().bots.forEach(bot1 -> {
                bot1.sendPlayerInfo(player);
                bot1.sendFakeDataIfNeed(player, true);
            }); // Leaves - render bot
        }
        // Leaves end - bot support

        final List<ServerPlayer> onlinePlayers = Lists.newArrayListWithExpectedSize(this.players.size() - 1);
        final List<ServerPlayer> playerList = List.copyOf(this.players); // Mili - copy to avoid ConcurrentModificationException
        for (ServerPlayer serverPlayer : playerList) {
            if (serverPlayer == player || !bukkitPlayer.canSee(serverPlayer.getBukkitEntity())) {
                continue;
            }

            // Leaves start - skip photographer
            if (serverPlayer instanceof org.leavesmc.leaves.replay.ServerPhotographer) {
                continue;
            }
            // Leaves end - skip photographer

            onlinePlayers.add(serverPlayer);
        }
        if (!onlinePlayers.isEmpty()) {
            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(onlinePlayers, player));
        }

        player.sentListPacket = true;
        player.suppressTrackerForLogin = false;
        player.level().getChunkSource().chunkMap.addEntity(player);

        this.sendLevelInfo(player, worldserver1);

        if (player.level() == worldserver1 && !worldserver1.players().contains(player)) {
            worldserver1.addNewPlayer(player);
            this.server.getCustomBossEvents().onPlayerConnect(player);
        }

        worldserver1 = player.level();
        for (MobEffectInstance mobeffect : player.getActiveEffects()) {
            playerconnection.send(new ClientboundUpdateMobEffectPacket(player.getId(), mobeffect, false));
        }

        if (player.isDeadOrDying()) {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> plains = worldserver1.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                    new net.minecraft.world.level.chunk.EmptyLevelChunk(worldserver1, player.chunkPosition(), plains),
                    worldserver1.getLightEngine(), null, null, false)
            );
        }
    }
    // Leaves end - replay mod api

    public void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        player.isRealPlayer = true; // Paper
        player.loginTime = System.currentTimeMillis(); // Paper - Replace OfflinePlayer#getLastPlayed
        player.lastSave = System.nanoTime(); // Mili fix - #459: initialize lastSave to prevent abnormal autosave interval on first tick
        NameAndId nameAndId = player.nameAndId();
        UserNameToIdResolver userNameToIdResolver = this.server.services().nameToIdCache();
        Optional<NameAndId> optional = userNameToIdResolver.get(nameAndId.id());
        String string = optional.map(NameAndId::name).orElse(nameAndId.name());
        if (player.lastKnownName != null) { string = player.lastKnownName; player.lastKnownName = null; } // CraftBukkit - Better rename detection
        userNameToIdResolver.add(nameAndId);
        ServerLevel serverLevel = player.level();
        String loggableAddress = connection.getLoggableAddress(this.server.logIPs());
        LevelData levelData = serverLevel.getLevelData();
        ServerGamePacketListenerImpl serverGamePacketListenerImpl = new ServerGamePacketListenerImpl(this.server, connection, player, cookie);
        // Folia start - rewrite login process
        // only after setting the connection listener to game type, add the connection to this regions list
        serverLevel.getCurrentWorldData().connections.add(connection);
        // Folia end - rewrite login process
        if (!me.earthme.luminol.config.modules.optimizations.AsyncProtocolChangeConfig.enabled) { // Luminol - Async protocol switch // we will run async switch once these main thread logics became done
        connection.setupInboundProtocol(
            GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess()), serverGamePacketListenerImpl),
            serverGamePacketListenerImpl
        );
        } // Luminol - Async protocol switch
        serverGamePacketListenerImpl.suspendFlushing();

        // Leaves start - Bytebuf API
        if (!(player instanceof org.leavesmc.leaves.bot.ServerBot) && !(player instanceof org.leavesmc.leaves.replay.ServerPhotographer)) {
            org.leavesmc.leaves.bytebuf.internal.InternalBytebufHandler.updatePlayer(player);
        }
        // Leaves end - Bytebuf API

        GameRules gameRules = serverLevel.getGameRules();
        boolean flag = gameRules.get(GameRules.IMMEDIATE_RESPAWN);
        boolean flag1 = gameRules.get(GameRules.REDUCED_DEBUG_INFO);
        boolean flag2 = gameRules.get(GameRules.LIMITED_CRAFTING);
        serverGamePacketListenerImpl.send(
            new ClientboundLoginPacket(
                player.getId(),
                levelData.isHardcore(),
                this.server.levelKeys(),
                this.getMaxPlayers(),
                io.papermc.paper.FeatureHooks.getViewDistance(serverLevel), // Paper - view distance
                io.papermc.paper.FeatureHooks.getSimulationDistance(serverLevel), // Paper - simulation distance
                flag1,
                !flag,
                flag2,
                player.createCommonSpawnInfo(serverLevel),
                this.server.enforceSecureProfile()
            )
        );
        player.getBukkitEntity().sendSupportedChannels(); // CraftBukkit
        serverGamePacketListenerImpl.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
        serverGamePacketListenerImpl.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        serverGamePacketListenerImpl.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
        RecipeManager recipeManager = this.server.getRecipeManager();
        serverGamePacketListenerImpl.send(
            new ClientboundUpdateRecipesPacket(recipeManager.getSynchronizedItemProperties(), recipeManager.getSynchronizedStonecutterRecipes())
        );
        this.sendPlayerPermissionLevel(player);
        player.getStats().markAllDirty();
        player.getRecipeBook().sendInitialRecipeBook(player);
        //this.updateEntireScoreboard(serverLevel.getScoreboard(), player); // Folia - region threading
        this.server.invalidateStatus();
        MutableComponent mutableComponent;
        if (player.getGameProfile().name().equalsIgnoreCase(string)) {
            mutableComponent = Component.translatable("multiplayer.player.joined", player.getDisplayName());
        } else {
            mutableComponent = Component.translatable("multiplayer.player.joined.renamed", player.getDisplayName(), string);
        }

        // CraftBukkit start
        mutableComponent.withStyle(ChatFormatting.YELLOW);
        Component joinMessage = mutableComponent; // Paper - Adventure
        serverGamePacketListenerImpl.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ServerStatus status = this.server.getStatus();
        if (status != null && !cookie.transferred()) {
            player.sendServerStatus(status);
        }

        // player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players)); // CraftBukkit - replaced with loop below
        this.players.add(player);
        this.realPlayers.add(player); // Leaves - replay api
        this.playersByName.put(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT), player); // Spigot
        this.playersByUUID.put(player.getUUID(), player);
        // this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player))); // CraftBukkit - replaced with loop below
        // Paper start - Fire PlayerJoinEvent when Player is actually ready; correctly register player BEFORE PlayerJoinEvent, so the entity is valid and doesn't require tick delay hacks
        player.suppressTrackerForLogin = true;
        this.sendLevelInfo(player, serverLevel);
        serverLevel.addNewPlayer(player);
        this.server.getCustomBossEvents().onPlayerConnect(player); // see commented out section below serverLevel.addPlayerJoin(player);
        // Paper end - Fire PlayerJoinEvent when Player is actually ready
        player.initInventoryMenu();
        // CraftBukkit start
        org.bukkit.craftbukkit.entity.CraftPlayer bukkitPlayer = player.getBukkitEntity();

        // Ensure that player inventory is populated with its viewer
        player.containerMenu.transferTo(player.containerMenu, bukkitPlayer);

        org.bukkit.event.player.PlayerJoinEvent playerJoinEvent = new org.bukkit.event.player.PlayerJoinEvent(bukkitPlayer, io.papermc.paper.adventure.PaperAdventure.asAdventure(mutableComponent)); // Paper - Adventure
        this.cserver.getPluginManager().callEvent(playerJoinEvent);

        if (!player.connection.isAcceptingMessages()) {
            //return; // Folia - region threading - must still allow the player to connect, as we must add to chunk map before handling disconnect
        }

        // Leaves start - bot support
        if (fun.bm.mili.config.modules.function.FakeplayerConfig.enable) {
            org.leavesmc.leaves.bot.ServerBot bot = this.server.getBotList().getBotByName(player.getScoreboardName());
            if (bot != null) {
                this.server.getBotList().removeBot(bot, org.leavesmc.leaves.event.bot.BotRemoveEvent.RemoveReason.INTERNAL, player.getBukkitEntity(), false, false);
            }
            this.server.getBotList().bots.forEach(bot1 -> {
                bot1.sendPlayerInfo(player);
                bot1.sendFakeDataIfNeed(player, true);
            }); // Leaves - render bot
        }
        // Leaves end - bot support

        final net.kyori.adventure.text.Component jm = playerJoinEvent.joinMessage();

        if (jm != null && !jm.equals(net.kyori.adventure.text.Component.empty())) { // Paper - Adventure
            joinMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(jm); // Paper - Adventure
            this.server.getPlayerList().broadcastSystemMessage(joinMessage, false); // Paper - Adventure
        }
        // CraftBukkit end

        // CraftBukkit start - sendAll above replaced with this loop
        ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)); // Paper - Add Listing API for Player

        final List<ServerPlayer> onlinePlayers = Lists.newArrayListWithExpectedSize(this.players.size() - 1); // Paper - Use single player info update packet on join
        for (ServerPlayer entityplayer1 : this.players) { // Folia - region threading

            if (entityplayer1.getBukkitEntity().canSee(bukkitPlayer)) {
                // Paper start - Add Listing API for Player
                if (entityplayer1.getBukkitEntity().isListed(bukkitPlayer)) {
                    // Paper end - Add Listing API for Player
                    entityplayer1.connection.send(packet);
                    // Paper start - Add Listing API for Player
                } else {
                    entityplayer1.connection.send(ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(player, false));
                }
                // Paper end - Add Listing API for Player
            }

            if (entityplayer1 == player || !bukkitPlayer.canSee(entityplayer1.getBukkitEntity())) { // Paper - Use single player info update packet on join; Don't include joining player
                continue;
            }

            onlinePlayers.add(entityplayer1); // Paper - Use single player info update packet on join
        }
        // Paper start - Use single player info update packet on join
        if (!onlinePlayers.isEmpty()) {
            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(onlinePlayers, player)); // Paper - Add Listing API for Player
        }
        // Paper end - Use single player info update packet on join
        player.sentListPacket = true;
        player.suppressTrackerForLogin = false; // Paper - Fire PlayerJoinEvent when Player is actually ready
        ((ServerLevel)player.level()).getChunkSource().chunkMap.addEntity(player); // Paper - Fire PlayerJoinEvent when Player is actually ready; track entity now
        // CraftBukkit end

        //player.refreshEntityData(player); // CraftBukkit - BungeeCord#2321, send complete data to self on spawn // Paper - THIS IS NOT NEEDED ANYMORE

        this.sendLevelInfo(player, serverLevel);

        // CraftBukkit start - Only add if the player wasn't moved in the event
        if (player.level() == serverLevel && !serverLevel.players().contains(player)) {
            serverLevel.addNewPlayer(player);
            this.server.getCustomBossEvents().onPlayerConnect(player);
        }

        serverLevel = player.level(); // CraftBukkit - Update in case join event changed it
        // CraftBukkit end
        this.sendActivePlayerEffects(player);
        // Paper - move loading pearls / parent vehicle up
        player.initInventoryMenu();
        this.server.notificationManager().playerJoined(player);
        serverGamePacketListenerImpl.resumeFlushing();
        // Paper start - Configurable player collision; Add to collideRule team if needed
        final net.minecraft.world.scores.Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
        final PlayerTeam collideRuleTeam = scoreboard.getPlayerTeam(this.collideRuleTeamName);
        if (false && this.collideRuleTeamName != null && collideRuleTeam != null && player.getTeam() == null) { // Folia - region threading
            scoreboard.addPlayerToTeam(player.getScoreboardName(), collideRuleTeam);
        }
        // Paper end - Configurable player collision
        // CraftBukkit start - moved down
        LOGGER.info(
            "{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", // CraftBukkit - add world name
            player.getPlainTextName(),
            loggableAddress,
            player.getId(),
            serverLevel.serverLevelData.getLevelName(), // CraftBukkit - add world name
            player.getX(),
            player.getY(),
            player.getZ()
        );
        // CraftBukkit end - moved down
        // Paper start - Send empty chunk, so players aren't stuck in the world loading screen with our chunk system not sending chunks when dead
        if (player.isDeadOrDying()) {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> plains = serverLevel.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                new net.minecraft.world.level.chunk.EmptyLevelChunk(serverLevel, player.chunkPosition(), plains),
                serverLevel.getLightEngine(), (java.util.BitSet)null, (java.util.BitSet) null, true) // Paper - Anti-Xray
            );
        }
        // Paper end - Send empty chunk
        // Luminol start - Async protocol switch
        if (me.earthme.luminol.config.modules.optimizations.AsyncProtocolChangeConfig.enabled) {
            // auto read will be enabled once the async switch is done
            connection.setupInboundProtocolAsync(
                    GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess()), serverGamePacketListenerImpl),
                    serverGamePacketListenerImpl,
                    null, // we don't need do anything more
                    true // start auto read which we have disabled in configuration handler
            );
        }
        // Luminol end
        org.leavesmc.leaves.protocol.core.LeavesProtocolManager.handlePlayerJoin(player); // Leaves - protocol
    }

    public void updateEntireScoreboard(ServerScoreboard scoreboard, ServerPlayer player) {
        Set<Objective> set = Sets.newHashSet();

        for (PlayerTeam playerTeam : scoreboard.getPlayerTeams()) {
            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));
        }

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            Objective displayObjective = scoreboard.getDisplayObjective(displaySlot);
            if (displayObjective != null && !set.contains(displayObjective)) {
                for (Packet<?> packet : scoreboard.getStartTrackingPackets(displayObjective)) {
                    player.connection.send(packet);
                }

                set.add(displayObjective);
            }
        }
    }

    // Paper start - virtual world border API
    private void broadcastWorldborder(Packet<?> packet, ResourceKey<Level> dimension) {
        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.level().dimension() == dimension && serverPlayer.getBukkitEntity().getWorldBorder() == null) {
                serverPlayer.connection.send(packet);
            }
        }
    }
    // Paper end - virtual world border API
    public void addWorldborderListener(final ServerLevel level) {
        level.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onSetSize(WorldBorder border, double size) {
                PlayerList.this.broadcastWorldborder(new ClientboundSetBorderSizePacket(border), level.dimension()); // Paper - virtual world border API
            }

            @Override
            public void onLerpSize(WorldBorder border, double oldSize, double newSize, long time, long startTime) {
                PlayerList.this.broadcastWorldborder(new ClientboundSetBorderLerpSizePacket(border), level.dimension()); // Paper - virtual world border API
            }

            @Override
            public void onSetCenter(WorldBorder border, double x, double z) {
                PlayerList.this.broadcastWorldborder(new ClientboundSetBorderCenterPacket(border), level.dimension()); // Paper - virtual world border API
            }

            @Override
            public void onSetWarningTime(WorldBorder border, int warningTime) {
                PlayerList.this.broadcastWorldborder(new ClientboundSetBorderWarningDelayPacket(border), level.dimension()); // Paper - virtual world border API
            }

            @Override
            public void onSetWarningBlocks(WorldBorder border, int warningBlocks) {
                PlayerList.this.broadcastWorldborder(new ClientboundSetBorderWarningDistancePacket(border), level.dimension()); // Paper - virtual world border API
            }

            @Override
            public void onSetDamagePerBlock(WorldBorder border, double damagePerBlock) {
            }

            @Override
            public void onSetSafeZone(WorldBorder border, double safeZone) {
            }
        });
    }

    public Optional<CompoundTag> loadPlayerData(NameAndId nameAndId) {
        CompoundTag loadedPlayerTag = this.server.getWorldData().getLoadedPlayerTag();
        if (this.server.isSingleplayerOwner(nameAndId) && loadedPlayerTag != null) {
            LOGGER.debug("loading single player");
            return Optional.of(loadedPlayerTag);
        } else {
            return this.playerIo.load(nameAndId);
        }
    }

    protected void save(ServerPlayer player) {
        if (player instanceof org.leavesmc.leaves.replay.ServerPhotographer) return; // Leaves - skip photographer
        if (!player.getBukkitEntity().isPersistent()) return; // CraftBukkit
        player.lastSave = System.nanoTime(); // Folia - region threading - changed to nanoTime tracking
        this.playerIo.save(player);
        ServerStatsCounter serverStatsCounter = player.getStats(); // CraftBukkit
        if (serverStatsCounter != null) {
            serverStatsCounter.save();
        }

        PlayerAdvancements playerAdvancements = player.getAdvancements(); // CraftBukkit
        if (playerAdvancements != null) {
            playerAdvancements.save();
        }
    }

    // Leaves start - replay mod api
    public void removePhotographer(org.leavesmc.leaves.replay.ServerPhotographer entityplayer) {
        ServerLevel worldserver = entityplayer.level();

        entityplayer.awardStat(Stats.LEAVE_GAME);

        if (entityplayer.containerMenu != entityplayer.inventoryMenu) {
            entityplayer.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DISCONNECT);
        }

        if (server.isSameThread()) entityplayer.doTick();

        if (this.collideRuleTeamName != null) {
            final net.minecraft.world.scores.Scoreboard scoreBoard = this.server.getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreBoard.getPlayersTeam(this.collideRuleTeamName);
            if (entityplayer.getTeam() == team && team != null) {
                scoreBoard.removePlayerFromTeam(entityplayer.getScoreboardName(), team);
            }
        }

        worldserver.getCurrentWorldData().connections.remove(entityplayer.connection.connection);
        worldserver.removePlayerImmediately(entityplayer, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        entityplayer.retireScheduler();
        entityplayer.getAdvancements().stopListening();
        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT));
        this.server.getCustomBossEvents().onPlayerDisconnect(entityplayer);
        UUID uuid = entityplayer.getUUID();
        ServerPlayer entityplayer1 = this.playersByUUID.get(uuid);

        if (entityplayer1 == entityplayer) {
            this.playersByUUID.remove(uuid);
        }

        this.cserver.getScoreboardManager().removePlayer(entityplayer.getBukkitEntity());
    }
    // Leaves stop - replay mod api

    public net.kyori.adventure.text.@Nullable Component remove(ServerPlayer player) { // CraftBukkit - return string // Paper - return Component
        // Paper start - Fix kick event leave message not being sent
        return this.remove(player, net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? player.getBukkitEntity().displayName() : io.papermc.paper.adventure.PaperAdventure.asAdventure(player.getDisplayName())));
    }
    public net.kyori.adventure.text.@Nullable Component remove(ServerPlayer player, net.kyori.adventure.text.Component leaveMessage) {
        // Paper end - Fix kick event leave message not being sent
        org.leavesmc.leaves.protocol.core.LeavesProtocolManager.handlePlayerLeave(player); // Leaves - protocol
        ServerLevel serverLevel = player.level();
        player.awardStat(Stats.LEAVE_GAME);
        // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
        // See SPIGOT-5799, SPIGOT-6145
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DISCONNECT); // Paper - Inventory close reason
        }

        org.bukkit.event.player.PlayerQuitEvent playerQuitEvent = new org.bukkit.event.player.PlayerQuitEvent(player.getBukkitEntity(), leaveMessage, player.quitReason); // Paper - Adventure & Add API for quit reason
        this.cserver.getPluginManager().callEvent(playerQuitEvent);
        player.getBukkitEntity().disconnect();

        if (this.server.isSameThread()) player.doTick(); // SPIGOT-924 // Paper - Improved watchdog support; don't tick during emergency shutdowns
        // CraftBukkit end

        // Paper start - Configurable player collision; Remove from collideRule team if needed
        if (false && this.collideRuleTeamName != null) { // Folia - region threading
            final net.minecraft.world.scores.Scoreboard scoreBoard = this.server.getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreBoard.getPlayersTeam(this.collideRuleTeamName);
            if (player.getTeam() == team && team != null) {
                scoreBoard.removePlayerFromTeam(player.getScoreboardName(), team);
            }
        }
        // Paper end - Configurable player collision

        // Paper - Drop carried item when player has disconnected
        if (!player.containerMenu.getCarried().isEmpty()) {
            net.minecraft.world.item.ItemStack carried = player.containerMenu.getCarried();
            player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            player.drop(carried, false);
        }
        // Paper end - Drop carried item when player has disconnected
        this.save(player);
        if (player.isPassenger()) {
            Entity rootVehicle = player.getRootVehicle();
            if (rootVehicle.hasExactlyOnePlayerPassenger()) {
                LOGGER.debug("Removing player mount");
                player.stopRiding();
                rootVehicle.getPassengersAndSelf().forEach(entity -> {
                    // Paper start - Fix villager boat exploit
                    if (entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager villager) {
                        final net.minecraft.world.entity.player.Player human = villager.getTradingPlayer();
                        if (human != null) {
                            villager.setTradingPlayer(null);
                        }
                    }
                    // Paper end - Fix villager boat exploit
                    entity.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, org.bukkit.event.entity.EntityRemoveEvent.Cause.PLAYER_QUIT); // CraftBukkit - add Bukkit remove cause
                });
            }
        }

        player.unRide();

        for (ThrownEnderpearl thrownEnderpearl : player.getEnderPearls()) {
            // Paper start - Allow using old ender pearl behavior
            if (!thrownEnderpearl.level().paperConfig().misc.legacyEnderPearlBehavior) {
                thrownEnderpearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, org.bukkit.event.entity.EntityRemoveEvent.Cause.PLAYER_QUIT); // CraftBukkit - add Bukkit remove cause
            }
            // Paper end - Allow using old ender pearl behavior
        }

        serverLevel.removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        player.retireScheduler(); // Paper - Folia schedulers
        player.getBukkitEntity().packetProcessor.close(); // Folia - region threading
        player.getAdvancements().stopListening();
        this.players.remove(player);
        this.realPlayers.remove(player); // Leaves - replay api
        this.playersByName.remove(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        this.server.getCustomBossEvents().onPlayerDisconnect(player);
        UUID uuid = player.getUUID();
        ServerPlayer serverPlayer = this.playersByUUID.get(uuid);
        if (serverPlayer == player) {
            this.playersByUUID.remove(uuid);
            // CraftBukkit start
            // this.stats.remove(uuid);
            // this.advancements.remove(uuid);
            // CraftBukkit end
            this.server.notificationManager().playerLeft(player);
        }

        // CraftBukkit start
        // this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID()));
        for (ServerPlayer otherPlayer : this.players) { // Folia - region threading

            if (otherPlayer.getBukkitEntity().canSee(player.getBukkitEntity())) {
                otherPlayer.connection.send(packet);
            } else {
                // Mili fix - #406: schedule onEntityRemove on the player's region thread to avoid
                // concurrent modification of invertedVisibilityEntities (non-thread-safe HashMap)
                final ServerPlayer targetPlayer = otherPlayer;
                final ServerPlayer removedPlayer = player;
                targetPlayer.getBukkitEntity().taskScheduler.schedule(
                    (Entity p) -> targetPlayer.getBukkitEntity().onEntityRemove(removedPlayer),
                    null, 1L
                );
            }
        }
        // This removes the scoreboard (and player reference) for the specific player in the manager
        this.cserver.getScoreboardManager().removePlayer(player.getBukkitEntity());
        // CraftBukkit end
        return playerQuitEvent.quitMessage(); // Paper - Adventure
    }

    // Paper start - PlayerLoginEvent
    public record LoginResult(@Nullable Component message, org.bukkit.event.player.PlayerLoginEvent.Result result) {
        public static LoginResult ALLOW = new net.minecraft.server.players.PlayerList.LoginResult(null, org.bukkit.event.player.PlayerLoginEvent.Result.ALLOWED);

        public boolean isAllowed() {
            return this == ALLOW;
        }
    }
    // Paper end - PlayerLoginEvent
    public LoginResult canPlayerLogin(SocketAddress socketAddress, NameAndId nameAndId, Connection connection) { // Paper - PlayerLoginEvent // Folia - region threading - add connection parameter
        LoginResult whitelistEventResult; // Paper
        // Paper start - Fix MC-158900
        UserBanListEntry userBanListEntry;
        if (this.bans.isBanned(nameAndId) && (userBanListEntry = this.bans.get(nameAndId)) != null) {
            // Paper end - Fix MC-158900
            MutableComponent mutableComponent = Component.translatable("multiplayer.disconnect.banned.reason", userBanListEntry.getReasonMessage());
            if (userBanListEntry.getExpires() != null) {
                mutableComponent.append(
                    Component.translatable("multiplayer.disconnect.banned.expiration", BAN_DATE_FORMAT.get().format(userBanListEntry.getExpires())) // Folia - region threading - SDF is not thread-safe
                );
            }

            return new LoginResult(mutableComponent, org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED); // Paper - PlayerLoginEvent
            // Paper start - whitelist event
        } else if ((whitelistEventResult = this.isWhiteListedLogin(nameAndId)).result == org.bukkit.event.player.PlayerLoginEvent.Result.KICK_WHITELIST) {
            return whitelistEventResult;
            // Paper end
        } else if (this.ipBans.isBanned(socketAddress)) {
            IpBanListEntry ipBanListEntry = this.ipBans.get(socketAddress);
            MutableComponent mutableComponent = Component.translatable("multiplayer.disconnect.banned_ip.reason", ipBanListEntry.getReasonMessage());
            if (ipBanListEntry.getExpires() != null) {
                mutableComponent.append(
                    Component.translatable("multiplayer.disconnect.banned_ip.expiration", BAN_DATE_FORMAT.get().format(ipBanListEntry.getExpires())) // Folia - region threading - SDF is not thread-safe
                );
            }

            return new LoginResult(mutableComponent, org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED); // Paper - PlayerLoginEvent
        } else {
            return this.canBypassFullServerLogin(nameAndId, new LoginResult(Component.translatable("multiplayer.disconnect.server_full"), org.bukkit.event.player.PlayerLoginEvent.Result.KICK_FULL), connection); // Paper - PlayerServerFullCheckEvent // Folia - region threading - add connection parameter
        }
    }

    public boolean disconnectAllPlayersWithProfile(GameProfile profile) { // Paper - validate usernames
        UUID profileId = profile.id(); // Paper - validate usernames
        Set<ServerPlayer> set = Sets.newIdentityHashSet();

        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.getUUID().equals(profileId) || (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode() && serverPlayer.getGameProfile().name().equalsIgnoreCase(profile.name()))) { // Paper - validate usernames
                set.add(serverPlayer);
            }
        }

        ServerPlayer serverPlayer1 = this.playersByUUID.get(profileId);
        if (serverPlayer1 != null) {
            set.add(serverPlayer1);
        }

        // Folia - region threading - rewrite login process - moved to pushPendingJoin

        return !set.isEmpty();
    }

    // Paper start - respawn event
    public ServerPlayer respawn(ServerPlayer player, boolean keepInventory, Entity.RemovalReason reason, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason respawnReason) {
        // Folia start - region threading
        if (true) {
            throw new UnsupportedOperationException("Must use teleportAsync while in region threading");
        }
        // Folia end - region threading
        ServerPlayer.RespawnResult result = player.findRespawnPositionAndUseSpawnBlock0(!keepInventory, TeleportTransition.DO_NOTHING, respawnReason);
        if (result == null) { // disconnected player during the respawn event
            return player;
        }
        TeleportTransition teleportTransition = result.transition();
        Level fromLevel = player.level();
        // Paper end - respawn event
        this.players.remove(player);
        this.playersByName.remove(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Paper
        player.level().removePlayerImmediately(player, reason);
        ServerLevel level = teleportTransition.newLevel();
        ServerPlayer serverPlayer = player; // Paper - TODO - recreate instance
        serverPlayer.connection = player.connection;
        serverPlayer.restoreFrom(player, keepInventory);
        serverPlayer.setId(player.getId());
        serverPlayer.setMainArm(player.getMainArm());
        if (false && !teleportTransition.missingRespawnBlock()) { // Paper - Once we not reuse the player entity, this can be flipped again but without the events being fired
            serverPlayer.copyRespawnPosition(player);
        }

        for (String string : player.getTags()) {
            serverPlayer.addTag(string);
        }

        // Paper start - Once we not reuse the player entity we can remove this.
        if (!keepInventory) player.reset();
        serverPlayer.spawnIn(level);
        serverPlayer.unsetRemoved();
        serverPlayer.setShiftKeyDown(false);
        // Paper end
        Vec3 vec3 = teleportTransition.position();
        serverPlayer.snapTo(vec3.x, vec3.y, vec3.z, teleportTransition.yRot(), teleportTransition.xRot());
        serverPlayer.connection.resetPosition(); // Paper - Fix SPIGOT-1903, MC-98153
        level.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.POST_TELEPORT, new net.minecraft.world.level.ChunkPos(net.minecraft.util.Mth.floor(vec3.x()) >> 4, net.minecraft.util.Mth.floor(vec3.z()) >> 4), 1); // Paper - post teleport ticket type
        if (teleportTransition.missingRespawnBlock()) {
            serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            serverPlayer.setRespawnPosition(null, false, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN); // CraftBukkit - SPIGOT-5988: Clear respawn location when obstructed
        }

        byte b = keepInventory ? ClientboundRespawnPacket.KEEP_ATTRIBUTE_MODIFIERS : 0;
        ServerLevel serverLevel = serverPlayer.level();
        LevelData levelData = serverLevel.getLevelData();
        serverPlayer.connection.send(new ClientboundRespawnPacket(serverPlayer.createCommonSpawnInfo(serverLevel), b));
        serverPlayer.connection.internalTeleport(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot()); // Paper
        serverPlayer.connection.send(new ClientboundSetDefaultSpawnPositionPacket(level.getRespawnData()));
        serverPlayer.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
        serverPlayer.connection
            .send(new ClientboundSetExperiencePacket(serverPlayer.experienceProgress, serverPlayer.totalExperience, serverPlayer.experienceLevel));
        this.sendActivePlayerEffects(serverPlayer);
        this.sendLevelInfo(serverPlayer, level);
        this.sendPlayerPermissionLevel(serverPlayer);
        level.addRespawnedPlayer(serverPlayer);
        this.players.add(serverPlayer);
        this.playersByName.put(serverPlayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT), serverPlayer); // Paper
        this.playersByUUID.put(serverPlayer.getUUID(), serverPlayer);
        serverPlayer.initInventoryMenu();
        serverPlayer.setHealth(serverPlayer.getHealth());
        // Paper start - Once we not reuse the player entity we can remove this.
        // But we have to resend the player info as it's not marked as dirty
        this.sendAllPlayerInfo(player); // Update health
        player.onUpdateAbilities(); // Update inventory, etc
        // Paper end
        ServerPlayer.RespawnConfig respawnConfig = serverPlayer.getRespawnConfig();
        if (!keepInventory && respawnConfig != null) {
            LevelData.RespawnData respawnData = respawnConfig.respawnData();
            ServerLevel level1 = this.server.getLevel(respawnData.dimension());
            if (level1 != null) {
                BlockPos blockPos = respawnData.pos();
                BlockState blockState = level1.getBlockState(blockPos);
                if (blockState.is(Blocks.RESPAWN_ANCHOR)) {
                    serverPlayer.connection
                        .send(
                            new ClientboundSoundPacket(
                                SoundEvents.RESPAWN_ANCHOR_DEPLETE,
                                SoundSource.BLOCKS,
                                blockPos.getX(),
                                blockPos.getY(),
                                blockPos.getZ(),
                                1.0F,
                                1.0F,
                                level.getRandom().nextLong()
                            )
                        );
                }
            }
        }

        // Paper start
        // Save player file again if they were disconnected
        if (serverPlayer.connection.isDisconnected()) {
            this.save(serverPlayer);
        }

        // It's possible for respawn to be in a diff dimension
        if (fromLevel != level) {
            new org.bukkit.event.player.PlayerChangedWorldEvent(serverPlayer.getBukkitEntity(), fromLevel.getWorld()).callEvent();
            // Mili start - nether portal fix
            if (fun.bm.mili.config.modules.function.OldFeatureConfig.netherPortalFix) {
                final ResourceKey<Level> fromDim = player.level().dimension();
                final ResourceKey<Level> toDim = serverPlayer.level().dimension();
                final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
                final ResourceKey<Level> THE_NETHER = Level.NETHER;
                if (!((fromDim != OVERWORLD || toDim != THE_NETHER) && (fromDim != THE_NETHER || toDim != OVERWORLD))) {
                    BlockPos fromPortal = fun.bm.mili.utils.ReturnPortalManager.findPortalAt(serverPlayer, fromDim, serverPlayer.lastPos);
                    if (fromPortal != null) {
                        fun.bm.mili.utils.ReturnPortalManager.storeReturnPortal(serverPlayer, toDim, serverPlayer.blockPosition(), fromPortal);
                    }
                }
            }
            // Mili end - nether portal fix
            serverPlayer.triggerDimensionChangeTriggers(level);
        }

        // Call post respawn event
        new com.destroystokyo.paper.event.player.PlayerPostRespawnEvent(
            serverPlayer.getBukkitEntity(),
            org.bukkit.craftbukkit.util.CraftLocation.toBukkit(teleportTransition.position(), level, teleportTransition.yRot(), teleportTransition.xRot()),
            result.isBedSpawn(),
            result.isAnchorSpawn(),
            teleportTransition.missingRespawnBlock(),
            respawnReason
        ).callEvent();
        // Paper end

        // Leaves start - bot support
        if (fun.bm.mili.config.modules.function.FakeplayerConfig.enable) {
            this.server.getBotList().bots.forEach(bot -> bot.sendFakeDataIfNeed(serverPlayer, true)); // Leaves - render bot
        }
        // Leaves end - bot support

        return serverPlayer;
    }

    public void sendActivePlayerEffects(ServerPlayer player) {
        this.sendActiveEffects(player, player.connection);
    }

    public void sendActiveEffects(LivingEntity entity, ServerGamePacketListenerImpl connection) {
        // Paper start - collect packets
        this.sendActiveEffects(entity, connection::send);
    }
    public void sendActiveEffects(LivingEntity entity, java.util.function.Consumer<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> packetConsumer) {
        // Paper end - collect packets
        for (MobEffectInstance mobEffectInstance : entity.getActiveEffects()) {
            packetConsumer.accept(new ClientboundUpdateMobEffectPacket(entity.getId(), mobEffectInstance, false)); // Paper - collect packets
        }
    }

    public void sendPlayerPermissionLevel(ServerPlayer player) {
        // Paper start - avoid recalculating permissions if possible
        this.sendPlayerPermissionLevel(player, true);
    }

    public void sendPlayerPermissionLevel(ServerPlayer player, boolean recalculatePermissions) {
        // Paper end - avoid recalculating permissions if possible
        LevelBasedPermissionSet profilePermissions = this.server.getProfilePermissions(player.nameAndId());
        this.sendPlayerPermissionLevel(player, profilePermissions, recalculatePermissions); // Paper - avoid recalculating permissions if possible
    }

    public void tick() {
        if (++this.sendAllPlayerInfoIn > 600) {
            // CraftBukkit start
            ServerPlayer[] players = this.players.toArray(new ServerPlayer[0]); // Folia - region threading
            for (final ServerPlayer target : players) { // Folia - region threading

                target.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), com.google.common.collect.Collections2.filter(java.util.Arrays.asList(players),t -> target.getBukkitEntity().canSee(t.getBukkitEntity())))); // Folia - region threading
            }
            // CraftBukkit end
            this.sendAllPlayerInfoIn = 0;
        }
    }

    // CraftBukkit start - add a world/entity limited version
    public void broadcastAll(Packet packet, net.minecraft.world.entity.player.Player entityhuman) {
        for (ServerPlayer entityplayer : this.players) { // Paper - replace for i with for each for thread safety
            if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                continue;
            }
            ((ServerPlayer) entityplayer).connection.send(packet); // Paper - replace for i with for each for thread safety
        }
    }

    public void broadcastAll(Packet packet, Level world) {
        for (net.minecraft.world.entity.player.Player player : world.players()) { // Folia - region threading
            ((ServerPlayer) player).connection.send(packet); // Folia - region threading
        }

    }
    // CraftBukkit end

    public void broadcastAll(Packet<?> packet) {
        for (ServerPlayer serverPlayer : this.players) {
            serverPlayer.connection.send(packet);
        }
    }

    public void broadcastAll(Packet<?> packet, ResourceKey<Level> dimension) {
        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.level().dimension() == dimension) {
                serverPlayer.connection.send(packet);
            }
        }
    }

    public void broadcastSystemToTeam(Player player, Component message) {
        Team team = player.getTeam();
        if (team != null) {
            for (String string : team.getPlayers()) {
                ServerPlayer playerByName = this.getPlayerByName(string);
                if (playerByName != null && playerByName != player) {
                    playerByName.sendSystemMessage(message);
                }
            }
        }
    }

    public void broadcastSystemToAllExceptTeam(Player player, Component message) {
        Team team = player.getTeam();
        if (team == null) {
            this.broadcastSystemMessage(message, false);
        } else {
            for (ServerPlayer serverPlayer : this.players) { // Folia - region threading
                if (serverPlayer.getTeam() != team) {
                    serverPlayer.sendSystemMessage(message);
                }
            }
        }
    }

    public String[] getPlayerNamesArray() {
        List<ServerPlayer> players = new java.util.ArrayList<>(this.realPlayers); // Folia - region threading
        String[] strings = new String[players.size() + this.server.getBotList().bots.size()]; // Leaves - fakeplayer support, and skip photographer

        for (int i = 0; i < players.size(); i++) { // Folia - region threading
            strings[i] = players.get(i).getGameProfile().name(); // Folia - region threading
        }
        // Leaves start - fakeplayer support
        for (int i = players.size(); i < strings.length; ++i) { // Leaves - only real players
            strings[i] = this.server.getBotList().bots.get(i - players.size()).getGameProfile().name(); // Leaves - only real players
        }
        // Leaves end - fakeplayer support

        return strings;
    }

    public UserBanList getBans() {
        return this.bans;
    }

    public IpBanList getIpBans() {
        return this.ipBans;
    }

    public void op(NameAndId nameAndId) {
        this.op(nameAndId, Optional.empty(), Optional.empty());
    }

    public void op(NameAndId nameAndId, Optional<LevelBasedPermissionSet> permissions, Optional<Boolean> bypassesPlayerLimit) {
        this.ops
            .add(
                new ServerOpListEntry(
                    nameAndId, permissions.orElse(this.server.operatorUserPermissions()), bypassesPlayerLimit.orElse(this.ops.canBypassPlayerLimit(nameAndId))
                )
            );
        ServerPlayer player = this.getPlayer(nameAndId.id());
        if (player != null) {
            player.getBukkitEntity().taskScheduler.schedule((ServerPlayer serverPlayer) -> { // Folia - region threading
            this.sendPlayerPermissionLevel(serverPlayer); // Folia - region threading
            }, null, 1L); // Folia - region threading
        }
    }

    public void deop(NameAndId nameAndId) {
        if (this.ops.remove(nameAndId)) {
            ServerPlayer player = this.getPlayer(nameAndId.id());
            if (player != null) {
                // Folia start - region threading
                player.getBukkitEntity().taskScheduler.schedule((ServerPlayer serverPlayer) -> {
                    this.sendPlayerPermissionLevel(serverPlayer);
                }, null, 1L);
                // Folia end - region threading
            }
        }
    }

    private void sendPlayerPermissionLevel(ServerPlayer player, LevelBasedPermissionSet permissions) {
        // Paper start - Add sendOpLevel API
        this.sendPlayerPermissionLevel(player, permissions, true);
    }

    public void sendPlayerPermissionLevel(ServerPlayer player, LevelBasedPermissionSet permissions, boolean recalculatePermissions) {
        // Paper end - Add sendOpLevel API
        if (player.connection != null) {
            byte b = switch (permissions.level()) {
                case ALL -> EntityEvent.PERMISSION_LEVEL_ALL;
                case MODERATORS -> EntityEvent.PERMISSION_LEVEL_MODERATORS;
                case GAMEMASTERS -> EntityEvent.PERMISSION_LEVEL_GAMEMASTERS;
                case ADMINS -> EntityEvent.PERMISSION_LEVEL_ADMINS;
                case OWNERS -> EntityEvent.PERMISSION_LEVEL_OWNERS;
            };
            player.connection.send(new ClientboundEntityEventPacket(player, b));
        }

        if (recalculatePermissions) { // Paper - Add sendOpLevel API
        player.getBukkitEntity().recalculatePermissions(); // CraftBukkit
        this.server.getCommands().sendCommands(player);
        } // Paper - Add sendOpLevel API
    }

    // Paper start - whitelist verify event / login event
    public LoginResult canBypassFullServerLogin(final NameAndId nameAndId, final LoginResult currentResult, final Connection connection) { // Folia - region threading - add connection parameter
        final boolean shouldKick = !this.countConnection(connection, this.getMaxPlayers()) && !this.canBypassPlayerLimit(nameAndId); // Folia - region threading - we control connection state here now async, not player list size
        final io.papermc.paper.event.player.PlayerServerFullCheckEvent fullCheckEvent = new io.papermc.paper.event.player.PlayerServerFullCheckEvent(
            new com.destroystokyo.paper.profile.CraftPlayerProfile(nameAndId),
            io.papermc.paper.adventure.PaperAdventure.asAdventure(currentResult.message),
            shouldKick
        );

        fullCheckEvent.callEvent();
        if (fullCheckEvent.isAllowed()) {
            return net.minecraft.server.players.PlayerList.LoginResult.ALLOW;
        } else {
            return new net.minecraft.server.players.PlayerList.LoginResult(
                io.papermc.paper.adventure.PaperAdventure.asVanilla(fullCheckEvent.kickMessage()), currentResult.result
            );
        }
    }

    public LoginResult isWhiteListedLogin(NameAndId nameAndId) {
        boolean isOp = this.ops.contains(nameAndId);
        boolean isWhitelisted = !this.isUsingWhitelist() || isOp || this.whitelist.contains(nameAndId);

        final net.kyori.adventure.text.Component configuredMessage = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.whitelistMessage);
        final com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent event
            = new com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent(new com.destroystokyo.paper.profile.CraftPlayerProfile(nameAndId), this.isUsingWhitelist(), isWhitelisted, isOp, configuredMessage);
        event.callEvent();
        if (!event.isWhitelisted()) {
            return new net.minecraft.server.players.PlayerList.LoginResult(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.kickMessage() == null ? configuredMessage : event.kickMessage()), org.bukkit.event.player.PlayerLoginEvent.Result.KICK_WHITELIST);
        }

        return net.minecraft.server.players.PlayerList.LoginResult.ALLOW;
    }
    // Paper end

    @io.papermc.paper.annotation.DoNotUse // Paper
    public boolean isWhiteListed(NameAndId nameAndId) {
        return !this.isUsingWhitelist() || this.ops.contains(nameAndId) || this.whitelist.contains(nameAndId);
    }

    public boolean isOp(NameAndId nameAndId) {
        return this.ops.contains(nameAndId)
            || this.server.isSingleplayerOwner(nameAndId) && this.server.getWorldData().isAllowCommands()
            || this.allowCommandsForAllPlayers;
    }

    public @Nullable ServerPlayer getPlayerByName(String username) {
        // Leaves start - fakeplayer support
        username = username.toLowerCase(java.util.Locale.ROOT);
        ServerPlayer player = this.playersByName.get(username);
        if (player == null) {
            player = this.server.getBotList().getBotByName(username);
        }
        return player; // Spigot
        // Leaves end - fakeplayer support
    }

    public void broadcast(@Nullable Player except, double x, double y, double z, double radius, ResourceKey<Level> dimension, Packet<?> packet) {
        for (ServerPlayer serverPlayer : this.players) { // Folia - region threading
            // CraftBukkit start - Test if player receiving packet can see the source of the packet
            if (except != null && !serverPlayer.getBukkitEntity().canSee(except.getBukkitEntity())) {
               continue;
            }
            // CraftBukkit end
            if (serverPlayer != except && serverPlayer.level().dimension() == dimension) {
                double d = x - serverPlayer.getX();
                double d1 = y - serverPlayer.getY();
                double d2 = z - serverPlayer.getZ();
                if (d * d + d1 * d1 + d2 * d2 < radius * radius) {
                    serverPlayer.connection.send(packet);
                }
            }
        }
    }

    public void saveAll() {
        // Paper start - Incremental chunk and player saving
        this.saveAll(-1);
    }

    public void saveAll(final int interval) {
        io.papermc.paper.util.MCUtil.ensureMain("Save Players" , () -> { // Paper - Ensure main
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        int numSaved = 0;
        final long now = System.nanoTime(); // Folia - region threading
        long timeInterval = (long)interval * io.papermc.paper.threadedregions.TickRegionScheduler.TIME_BETWEEN_TICKS; // Folia - region threading
        for (final ServerPlayer player : this.players) { // Folia - region threading
            // Folia start - region threading
            if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player)) {
                continue;
            }
            // Folia end - region threading
            if (interval == -1 || now - player.lastSave >= timeInterval) { // Folia - region threading
                profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_SAVE); try { // Folia - profiler
                this.save(player);
                } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_SAVE); } // Folia - profiler
                if (interval != -1 && ++numSaved >= io.papermc.paper.configuration.GlobalConfiguration.get().playerAutoSave.maxPerTick()) {
                    break;
                }
            }
            // Paper end - Incremental chunk and player saving
        }
        return null; }); // Paper - ensure main
    }

    public UserWhiteList getWhiteList() {
        return this.whitelist;
    }

    public String[] getWhiteListNames() {
        return this.whitelist.getUserList();
    }

    public ServerOpList getOps() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getUserList();
    }

    public void reloadWhiteList() {
    }

    public void sendLevelInfo(ServerPlayer player, ServerLevel level) {
        WorldBorder worldBorder = level.getWorldBorder();
        player.connection.send(new ClientboundInitializeBorderPacket(worldBorder));
        player.connection.send(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().get(GameRules.ADVANCE_TIME)));
        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(level.getRespawnData()));
        // Paper start - view distances
        //player.connection.send(new ClientboundSetChunkCacheRadiusPacket(io.papermc.paper.FeatureHooks.getViewDistance(level))); // Paper - rewrite chunk system
        //player.connection.send(new ClientboundSetSimulationDistancePacket(io.papermc.paper.FeatureHooks.getSimulationDistance(level))); // Paper - rewrite chunk system
        // Paper end - view distances
        org.leavesmc.leaves.protocol.XaeroMapProtocol.onSendWorldInfo(player); // Leaves - xaero map protocol
        if (level.isRaining()) {
            // CraftBukkit start - handle player weather
            // player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
            // player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1.0F)));
            // player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1.0F)));
            player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL, false);
            player.updateWeather(-level.rainLevel, level.rainLevel, -level.thunderLevel, level.thunderLevel);
            // CraftBukkit end
        }

        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
        this.server.tickRateManager().updateJoiningPlayer(player);
    }

    public void sendAllPlayerInfo(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
        // entityplayer.resetSentInfo();
        // Paper start - send all attributes
        // needs to be done because the ServerPlayer instance is being reused on respawn instead of getting replaced like on vanilla
        java.util.Collection<net.minecraft.world.entity.ai.attributes.AttributeInstance> syncableAttributes = player.getAttributes().getSyncableAttributes();
        player.getBukkitEntity().injectScaledMaxHealth(syncableAttributes, true);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket(player.getId(), syncableAttributes));
        // Paper end - send all attributes
        player.refreshEntityData(player); // CraftBukkit - SPIGOT-7218: sync metadata
        player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
        // CraftBukkit start - from GameRules
        int i = player.level().getGameRules().get(GameRules.REDUCED_DEBUG_INFO) ? 22 : 23;
        player.connection.send(new ClientboundEntityEventPacket(player, (byte) i));
        float immediateRespawn = player.level().getGameRules().get(GameRules.IMMEDIATE_RESPAWN) ? 1.0F: 0.0F;
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, immediateRespawn));
        // CraftBukkit end
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.server.getMaxPlayers();
    }

    public boolean isUsingWhitelist() {
        return this.server.isUsingWhitelist();
    }

    public List<ServerPlayer> getPlayersWithAddress(String address) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.getIpAddress().equals(address)) {
                list.add(serverPlayer);
            }
        }

        return list;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public int getSimulationDistance() {
        return this.simulationDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public @Nullable CompoundTag getSingleplayerData() {
        return null;
    }

    public void setAllowCommandsForAllPlayers(boolean allowCommandsForAllPlayers) {
        this.allowCommandsForAllPlayers = allowCommandsForAllPlayers;
    }

    public void removeAll() {
        // Paper start - Extract method to allow for restarting flag
        this.removeAll(false);
    }

    public void removeAll(boolean isRestarting) {
        // Folia start - region threading
        // just send disconnect packet, don't modify state
        for (ServerPlayer player : this.players) {
            final Component shutdownMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(this.server.server.shutdownMessage()); // Paper - Adventure
            // CraftBukkit end

            player.connection.send(new net.minecraft.network.protocol.common.ClientboundDisconnectPacket(shutdownMessage), net.minecraft.network.PacketSendListener.thenRun(() -> {
                player.connection.connection.disconnect(shutdownMessage);
            }));
        }
        if (true) {
            return;
        }
        // Folia end - region threading
        // Paper end
        // CraftBukkit start - disconnect safely
        for (ServerPlayer player : this.players) {
            if (isRestarting) player.connection.disconnect(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.restartMessage), org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN); else // Paper - kick event cause (cause is never used here)
            player.connection.disconnect(java.util.Objects.requireNonNullElseGet(this.server.server.shutdownMessage(), net.kyori.adventure.text.Component::empty)); // CraftBukkit - add custom shutdown message // Paper - Adventure
        }
        // CraftBukkit end

        // Paper start - Configurable player collision; Remove collideRule team if it exists
        if (false && this.collideRuleTeamName != null) { // Folia - region threading
            final net.minecraft.world.scores.Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreboard.getPlayersTeam(this.collideRuleTeamName);
            if (team != null) scoreboard.removePlayerTeam(team);
        }
        // Paper end - Configurable player collision
    }

    public void broadcastSystemMessage(Component message, boolean overlay) {
        this.broadcastSystemMessage(message, serverPlayer -> message, overlay);
    }

    public void broadcastSystemMessage(Component serverMessage, Function<ServerPlayer, Component> playerMessageFactory, boolean overlay) {
        this.server.sendSystemMessage(serverMessage);

        for (ServerPlayer serverPlayer : this.players) {
            Component component = playerMessageFactory.apply(serverPlayer);
            if (component != null) {
                serverPlayer.sendSystemMessage(component, overlay);
            }
        }
    }

    public void broadcastChatMessage(PlayerChatMessage message, CommandSourceStack sender, ChatType.Bound boundChatType) {
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender.getPlayer(), boundChatType);
    }

    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound boundChatType) {
        // Paper start
        this.broadcastChatMessage(message, sender, boundChatType, null);
    }
    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound boundChatType, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        // Paper end
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender, boundChatType, unsignedFunction); // Paper
    }

    private void broadcastChatMessage(
        PlayerChatMessage message, Predicate<ServerPlayer> shouldFilterMessageTo, @Nullable ServerPlayer sender, ChatType.Bound boundChatType
    ) {
        // Paper start
        this.broadcastChatMessage(message, shouldFilterMessageTo, sender, boundChatType, null);
    }
    public void broadcastChatMessage(PlayerChatMessage message, Predicate<ServerPlayer> shouldFilterMessageTo, @Nullable ServerPlayer sender, ChatType.Bound boundChatType, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        // Paper end
        boolean flag = this.verifyChatTrusted(message);
        this.server.logChatMessage((unsignedFunction == null ? message.decoratedContent() : unsignedFunction.apply(this.server.console)), boundChatType, flag ? null : "Not Secure"); // Paper
        OutgoingChatMessage outgoingChatMessage = OutgoingChatMessage.create(message);
        boolean flag1 = false;

        Packet<?> disguised = sender != null && unsignedFunction == null ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(outgoingChatMessage.content(), boundChatType) : null; // Paper - don't send player chat packets from vanished players
        for (ServerPlayer serverPlayer : this.players) {
            boolean flag2 = shouldFilterMessageTo.test(serverPlayer);
            // Paper start - don't send player chat packets from vanished players
            if (sender != null && !serverPlayer.getBukkitEntity().canSee(sender.getBukkitEntity())) {
                serverPlayer.connection.send(unsignedFunction != null
                    ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(unsignedFunction.apply(serverPlayer.getBukkitEntity()), boundChatType)
                    : disguised);
                continue;
            }
            serverPlayer.sendChatMessage(outgoingChatMessage, flag2, boundChatType, unsignedFunction == null ? null : unsignedFunction.apply(serverPlayer.getBukkitEntity()));
            // Paper end
            flag1 |= flag2 && message.isFullyFiltered();
        }

        if (flag1 && sender != null) {
            sender.sendSystemMessage(CHAT_FILTERED_FULL);
        }
    }

    public boolean verifyChatTrusted(PlayerChatMessage message) {
        return message.hasSignature() && !message.hasExpiredServer(Instant.now());
    }

    // CraftBukkit start
    public ServerStatsCounter getPlayerStats(ServerPlayer player) {
        GameProfile gameProfile = player.getGameProfile();
        ServerStatsCounter playerStatsCounter = player.getStats();
        if (playerStatsCounter == null) {
            return this.getPlayerStats(gameProfile);
        } else {
            return playerStatsCounter;
        }
    }
    public ServerStatsCounter getPlayerStats(GameProfile gameProfile) {
            Path path = this.locateStatsFile(gameProfile);
            return new ServerStatsCounter(this.server, path);
    }
    // CraftBukkit end

    private Path locateStatsFile(GameProfile profile) {
        Path worldPath = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
        Path path = worldPath.resolve(profile.id() + ".json");
        if (Files.exists(path)) {
            return path;
        } else {
            // Leaf start - Remove useless creating stats json bases on player name logic
            /*
            String string = profile.name() + ".json";
            if (FileUtil.isValidPathSegment(string)) {
                Path path1 = worldPath.resolve(string);
                if (Files.isRegularFile(path1)) {
                    try {
                        return Files.move(path1, path);
                    } catch (IOException var7) {
                        LOGGER.warn("Failed to copy file {} to {}", string, path);
                        return path1;
                    }
                }
            }
            */
            // Leaf end - Remove useless creating stats json bases on player name logic

            return path;
        }
    }

    public PlayerAdvancements getPlayerAdvancements(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerAdvancements playerAdvancements = player.getAdvancements(); // CraftBukkit
        if (playerAdvancements == null) {
            Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uuid + ".json");
            playerAdvancements = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, player);
            // this.advancements.put(uuid, playerAdvancements); // CraftBukkit
        }

        playerAdvancements.setPlayer(player);
        return playerAdvancements;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        //this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(viewDistance)); // Paper - rewrite chunk system

        for (ServerLevel serverLevel : this.server.getAllLevels()) {
            serverLevel.getChunkSource().setViewDistance(viewDistance);
        }
    }

    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
        //this.broadcastAll(new ClientboundSetSimulationDistancePacket(simulationDistance)); // Paper - rewrite chunk system

        for (ServerLevel serverLevel : this.server.getAllLevels()) {
            serverLevel.getChunkSource().setSimulationDistance(simulationDistance);
        }
    }

    public List<ServerPlayer> getPlayers() {
        return this.players;
    }

    public @Nullable ServerPlayer getPlayer(UUID playerUUID) {
        // Leaves start - fakeplayer support
        ServerPlayer player = this.playersByUUID.get(playerUUID);
        if (player == null) {
            player = this.server.getBotList().getBot(playerUUID);
        }
        return player;
        // Leaves start - fakeplayer support
    }

    public @Nullable ServerPlayer getPlayer(String name) {
        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.getGameProfile().name().equalsIgnoreCase(name)) {
                return serverPlayer;
            }
        }

        return null;
    }

    public boolean canBypassPlayerLimit(NameAndId nameAndId) {
        return false;
    }

    public void reloadResources() {
        // Paper start - API for updating recipes on clients
        this.reloadAdvancementData();
        this.reloadTagData();
        this.reloadRecipes();
        org.leavesmc.leaves.protocol.core.LeavesProtocolManager.handleDataPackReload(); // Leaves - protocol
    }
    public void reloadAdvancementData() {
        // Paper end - API for updating recipes on clients
        // CraftBukkit start
        // for (PlayerAdvancements playerAdvancements : this.advancements.values()) {
        //     playerAdvancements.reload(this.server.getAdvancements());
        // }
        for (ServerPlayer player : this.players) {
            player.getAdvancements().reload(this.server.getAdvancements());
            player.getAdvancements().flushDirty(player, false); // CraftBukkit - trigger immediate flush of advancements
        }
        // CraftBukkit end

        // Paper start - API for updating recipes on clients
    }
    public void reloadTagData() {
        this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
        // CraftBukkit start
        // this.reloadRecipes(); // Paper - do not reload recipes just because tag data was reloaded
        // Paper end - API for updating recipes on clients
    }

    public void reloadRecipes() {
        // CraftBukkit end
        RecipeManager recipeManager = this.server.getRecipeManager();
        ClientboundUpdateRecipesPacket clientboundUpdateRecipesPacket = new ClientboundUpdateRecipesPacket(
            recipeManager.getSynchronizedItemProperties(), recipeManager.getSynchronizedStonecutterRecipes()
        );

        for (ServerPlayer serverPlayer : this.players) {
            serverPlayer.connection.send(clientboundUpdateRecipesPacket);
            serverPlayer.getRecipeBook().sendInitialRecipeBook(serverPlayer);
        }
    }

    public boolean isAllowCommandsForAllPlayers() {
        return this.allowCommandsForAllPlayers;
    }
}
