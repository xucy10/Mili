package net.minecraft.server.network.config;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PrepareSpawnTask implements ConfigurationTask {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("prepare_spawn");
    public static final int PREPARE_CHUNK_RADIUS = 3;
    final MinecraftServer server;
    final NameAndId nameAndId;
    final LevelLoadListener loadListener;
    private PrepareSpawnTask.@Nullable State state;

    // Paper start - passthrough profile and packet listener
    private final com.mojang.authlib.GameProfile profile;
    private final net.minecraft.server.network.ServerConfigurationPacketListenerImpl listener;
    private boolean newPlayer;
    public PrepareSpawnTask(MinecraftServer server, com.mojang.authlib.GameProfile profile, net.minecraft.server.network.ServerConfigurationPacketListenerImpl listener) {
        this.profile = profile;
        this.listener = listener;
    // Paper end - passthrough profile and packet listener
        this.server = server;
        this.nameAndId = new net.minecraft.server.players.NameAndId(profile); // Paper - passthrough profile and packet listener - create from profile
        this.loadListener = LevelLoadListener.noop(); // Paper - per level load listener - this is already a no-op on dedicated server, but we moved it to Level
    }

    @Override
    public void start(Consumer<Packet<?>> task) {
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
            Optional<ValueInput> optional = this.server
                .getPlayerList()
                .loadPlayerData(this.nameAndId)
                .map(compoundTag -> TagValueInput.create(scopedCollector, this.server.registryAccess(), compoundTag));
            // Paper start - move logic in Entity to here, to use bukkit supplied world UUID & reset to main world spawn if no valid world is found
            this.newPlayer = optional.isEmpty(); // New players don't have saved data!
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> resourceKey = null; // Paper
            boolean[] invalidPlayerWorld = {false};
            bukkitData: if (optional.isPresent()) {
                // The main way for bukkit worlds to store the world is the world UUID despite mojang adding custom worlds
                final org.bukkit.World bWorld;
                final ValueInput playerData = optional.get();
                // TODO maybe convert this to a codec and use compoundTag#read, we need silent variants of that method first.
                final Optional<Long> worldUUIDMost = playerData.getLong("WorldUUIDMost");
                final Optional<Long> worldUUIDLeast = playerData.getLong("WorldUUIDLeast");
                final java.util.Optional<String> worldName = playerData.getString("world");
                if (worldUUIDMost.isPresent() && worldUUIDLeast.isPresent()) {
                    bWorld = org.bukkit.Bukkit.getServer().getWorld(new java.util.UUID(worldUUIDMost.get(), worldUUIDLeast.get()));
                } else if (worldName.isPresent()) { // Paper - legacy bukkit world name
                    bWorld = org.bukkit.Bukkit.getServer().getWorld(worldName.get());
                } else {
                    break bukkitData; // if neither of the bukkit data points exist, proceed to the vanilla migration section
                }
                if (bWorld != null) {
                    resourceKey = ((org.bukkit.craftbukkit.CraftWorld) bWorld).getHandle().dimension();
                } else {
                    resourceKey = net.minecraft.world.level.Level.OVERWORLD;
                    invalidPlayerWorld[0] = true;
                }
            }
            ServerPlayer.SavedPosition savedPosition = optional.<ServerPlayer.SavedPosition>flatMap(
                    valueInput -> valueInput.read(ServerPlayer.SavedPosition.MAP_CODEC)
                )
                .orElse(ServerPlayer.SavedPosition.EMPTY);
            LevelData.RespawnData respawnData = this.server.getWorldData().overworldData().getRespawnData();
            if (resourceKey == null) { // only run the vanilla logic if we haven't found a world from the bukkit data
                // Below is the vanilla way of getting the dimension, this is for migration from vanilla servers
                resourceKey = savedPosition.dimension().orElse(null);
            }
            ServerLevel vanillaDefaultLevel = this.server.getLevel(respawnData.dimension());
            if (vanillaDefaultLevel == null) {
                vanillaDefaultLevel = this.server.overworld();
            }
            ServerLevel serverLevel1;
            if (resourceKey == null) {
                serverLevel1 = vanillaDefaultLevel;
            } else {
                serverLevel1 = this.server.getLevel(resourceKey);
                if (serverLevel1 == null) {
                    LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourceKey);
                    serverLevel1 = vanillaDefaultLevel;
                }
            }
            final ServerLevel serverLevel = serverLevel1;
            // Paper end - move logic in Entity to here, to use bukkit supplied world UUID & reset to main world spawn if no valid world is found
            // Folia start - region threading
            CompletableFuture<Vec3> completableFuture = new java.util.concurrent.CompletableFuture<>();
            if (savedPosition.position().isPresent()) {
                completableFuture.complete(savedPosition.position().get());
            } else {
                ca.spottedleaf.concurrentutil.completable.CallbackCompletable<org.bukkit.Location> spawnComplete = new ca.spottedleaf.concurrentutil.completable.CallbackCompletable<>();
                spawnComplete.addWaiter(
                        (final org.bukkit.Location loc, final Throwable throwable) -> {
                            if (throwable != null) {
                                completableFuture.completeExceptionally(throwable);
                            } else {
                                completableFuture.complete(io.papermc.paper.util.MCUtil.toVec3(loc));
                            }
                        }
                );
                ServerPlayer.fudgeSpawnLocation(serverLevel, spawnComplete);
            }
            // Folia end - region threading
            Vec2 vec2 = savedPosition.rotation().orElse(new Vec2(respawnData.yaw(), respawnData.pitch()));
            this.state = new PrepareSpawnTask.Preparing(serverLevel, completableFuture, vec2);
        }
    }

    @Override
    public boolean tick() {
        return switch (this.state) {
            case null -> false;
            case PrepareSpawnTask.Preparing preparing -> {
                PrepareSpawnTask.Ready ready = preparing.tick();
                if (ready != null) {
                    this.state = ready;
                    yield true;
                } else {
                    yield false;
                }
            }
            case PrepareSpawnTask.Ready ready -> true;
            default -> throw new MatchException(null, null);
        };
    }

    // Folia start - region threading
    public ServerLevel getSpawnWorld() {
        if (this.state instanceof PrepareSpawnTask.Ready ready) {
            return ready.getSpawnWorld();
        } else {
            throw new IllegalStateException("Player spawn was not ready");
        }
    }

    public Vec3 getSpawnPosition() {
        if (this.state instanceof PrepareSpawnTask.Ready ready) {
            return ready.getSpawnPosition();
        } else {
            throw new IllegalStateException("Player spawn was not ready");
        }
    }

    public ServerPlayer createPlayer(Connection connection, CommonListenerCookie cookie) {
        if (this.state instanceof PrepareSpawnTask.Ready ready) {
            return ready.createPlayer(connection, cookie);
        } else {
            throw new IllegalStateException("Player spawn was not ready");
        }
    }
    // Folia end - region threading

    public ServerPlayer spawnPlayer(Connection connection, CommonListenerCookie cookie, ServerPlayer serverPlayer) { // Folia - region threading
        if (this.state instanceof PrepareSpawnTask.Ready ready) {
            return ready.spawn(connection, cookie, serverPlayer); // Folia - region threading
        } else {
            throw new IllegalStateException("Player spawn was not ready");
        }
    }

    public void keepAlive() {
        if (this.state instanceof PrepareSpawnTask.Ready ready) {
            ready.keepAlive();
        }
    }

    public void close() {
        if (this.state instanceof PrepareSpawnTask.Preparing preparing) {
            preparing.cancel();
        }

        this.state = null;
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }

    final class Preparing implements PrepareSpawnTask.State {
        private ServerLevel spawnLevel; // Paper - remove final
        private CompletableFuture<Vec3> spawnPosition; // Paper - remove final
        private Vec2 spawnAngle; // Paper - remove final
        private @Nullable CompletableFuture<?> chunkLoadFuture;
        private @Nullable CompletableFuture<org.bukkit.Location> eventFuture; // Paper
        private final ChunkLoadCounter chunkLoadCounter = new ca.spottedleaf.moonrise.patches.chunk_system.MoonriseChunkLoadCounter(); // Paper - rewrite chunk system

        Preparing(final ServerLevel spawnLevel, final CompletableFuture<Vec3> spawnPosition, final Vec2 spawnAngle) {
            this.spawnLevel = spawnLevel;
            this.spawnPosition = spawnPosition;
            this.spawnAngle = spawnAngle;
        }

        public void cancel() {
            this.spawnPosition.cancel(false);
        }

        public PrepareSpawnTask.@Nullable Ready tick() {
            if (!this.spawnPosition.isDone()) {
                return null;
            } else {
                Vec3 vec3 = this.spawnPosition.join();
                if (this.chunkLoadFuture == null) {
                    // Paper start - PlayerSpawnLocationEvent
                    if (false && this.eventFuture == null && org.spigotmc.event.player.PlayerSpawnLocationEvent.getHandlerList().getRegisteredListeners().length != 0) { // Folia - region threading
                        ServerPlayer serverPlayer;
                        if (PrepareSpawnTask.this.listener.connection.savedPlayerForLegacyEvents != null) {
                            serverPlayer = PrepareSpawnTask.this.listener.connection.savedPlayerForLegacyEvents;
                        } else {
                            serverPlayer = new ServerPlayer(
                                PrepareSpawnTask.this.server, PrepareSpawnTask.this.server.overworld(), PrepareSpawnTask.this.profile, net.minecraft.server.level.ClientInformation.createDefault());
                            PrepareSpawnTask.this.listener.connection.savedPlayerForLegacyEvents = serverPlayer;
                        }
                        org.spigotmc.event.player.PlayerSpawnLocationEvent ev = new org.spigotmc.event.player.PlayerSpawnLocationEvent(
                            serverPlayer.getBukkitEntity(),
                            org.bukkit.craftbukkit.util.CraftLocation.toBukkit(vec3, this.spawnLevel, this.spawnAngle.x, this.spawnAngle.y)
                        );
                        ev.callEvent();
                        vec3 = io.papermc.paper.util.MCUtil.toVec3(ev.getSpawnLocation());
                        this.spawnLevel = ((org.bukkit.craftbukkit.CraftWorld) ev.getSpawnLocation().getWorld()).getHandle();
                        this.spawnPosition = CompletableFuture.completedFuture(vec3);
                        this.spawnAngle = new Vec2(ev.getSpawnLocation().getYaw(), ev.getSpawnLocation().getPitch());
                    }

                    if (this.eventFuture == null && io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent.getHandlerList().getRegisteredListeners().length != 0) {
                        final Vec3 vec3final = vec3;
                        this.eventFuture = CompletableFuture.supplyAsync(() -> {
                            io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent ev = new io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent(
                                PrepareSpawnTask.this.listener.paperConnection,
                                org.bukkit.craftbukkit.util.CraftLocation.toBukkit(vec3final, this.spawnLevel, this.spawnAngle.x, this.spawnAngle.y),
                                PrepareSpawnTask.this.newPlayer
                            );
                            ev.callEvent();
                            return ev.getSpawnLocation();
                        }, io.papermc.paper.connection.PaperConfigurationTask.CONFIGURATION_POOL);
                    }
                    if (this.eventFuture != null) {
                        if (!this.eventFuture.isDone()) {
                            return null;
                        }
                        org.bukkit.Location location = this.eventFuture.join();
                        vec3 = io.papermc.paper.util.MCUtil.toVec3(location);
                        this.spawnLevel = ((org.bukkit.craftbukkit.CraftWorld) location.getWorld()).getHandle();
                        this.spawnPosition = CompletableFuture.completedFuture(vec3);
                        this.spawnAngle = new Vec2(location.getYaw(), location.getPitch());
                    }
                    // Paper end - PlayerSpawnLocationEvent
                    ChunkPos chunkPos = new ChunkPos(BlockPos.containing(vec3));
                    this.chunkLoadFuture = ((ca.spottedleaf.moonrise.patches.chunk_system.MoonriseChunkLoadCounter)this.chunkLoadCounter).trackLoadWithRadius(this.spawnLevel, chunkPos, 3, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, ca.spottedleaf.concurrentutil.util.Priority.HIGH, () -> { Preparing.this.spawnLevel.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SPAWN, chunkPos, 3); });
                    PrepareSpawnTask.this.loadListener.start(LevelLoadListener.Stage.LOAD_PLAYER_CHUNKS, this.chunkLoadCounter.totalChunks());
                    PrepareSpawnTask.this.loadListener.updateFocus(this.spawnLevel.dimension(), chunkPos);
                }

                PrepareSpawnTask.this.loadListener
                    .update(LevelLoadListener.Stage.LOAD_PLAYER_CHUNKS, this.chunkLoadCounter.readyChunks(), this.chunkLoadCounter.totalChunks());
                if (!this.chunkLoadFuture.isDone()) {
                    return null;
                } else {
                    PrepareSpawnTask.this.loadListener.finish(LevelLoadListener.Stage.LOAD_PLAYER_CHUNKS);
                    return PrepareSpawnTask.this.new Ready(this.spawnLevel, vec3, this.spawnAngle);
                }
            }
        }
    }

    final class Ready implements PrepareSpawnTask.State {
        private final ServerLevel spawnLevel;
        private final Vec3 spawnPosition;
        private final Vec2 spawnAngle;

        Ready(final ServerLevel spawnLevel, final Vec3 spawnPosition, final Vec2 spawnAngle) {
            this.spawnLevel = spawnLevel;
            this.spawnPosition = spawnPosition;
            this.spawnAngle = spawnAngle;
        }

        public void keepAlive() {
            this.spawnLevel.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SPAWN, new ChunkPos(BlockPos.containing(this.spawnPosition)), 3);
        }

        // Folia start - region threading
        public ServerLevel getSpawnWorld() {
            return this.spawnLevel;
        }

        public Vec3 getSpawnPosition() {
            return this.spawnPosition;
        }

        public ServerPlayer createPlayer(Connection connection, CommonListenerCookie cookie) {
            //ChunkPos chunkPos = new ChunkPos(BlockPos.containing(this.spawnPosition));
            //this.spawnLevel.waitForEntities(chunkPos, 3);
            // Folia end - region threading
            // Paper start - configuration api - possibly use legacy saved server player instance
            ServerPlayer serverPlayer;
            if (connection.savedPlayerForLegacyEvents != null) {
                serverPlayer = connection.savedPlayerForLegacyEvents;
                connection.savedPlayerForLegacyEvents = null;
                // Update the existing instance
                serverPlayer.gameProfile = cookie.gameProfile();
                serverPlayer.updateOptionsNoEvents(cookie.clientInformation());
                serverPlayer.setServerLevel(this.spawnLevel);
            } else {
                serverPlayer = new ServerPlayer(PrepareSpawnTask.this.server, this.spawnLevel, cookie.gameProfile(), cookie.clientInformation());
            }
            // Paper end - configuration api - possibly use legacy saved server player instance
            // Folia start - region threading
            return serverPlayer;
        }
        public ServerPlayer spawn(Connection connection, CommonListenerCookie cookie, ServerPlayer serverPlayer) {
            // Folia end - region threading

            ServerPlayer var7;
            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(serverPlayer.problemPath(), PrepareSpawnTask.LOGGER)) {
                Optional<ValueInput> optional = PrepareSpawnTask.this.server
                    .getPlayerList()
                    .loadPlayerData(PrepareSpawnTask.this.nameAndId)
                    // CraftBukkit start
                    .map(tag -> {
                        org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer = serverPlayer.getBukkitEntity();
                        // Only update first played if it is older than the one we have
                        long modified = new java.io.File(net.minecraft.server.network.config.PrepareSpawnTask.this.server.playerDataStorage.getPlayerDir(), serverPlayer.getStringUUID() + ".dat").lastModified();
                        if (modified < craftPlayer.getFirstPlayed()) {
                            craftPlayer.setFirstPlayed(modified);
                        }
                        return tag;
                    })
                    // CraftBukkit end
                    .map(compoundTag -> TagValueInput.create(scopedCollector, PrepareSpawnTask.this.server.registryAccess(), compoundTag));
                optional.ifPresent(serverPlayer::load);
                // CraftBukkit start - Better rename detection
                if (optional.isPresent()) {
                    serverPlayer.lastKnownName = optional.flatMap(t -> t.child("bukkit")).flatMap(t -> t.getString("lastKnownName")).orElse(null);
                }
                // CraftBukkit end - Better rename detection
                // Paper start - Entity#getEntitySpawnReason
                if (optional.isEmpty()) {
                    serverPlayer.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT; // set Player SpawnReason to DEFAULT on first login
                }
                // Paper end - Entity#getEntitySpawnReason
                serverPlayer.snapTo(this.spawnPosition, this.spawnAngle.x, this.spawnAngle.y);
                PrepareSpawnTask.this.server.getPlayerList().placeNewPlayer(connection, serverPlayer, cookie);
                optional.ifPresent(valueInput -> {
                    serverPlayer.loadAndSpawnEnderPearls(valueInput);
                    serverPlayer.loadAndSpawnParentVehicle(valueInput);
                });
                var7 = serverPlayer;
            }

            return var7;
        }
    }

    sealed interface State permits PrepareSpawnTask.Preparing, PrepareSpawnTask.Ready {
    }
}
