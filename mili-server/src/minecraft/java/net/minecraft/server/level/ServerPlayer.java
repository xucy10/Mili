package net.minecraft.server.level;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.HashOps;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.debug.DebugSubscription;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.NautilusInventoryMenu;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ServerItemCooldowns;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerPlayer extends Player implements ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer { // Paper - rewrite chunk system
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final long LAST_SAVE_ABSENT = Long.MIN_VALUE; public long lastSave = LAST_SAVE_ABSENT; // Paper // Folia - threaded regions - changed to nanoTime
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_XZ = 32;
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_Y = 10;
    private static final int FLY_STAT_RECORDING_SPEED = 25;
    public static final double BLOCK_INTERACTION_DISTANCE_VERIFICATION_BUFFER = 1.0;
    public static final double ENTITY_INTERACTION_DISTANCE_VERIFICATION_BUFFER = 3.0;
    public static final int ENDER_PEARL_TICKET_RADIUS = 2;
    public static final String ENDER_PEARLS_TAG = "ender_pearls";
    public static final String ENDER_PEARL_DIMENSION_TAG = "ender_pearl_dimension";
    public static final String TAG_DIMENSION = "Dimension";
    private static final AttributeModifier CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER = new AttributeModifier(
        Identifier.withDefaultNamespace("creative_mode_block_range"), 0.5, AttributeModifier.Operation.ADD_VALUE
    );
    private static final AttributeModifier CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER = new AttributeModifier(
        Identifier.withDefaultNamespace("creative_mode_entity_range"), 2.0, AttributeModifier.Operation.ADD_VALUE
    );
    private static final Component SPAWN_SET_MESSAGE = Component.translatable("block.minecraft.set_spawn");
    private static final AttributeModifier WAYPOINT_TRANSMIT_RANGE_CROUCH_MODIFIER = new AttributeModifier(
        Identifier.withDefaultNamespace("waypoint_transmit_range_crouch"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );
    private static final boolean DEFAULT_SEEN_CREDITS = false;
    private static final boolean DEFAULT_SPAWN_EXTRA_PARTICLES_ON_FALL = false;
    public ServerGamePacketListenerImpl connection;
    private final MinecraftServer server;
    public final ServerPlayerGameMode gameMode;
    private final PlayerAdvancements advancements;
    private final ServerStatsCounter stats;
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE;
    private int lastRecordedAirLevel = Integer.MIN_VALUE;
    private int lastRecordedArmor = Integer.MIN_VALUE;
    private int lastRecordedLevel = Integer.MIN_VALUE;
    private int lastRecordedExperience = Integer.MIN_VALUE;
    private float lastSentHealth = -1.0E8F;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    public int lastSentExp = -99999999;
    private ChatVisiblity chatVisibility = ChatVisiblity.FULL;
    public ParticleStatus particleStatus = ParticleStatus.ALL;
    private boolean canChatColor = true;
    private long lastActionTime = Util.getMillis();
    private @Nullable Entity camera;
    public boolean isChangingDimension;
    public boolean seenCredits = false;
    private final ServerRecipeBook recipeBook;
    private @Nullable Vec3 levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    private int requestedViewDistance = 2;
    public String language = null; // Paper - default to null
    public java.util.Locale adventure$locale = java.util.Locale.US; // Paper
    private @Nullable Vec3 startingToFallPosition;
    private @Nullable Vec3 enteredNetherPosition;
    private @Nullable Vec3 enteredLavaOnVehiclePosition;
    private SectionPos lastSectionPos = SectionPos.of(0, 0, 0);
    private ChunkTrackingView chunkTrackingView = ChunkTrackingView.EMPTY;
    private ServerPlayer.@Nullable RespawnConfig respawnConfig;
    private final TextFilter textFilter;
    private boolean textFilteringEnabled;
    private boolean allowsListing;
    private boolean spawnExtraParticlesOnFall = false;
    public WardenSpawnTracker wardenSpawnTracker = new WardenSpawnTracker();
    private @Nullable BlockPos raidOmenPosition;
    private Vec3 lastKnownClientMovement = Vec3.ZERO;
    private Input lastClientInput = Input.EMPTY;
    private final Set<ThrownEnderpearl> enderPearls = new HashSet<>();
    private long timeEntitySatOnShoulder;
    private CompoundTag shoulderEntityLeft = new CompoundTag();
    private CompoundTag shoulderEntityRight = new CompoundTag();
    public final ContainerSynchronizer containerSynchronizer = new ContainerSynchronizer() {
        private final LoadingCache<TypedDataComponent<?>, Integer> cache = CacheBuilder.newBuilder()
            .maximumSize(256L)
            .build(
                new CacheLoader<TypedDataComponent<?>, Integer>() {
                    private final DynamicOps<HashCode> registryHashOps = ServerPlayer.this.registryAccess().createSerializationContext(HashOps.CRC32C_INSTANCE);

                    @Override
                    public Integer load(TypedDataComponent<?> component) {
                        return component.encodeValue(this.registryHashOps)
                            .getOrThrow(string -> new IllegalArgumentException("Failed to hash " + component + ": " + string))
                            .asInt();
                    }
                }
            );

        @Override
        public void sendInitialData(AbstractContainerMenu container, List<ItemStack> items, ItemStack carried, int[] remoteDataSlots) {
            ServerPlayer.this.connection.send(new ClientboundContainerSetContentPacket(container.containerId, container.incrementStateId(), items, carried));

            for (int i = 0; i < remoteDataSlots.length; i++) {
                this.broadcastDataValue(container, i, remoteDataSlots[i]);
            }
        }

        // Paper start - Sync offhand slot in menus
        @Override
        public void sendOffHandSlotChange() {
            this.sendSlotChange(ServerPlayer.this.inventoryMenu, net.minecraft.world.inventory.InventoryMenu.SHIELD_SLOT, ServerPlayer.this.inventoryMenu.getSlot(net.minecraft.world.inventory.InventoryMenu.SHIELD_SLOT).getItem().copy());
        }
        // Paper end - Sync offhand slot in menus

        @Override
        public void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack stack) {
            ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(container.containerId, container.incrementStateId(), slot, stack));
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu containerMenu, ItemStack stack) {
            ServerPlayer.this.connection.send(new ClientboundSetCursorItemPacket(stack));
        }

        @Override
        public void sendDataChange(AbstractContainerMenu container, int id, int value) {
            this.broadcastDataValue(container, id, value);
        }

        private void broadcastDataValue(AbstractContainerMenu container, int id, int value) {
            ServerPlayer.this.connection.send(new ClientboundContainerSetDataPacket(container.containerId, id, value));
        }

        @Override
        public RemoteSlot createSlot() {
            return new RemoteSlot.Synchronized(this.cache::getUnchecked);
        }
    };
    private final ContainerListener containerListener = new ContainerListener() {
        @Override
        public void slotChanged(AbstractContainerMenu containerToSend, int dataSlotIndex, ItemStack stack) {
            Slot slot = containerToSend.getSlot(dataSlotIndex);
            if (!(slot instanceof ResultSlot)) {
                if (slot.container == ServerPlayer.this.getInventory()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                }
            }
        }

        // Paper start - Add PlayerInventorySlotChangeEvent
        @Override
        public void slotChanged(AbstractContainerMenu containerToSend, int dataSlotIndex, ItemStack oldStack, ItemStack stack) {
            // See slotChanged above
            Slot slot = containerToSend.getSlot(dataSlotIndex);
            if (!(slot instanceof ResultSlot)) {
                if (slot.container == ServerPlayer.this.getInventory()) {
                    if (io.papermc.paper.event.player.PlayerInventorySlotChangeEvent.getHandlerList().getRegisteredListeners().length == 0) {
                        CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                        return;
                    }
                    io.papermc.paper.event.player.PlayerInventorySlotChangeEvent event = new io.papermc.paper.event.player.PlayerInventorySlotChangeEvent(
                        ServerPlayer.this.getBukkitEntity(),
                        dataSlotIndex,
                        org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(oldStack),
                        org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(stack)
                    );
                    event.callEvent();
                    if (event.shouldTriggerAdvancements()) {
                        CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                    }
                }
            }
        }
        // Paper end - Add PlayerInventorySlotChangeEvent

        @Override
        public void dataChanged(AbstractContainerMenu containerMenu, int dataSlotIndex, int value) {
        }
    };
    private @Nullable RemoteChatSession chatSession;
    public final @Nullable Object object;
    private final CommandSource commandSource = new CommandSource() {
        @Override
        public boolean acceptsSuccess() {
            return ServerPlayer.this.level().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return true;
        }

        @Override
        public void sendSystemMessage(Component message) {
            ServerPlayer.this.sendSystemMessage(message);
        }

        // CraftBukkit start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
            return ServerPlayer.this.getBukkitEntity();
        }
        // CraftBukkit end
    };
    private Set<DebugSubscription<?>> requestedDebugSubscriptions = Set.of();
    private int containerCounter;
    public boolean wonGame;
    private int containerUpdateDelay; // Paper - Configurable container update tick rate
    public long loginTime; // Paper - Replace OfflinePlayer#getLastPlayed
    public int patrolSpawnDelay; // Paper - Pillager patrol spawn settings and per player options
    // Paper start - cancellable death event
    public boolean queueHealthUpdatePacket;
    public net.minecraft.network.protocol.game.@Nullable ClientboundSetHealthPacket queuedHealthUpdatePacket;
    // Paper end - cancellable death event
    // Paper start - Optional per player mob spawns
    public static final int MOBCATEGORY_TOTAL_ENUMS = net.minecraft.world.entity.MobCategory.values().length;
    public final int[] mobCounts = new int[MOBCATEGORY_TOTAL_ENUMS];
    // Paper end - Optional per player mob spawns
    public final int[] mobBackoffCounts = new int[MOBCATEGORY_TOTAL_ENUMS]; // Paper - per player mob count backoff
    // CraftBukkit start
    public @Nullable String lastKnownName; // Better rename detection
    public String displayName;
    public net.kyori.adventure.text.Component adventure$displayName; // Paper
    public @Nullable Component listName;
    public int listOrder = 0;
    public org.bukkit.Location compassTarget;
    public int newExp = 0;
    public int newLevel = 0;
    public int newTotalExp = 0;
    public boolean keepLevel = false;
    public double maxHealthCache;
    public boolean joining = true;
    public boolean sentListPacket = false;
    public boolean suppressTrackerForLogin = false; // Paper - Fire PlayerJoinEvent when Player is actually ready
    // CraftBukkit end
    public boolean isRealPlayer; // Paper
    public com.destroystokyo.paper.event.entity.@Nullable PlayerNaturallySpawnCreaturesEvent playerNaturallySpawnedEvent; // Paper - PlayerNaturallySpawnCreaturesEvent
    public org.bukkit.event.player.PlayerQuitEvent.@Nullable QuitReason quitReason = null; // Paper - Add API for quit reason; there are a lot of changes to do if we change all methods leading to the event
    public volatile boolean isTpsBarVisible = false; //Luminol - Tps bar
    public volatile boolean isMemBarVisible = false; //Luminol - Memory bar
    public volatile boolean isRegionBarVisible = false; //Luminol - Region bar
    // Paper start - rewrite chunk system
    private ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.PlayerChunkLoaderData chunkLoader;
    private final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder viewDistanceHolder = new ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder();

    @Override
    public final boolean moonrise$isRealPlayer() {
        return this.isRealPlayer;
    }

    @Override
    public final void moonrise$setRealPlayer(final boolean real) {
        this.isRealPlayer = real;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.PlayerChunkLoaderData moonrise$getChunkLoader() {
        return this.chunkLoader;
    }

    @Override
    public final void moonrise$setChunkLoader(final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.PlayerChunkLoaderData loader) {
        this.chunkLoader = loader;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder moonrise$getViewDistanceHolder() {
        return this.viewDistanceHolder;
    }
    // Paper end - rewrite chunk system

    public ServerPlayer(MinecraftServer server, ServerLevel level, GameProfile gameProfile, ClientInformation clientInformation) {
        super(level, gameProfile);
        this.server = server;
        this.textFilter = server.createTextFilterForPlayer(this);
        this.gameMode = server.createGameModeForPlayer(this);
        this.gameMode.setGameModeForPlayer(this.calculateGameModeForNewPlayer(null), null);
        this.recipeBook = new ServerRecipeBook((recipe, output) -> server.getRecipeManager().listDisplaysForRecipe(recipe, output));
        this.stats = server.getPlayerList().getPlayerStats(this);
        this.advancements = server.getPlayerList().getPlayerAdvancements(this);
        this.updateOptionsNoEvents(clientInformation); // Paper - don't call options events on login
        this.object = null;
        // CraftBukkit start
        this.displayName = this.getScoreboardName();
        this.adventure$displayName = net.kyori.adventure.text.Component.text(this.getScoreboardName()); // Paper
        this.bukkitPickUpLoot = true;
        this.maxHealthCache = this.getMaxHealth();
        // CraftBukkit end
    }

    // Folia start - region threading
    private static final int SPAWN_RADIUS_SELECTION_SEARCH = 5;

    private static BlockPos getRandomSpawn(ServerLevel world, net.minecraft.util.RandomSource random) {
        BlockPos spawn = world.getLevelData().getRespawnData().pos();
        double radius = Math.max(0.0, world.getGameRules().get(GameRules.RESPAWN_RADIUS).doubleValue());

        double spawnX = (double)spawn.getX() + 0.5;
        double spawnZ = (double)spawn.getZ() + 0.5;

        net.minecraft.world.level.border.WorldBorder worldBorder = world.getWorldBorder();

        double selectMinX = Math.max(worldBorder.getMinX() + 1.0, spawnX - radius);
        double selectMinZ = Math.max(worldBorder.getMinZ() + 1.0, spawnZ - radius);
        double selectMaxX = Math.min(worldBorder.getMaxX() - 1.0, spawnX + radius);
        double selectMaxZ = Math.min(worldBorder.getMaxZ() - 1.0, spawnZ + radius);

        double amountX = selectMaxX - selectMinX;
        double amountZ = selectMaxZ - selectMinZ;

        // Luminol start - Correct player respawn place
        int selectX = amountX < 0.0 ? Mth.floor(worldBorder.getCenterX()) : (int)Mth.floor(amountX * random.nextDouble() + selectMinX);
        int selectZ = amountZ < 0.0 ? Mth.floor(worldBorder.getCenterZ()) : (int)Mth.floor(amountZ * random.nextDouble() + selectMinZ);
        // Luminol end - Correct player respawn place

        return new BlockPos(selectX, 0, selectZ);
    }

    private static void completeSpawn(ServerLevel world, BlockPos selected,
                                      ca.spottedleaf.concurrentutil.completable.CallbackCompletable<org.bukkit.Location> toComplete) {
        toComplete.complete(io.papermc.paper.util.MCUtil.toLocation(world, Vec3.atBottomCenterOf(selected), world.getLevelData().getRespawnData().yaw(), world.getLevelData().getRespawnData().pitch()));
    }

    private static BlockPos findSpawnAround(ServerLevel world, BlockPos selected) {
        // Luminol start - Correct player respawn place
        BlockPos inChunk;
        inChunk = PlayerSpawnFinder.getOverworldRespawnPos(world, selected.getX(), selected.getZ());
        if (inChunk != null) {
            AABB checkVolume = PlayerSpawnFinder.PLAYER_DIMENSIONS.makeBoundingBox((double)inChunk.getX() + 0.5, (double)inChunk.getY(), (double)inChunk.getZ() + 0.5);

            if (world.noCollision(null, checkVolume, true)) {
                return inChunk;
            }
        }
        // Luminol end - Correct player respawn place
        // try hard to find, so that we don't attempt another chunk load
        for (int dz = -SPAWN_RADIUS_SELECTION_SEARCH; dz <= SPAWN_RADIUS_SELECTION_SEARCH; ++dz) {
            for (int dx = -SPAWN_RADIUS_SELECTION_SEARCH; dx <= SPAWN_RADIUS_SELECTION_SEARCH; ++dx) {
                inChunk = PlayerSpawnFinder.getOverworldRespawnPos(world, selected.getX() + dx, selected.getZ() + dz);
                if (inChunk == null) {
                    continue;
                }

                AABB checkVolume = PlayerSpawnFinder.PLAYER_DIMENSIONS.makeBoundingBox((double)inChunk.getX() + 0.5, (double)inChunk.getY(), (double)inChunk.getZ() + 0.5);

                if (!world.noCollision(null, checkVolume, true)) {
                    continue;
                }

                return inChunk;
            }
        }

        return null;
    }

    // rets false when another attempt is required
    private static boolean trySpawnOrSchedule(ServerLevel world, net.minecraft.util.RandomSource random, int[] attemptCount, int maxAttempts,
                                              ca.spottedleaf.concurrentutil.completable.CallbackCompletable<org.bukkit.Location> toComplete) {
        ++attemptCount[0];

        BlockPos rough = getRandomSpawn(world, random);

        // add 2 to ensure that the chunks are loaded for collision checks
        int minX = (rough.getX() - (SPAWN_RADIUS_SELECTION_SEARCH + 2)) >> 4;
        int minZ = (rough.getZ() - (SPAWN_RADIUS_SELECTION_SEARCH + 2)) >> 4;
        int maxX = (rough.getX() + (SPAWN_RADIUS_SELECTION_SEARCH + 2)) >> 4;
        int maxZ = (rough.getZ() + (SPAWN_RADIUS_SELECTION_SEARCH + 2)) >> 4;

        // we could short circuit this check, but it would possibly recurse. Then, it could end up causing a stack overflow
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(world, minX, minZ, maxX, maxZ) || !world.moonrise$areChunksLoaded(minX, minZ, maxX, maxZ)) {
            world.moonrise$loadChunksAsync(minX, maxX, minZ, maxZ, ca.spottedleaf.concurrentutil.util.Priority.HIGHER,
                    (unused) -> {
                        BlockPos selected = findSpawnAround(world, rough);
                        if (selected == null) {
                            // run more spawn attempts
                            selectSpawn(world, random, attemptCount, maxAttempts, toComplete);
                            return;
                        }

                        completeSpawn(world, selected, toComplete);
                        return;
                    }
            );
            return true;
        }

        BlockPos selected = findSpawnAround(world, rough);
        if (selected == null) {
            return false;
        }

        completeSpawn(world, selected, toComplete);
        return true;
    }

    private static void selectSpawn(ServerLevel world, net.minecraft.util.RandomSource random, int[] attemptCount, int maxAttempts,
                                    ca.spottedleaf.concurrentutil.completable.CallbackCompletable<org.bukkit.Location> toComplete) {
        do {
            if (attemptCount[0] >= maxAttempts) {
                BlockPos sharedSpawn = world.getLevelData().getRespawnData().pos();

                LOGGER.warn("Found no spawn in radius, ignoring radius");

                selectSpawnWithoutRadius(world, sharedSpawn, toComplete);
                return;
            }
        } while (!trySpawnOrSchedule(world, random, attemptCount, maxAttempts, toComplete));
    }

    private static void selectSpawnWithoutRadius(ServerLevel world, BlockPos spawn, ca.spottedleaf.concurrentutil.completable.CallbackCompletable<org.bukkit.Location> toComplete) {
        world.loadChunksForMoveAsync(PlayerSpawnFinder.PLAYER_DIMENSIONS.makeBoundingBox(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5),
                ca.spottedleaf.concurrentutil.util.Priority.HIGHER,
                (c) -> {
                    BlockPos ret = spawn;
                    while (!world.noCollision(null, PlayerSpawnFinder.PLAYER_DIMENSIONS.makeBoundingBox(ret.getX() + 0.5, ret.getY(), ret.getZ() + 0.5), true) && ret.getY() < (double)world.getMaxY()) {
                        ret = ret.above();
                    }
                    while (world.noCollision(null, PlayerSpawnFinder.PLAYER_DIMENSIONS.makeBoundingBox(ret.getX() + 0.5, ret.getY() - 1, ret.getZ() + 0.5), true) && ret.getY() > (double)(world.getMinY() + 1)) {
                        ret = ret.below();
                    }
                    toComplete.complete(io.papermc.paper.util.MCUtil.toLocation(world, Vec3.atBottomCenterOf(ret), world.getLevelData().getRespawnData().yaw(), world.getLevelData().getRespawnData().pitch()));
                }
        );
    }

    public static void fudgeSpawnLocation(ServerLevel world, ca.spottedleaf.concurrentutil.completable.CallbackCompletable<org.bukkit.Location> toComplete) { // Folia - region threading
        if (world.dimensionType().hasSkyLight() && world.serverLevelData.getGameType() != GameType.ADVENTURE) { // CraftBukkit
            selectSpawn(world, world.random, new int[1], 500, toComplete);
        } else {
            selectSpawnWithoutRadius(world, world.getLevelData().getRespawnData().pos(), toComplete);
        }

    }

    public void queuePacketTask(Runnable run) {
        this.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
            run.run();
        }, null, 1L);
    }
    // Folia end - region threading

    @Override
    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        // Folia start - region threading
        if (true) {
            throw new UnsupportedOperationException();
        }
        // Folia end - region threading
        CompletableFuture<Vec3> completableFuture = PlayerSpawnFinder.findSpawn(level, pos);
        level.chunkSource.mainThreadProcessor.managedBlock(completableFuture::isDone); // Paper - rewrite chunk system
        return BlockPos.containing(completableFuture.join());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.wardenSpawnTracker = input.read("warden_spawn_tracker", WardenSpawnTracker.CODEC).orElseGet(WardenSpawnTracker::new);
        this.enteredNetherPosition = input.read("entered_nether_pos", Vec3.CODEC).orElse(null);
        this.seenCredits = input.getBooleanOr("seenCredits", false);
        input.read("recipeBook", ServerRecipeBook.Packed.CODEC)
            .ifPresent(packed -> this.recipeBook.loadUntrusted(packed, key -> this.server.getRecipeManager().byKey(key).isPresent()));
        this.getBukkitEntity().readExtraData(input); // CraftBukkit
        if (this.isSleeping()) {
            this.stopSleepingRaw(); // Folia - do not modify or read worldstate during data deserialization
        }

        this.respawnConfig = input.read("respawn", ServerPlayer.RespawnConfig.CODEC).orElse(null);
        this.spawnExtraParticlesOnFall = input.getBooleanOr("spawn_extra_particles_on_fall", false);
        this.raidOmenPosition = input.read("raid_omen_position", BlockPos.CODEC).orElse(null);
        // Paper start - Expand PlayerGameModeChangeEvent
        this.loadGameTypes(input);
    }
    private void loadGameTypes(ValueInput input) {
        if (this.server.getForcedGameType() != null && this.server.getForcedGameType() != readPlayerMode(input, "playerGameType")) {
            if (new org.bukkit.event.player.PlayerGameModeChangeEvent(this.getBukkitEntity(), org.bukkit.GameMode.getByValue(this.server.getDefaultGameType().getId()), org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE, null).callEvent()) {
                this.gameMode.setGameModeForPlayer(this.server.getForcedGameType(), GameType.DEFAULT_MODE);
            } else {
                this.gameMode.setGameModeForPlayer(readPlayerMode(input, "playerGameType"), readPlayerMode(input, "previousPlayerGameType"));
            }
            return;
        }
        // Paper end - Expand PlayerGameModeChangeEvent
        this.gameMode
            .setGameModeForPlayer(this.calculateGameModeForNewPlayer(readPlayerMode(input, "playerGameType")), readPlayerMode(input, "previousPlayerGameType"));
        this.setShoulderEntityLeft(input.read("ShoulderEntityLeft", CompoundTag.CODEC).orElseGet(CompoundTag::new));
        this.setShoulderEntityRight(input.read("ShoulderEntityRight", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("warden_spawn_tracker", WardenSpawnTracker.CODEC, this.wardenSpawnTracker);
        this.storeGameTypes(output);
        output.putBoolean("seenCredits", this.seenCredits);
        output.storeNullable("entered_nether_pos", Vec3.CODEC, this.enteredNetherPosition);
        this.saveParentVehicle(output);
        output.store("recipeBook", ServerRecipeBook.Packed.CODEC, this.recipeBook.pack());
        output.putString("Dimension", this.level().dimension().identifier().toString());
        output.storeNullable("respawn", ServerPlayer.RespawnConfig.CODEC, this.respawnConfig);
        output.putBoolean("spawn_extra_particles_on_fall", this.spawnExtraParticlesOnFall);
        output.storeNullable("raid_omen_position", BlockPos.CODEC, this.raidOmenPosition);
        this.saveEnderPearls(output);
        if (!this.getShoulderEntityLeft().isEmpty()) {
            output.store("ShoulderEntityLeft", CompoundTag.CODEC, this.getShoulderEntityLeft());
        }

        if (!this.getShoulderEntityRight().isEmpty()) {
            output.store("ShoulderEntityRight", CompoundTag.CODEC, this.getShoulderEntityRight());
        }
        this.getBukkitEntity().setExtraData(output); // CraftBukkit
    }

    private void saveParentVehicle(ValueOutput output) {
        Entity rootVehicle = this.getRootVehicle();
        Entity vehicle = this.getVehicle();
        // CraftBukkit start - handle non-persistent vehicles
        boolean persistVehicle = true;
        if (vehicle != null) {
            for (Entity topVehicle = vehicle; topVehicle != null; topVehicle = topVehicle.getVehicle()) {
                if (!topVehicle.persist) {
                    persistVehicle = false;
                    break;
                }
            }
        }
        if (persistVehicle && vehicle != null && rootVehicle != this && rootVehicle.hasExactlyOnePlayerPassenger() && !rootVehicle.isRemoved()) { // Paper - Ensure valid vehicle status
            // CraftBukkit end
            ValueOutput valueOutput = output.child("RootVehicle");
            valueOutput.store("Attach", UUIDUtil.CODEC, vehicle.getUUID());
            rootVehicle.save(valueOutput.child("Entity"));
        }
    }

    public void loadAndSpawnParentVehicle(ValueInput input) {
        Optional<ValueInput> optional = input.child("RootVehicle");
        if (!optional.isEmpty()) {
            ServerLevel serverLevel = this.level();
            Vec3 playerPos = this.position(); // Paper - force sync root vehicle to player position
            Entity entity = EntityType.loadEntityRecursive(
                    // Paper start - force sync root vehicle to player position
                    optional.get().childOrEmpty("Entity"), serverLevel, EntitySpawnReason.LOAD, (Entity entity2) -> {
                        // Paper start - force sync root vehicle to player position
                        if (entity2.distanceToSqr(ServerPlayer.this) > (5.0 * 5.0)) {
                            entity2.setPosRaw(playerPos.x, playerPos.y, playerPos.z, true);
                        }
                        // Paper end - force sync root vehicle to player position
                        return !serverLevel.addWithUUID(entity2, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.MOUNT) ? null : entity2; // Paper - Entity#getEntitySpawnReason
                    }
                    // Paper end - force sync root vehicle to player position
            );
            if (entity != null) {
                UUID uuid = optional.get().read("Attach", UUIDUtil.CODEC).orElse(null);
                if (entity.getUUID().equals(uuid)) {
                    this.startRiding(entity, true, false);
                } else {
                    for (Entity entity1 : entity.getIndirectPassengers()) {
                        if (entity1.getUUID().equals(uuid)) {
                            this.startRiding(entity1, true, false);
                            break;
                        }
                    }
                }

                if (!this.isPassenger()) {
                    LOGGER.warn("Couldn't reattach entity to player");
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause

                    for (Entity entity1x : entity.getIndirectPassengers()) {
                        entity1x.discard(null); // CraftBukkit - add Bukkit remove cause
                    }
                }
            }
        }
    }

    private void saveEnderPearls(ValueOutput output) {
        if (!this.enderPearls.isEmpty()) {
            ValueOutput.ValueOutputList valueOutputList = output.childrenList("ender_pearls");

            for (ThrownEnderpearl thrownEnderpearl : this.enderPearls) {
                if (thrownEnderpearl.level().paperConfig().misc.legacyEnderPearlBehavior) continue; // Paper - Allow using old ender pearl behavior
                if (thrownEnderpearl.isRemoved()) {
                    LOGGER.warn("Trying to save removed ender pearl, skipping");
                } else {
                    ValueOutput valueOutput = valueOutputList.addChild();
                    thrownEnderpearl.save(valueOutput);
                    valueOutput.store("ender_pearl_dimension", Level.RESOURCE_KEY_CODEC, thrownEnderpearl.level().dimension());
                }
            }
        }
    }

    public void loadAndSpawnEnderPearls(ValueInput input) {
        input.childrenListOrEmpty("ender_pearls").forEach(this::loadAndSpawnEnderPearl);
    }

    private void loadAndSpawnEnderPearl(ValueInput input) {
        Optional<ResourceKey<Level>> optional = input.read("ender_pearl_dimension", Level.RESOURCE_KEY_CODEC);
        if (!optional.isEmpty()) {
            ServerLevel level = this.level().getServer().getLevel(optional.get());
            if (level != null) {
                Entity entity = EntityType.loadEntityRecursive(input, level, EntitySpawnReason.LOAD, net.minecraft.world.entity.EntityProcessor.NOP); // Folia - region threading - delay world add
                if (entity != null) {
                    // Folia start - region threading
                    io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                            level, entity.chunkPosition().x, entity.chunkPosition().z, () -> {
                                level.addFreshEntityWithPassengers(entity);
                                ServerPlayer.placeEnderPearlTicket(level, entity.chunkPosition());
                            }
                    );
                    // Folia end - region threading
                } else {
                    LOGGER.warn("Failed to spawn player ender pearl in level ({}), skipping", optional.get());
                }
            } else {
                LOGGER.warn("Trying to load ender pearl without level ({}) being loaded, skipping", optional.get());
            }
        }
    }

    // CraftBukkit start
    public void spawnIn(final ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level can't be null");
        }
        this.setLevel(level);
        this.gameMode.setLevel(level);
    }
    // CraftBukkit end

    public void setExperiencePoints(int experiencePoints) {
        float f = this.getXpNeededForNextLevel();
        float f1 = (f - 1.0F) / f;
        float f2 = Mth.clamp(experiencePoints / f, 0.0F, f1);
        if (f2 != this.experienceProgress) {
            this.experienceProgress = f2;
            this.lastSentExp = -1;
        }
    }

    public void setExperienceLevels(int level) {
        if (level != this.experienceLevel) {
            this.experienceLevel = level;
            this.lastSentExp = -1;
        }
    }

    @Override
    public void giveExperienceLevels(int levels) {
        if (levels != 0) {
            super.giveExperienceLevels(levels);
            this.lastSentExp = -1;
        }
    }

    @Override
    public void onEnchantmentPerformed(ItemStack enchantedItem, int cost) {
        super.onEnchantmentPerformed(enchantedItem, cost);
        this.lastSentExp = -1;
    }

    public void initMenu(AbstractContainerMenu menu) {
        menu.addSlotListener(this.containerListener);
        menu.setSynchronizer(this.containerSynchronizer);
    }

    public void initInventoryMenu() {
        this.initMenu(this.inventoryMenu);
    }

    @Override
    public void onEnterCombat() {
        super.onEnterCombat();
        this.connection.send(ClientboundPlayerCombatEnterPacket.INSTANCE);
    }

    @Override
    public void onLeaveCombat() {
        super.onLeaveCombat();
        this.connection.send(new ClientboundPlayerCombatEndPacket(this.getCombatTracker()));
    }

    @Override
    public void onInsideBlock(BlockState state) {
        CriteriaTriggers.ENTER_BLOCK.trigger(this, state);
    }

    @Override
    protected ItemCooldowns createItemCooldowns() {
        return new ServerItemCooldowns(this);
    }

    @Override
    public void tick() {
        // CraftBukkit start
        if (this.joining) {
            this.joining = false;
        }
        // CraftBukkit end
        this.connection.tickClientLoadTimeout();
        this.gameMode.tick();
        this.wardenSpawnTracker.tick();
        if (this.invulnerableTime > 0) {
            this.invulnerableTime--;
        }

        // Paper start - Configurable container update tick rate
        if (--this.containerUpdateDelay <= 0) {
            this.containerMenu.broadcastChanges();
            // Broadcast equipment and crafting slots when the player is in a container, fixes MC-297508
            if (this.containerMenu != this.inventoryMenu) {
                this.inventoryMenu.broadcastNonContainerSlotChanges();
            }
            this.containerUpdateDelay = this.level().paperConfig().tickRates.containerUpdate;
        }
        // Paper end - Configurable container update tick rate
        if (this.containerMenu != this.inventoryMenu && (this.isImmobile() || !this.containerMenu.stillValid(this))) { // Paper - Prevent opening inventories when frozen
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.CANT_USE); // Paper - Inventory close reason
            this.containerMenu = this.inventoryMenu;
        }

        Entity camera = this.getCamera();
        if (camera != this) {
            if (camera.canBeSpectated()) { // Folia - region threading - replace removed check
                if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(camera) && !camera.isRemoved()) { // Folia - region threading
                this.absSnapTo(camera.getX(), camera.getY(), camera.getZ(), camera.getYRot(), camera.getXRot());
                this.level().getChunkSource().move(this);
                if (this.wantsToStopRiding()) {
                    this.setCamera(this);
                }
                } else { // Folia start - region threading
                    Entity realCamera = camera.getBukkitEntity().getHandleRaw();
                    if (realCamera != camera) {
                        this.setCamera(this);
                        this.setCamera(realCamera);
                    } else {
                        this.teleportToCameraOffRegion();
                    }
                }
                // Folia end - region threading
            } else {
                this.setCamera(this);
            }
        }

        CriteriaTriggers.TICK.trigger(this);
        if (this.levitationStartPos != null) {
            CriteriaTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
        }

        this.trackStartFallingPosition();
        this.trackEnteredOrExitedLavaOnVehicle();
        this.updatePlayerAttributes();
        this.advancements.flushDirty(this, true);

        // KioCG start - ChunkHot
        if (this.tickCount % 20 == 0){
            this.nearbyChunkHot = this.refreshNearbyChunkHot();
        }
        // KioCG end
    }

    // KioCG start - ChunkHot
    private volatile long nearbyChunkHot = 0;

    public long getNearbyChunkHot() { return this.nearbyChunkHot; }

    private long refreshNearbyChunkHot() {
        long total = 0L;
        int searchRadius = ((ServerLevel) this.level()).moonrise$getViewDistanceHolder().getViewDistances().tickViewDistance();
        for (int i = this.moonrise$getSectionX() - searchRadius; i <= this.moonrise$getSectionX() + searchRadius; ++i) {
            for (int j = this.moonrise$getSectionZ() - searchRadius; j <= this.moonrise$getSectionZ() + searchRadius; ++j) {
                net.minecraft.world.level.chunk.LevelChunk targetChunk = this.level().getChunkIfLoaded(i, j);
                if (targetChunk != null) {
                    total += targetChunk.getChunkHot().getAverage();
                }
            }
        }
        return total;
    }
    // KioCG end


    private void updatePlayerAttributes() {
        AttributeInstance attribute = this.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (attribute != null) {
            if (this.isCreative()) {
                attribute.addOrUpdateTransientModifier(CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            } else {
                attribute.removeModifier(CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            }
        }

        AttributeInstance attribute1 = this.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attribute1 != null) {
            if (this.isCreative()) {
                attribute1.addOrUpdateTransientModifier(CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            } else {
                attribute1.removeModifier(CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            }
        }

        AttributeInstance attribute2 = this.getAttribute(Attributes.WAYPOINT_TRANSMIT_RANGE);
        if (attribute2 != null) {
            if (this.isCrouching()) {
                attribute2.addOrUpdateTransientModifier(WAYPOINT_TRANSMIT_RANGE_CROUCH_MODIFIER);
            } else {
                attribute2.removeModifier(WAYPOINT_TRANSMIT_RANGE_CROUCH_MODIFIER);
            }
        }
    }

    public void doTick() {
        try {
            if (valid && !this.isSpectator() || !this.touchingUnloadedChunk()) { // Paper - don't tick dead players that are not in the world currently (pending respawn)
                super.tick();
                if (!this.containerMenu.stillValid(this)) {
                    this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.CANT_USE); // Paper - Inventory close reason
                    this.containerMenu = this.inventoryMenu;
                }

                this.foodData.tick(this);
                this.awardStat(Stats.PLAY_TIME);
                this.awardStat(Stats.TOTAL_WORLD_TIME);
                if (this.isAlive()) {
                    this.awardStat(Stats.TIME_SINCE_DEATH);
                }

                if (this.isDiscrete()) {
                    this.awardStat(Stats.CROUCH_TIME);
                }

                if (!this.isSleeping()) {
                    this.awardStat(Stats.TIME_SINCE_REST);
                }
            }

            for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
                ItemStack item = this.getInventory().getItem(i);
                if (!item.isEmpty()) {
                    this.synchronizeSpecialItemUpdates(item);
                }
            }

            if (this.getHealth() != this.lastSentHealth
                || this.lastSentFood != this.foodData.getFoodLevel()
                || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
                this.connection.send(new ClientboundSetHealthPacket(this.getBukkitEntity().getScaledHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel())); // CraftBukkit
                this.lastSentHealth = this.getHealth();
                this.lastSentFood = this.foodData.getFoodLevel();
                this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
                this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
                this.updateScoreForCriteria(ObjectiveCriteria.HEALTH, Mth.ceil(this.lastRecordedHealthAndAbsorption));
            }

            if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
                this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
                this.updateScoreForCriteria(ObjectiveCriteria.FOOD, Mth.ceil((float)this.lastRecordedFoodLevel));
            }

            if (this.getAirSupply() != this.lastRecordedAirLevel) {
                this.lastRecordedAirLevel = this.getAirSupply();
                this.updateScoreForCriteria(ObjectiveCriteria.AIR, Mth.ceil((float)this.lastRecordedAirLevel));
            }

            if (this.getArmorValue() != this.lastRecordedArmor) {
                this.lastRecordedArmor = this.getArmorValue();
                this.updateScoreForCriteria(ObjectiveCriteria.ARMOR, Mth.ceil((float)this.lastRecordedArmor));
            }

            if (this.totalExperience != this.lastRecordedExperience) {
                this.lastRecordedExperience = this.totalExperience;
                this.updateScoreForCriteria(ObjectiveCriteria.EXPERIENCE, Mth.ceil((float)this.lastRecordedExperience));
            }

            // CraftBukkit start - Force max health updates
            if (this.maxHealthCache != this.getMaxHealth()) {
                this.getBukkitEntity().updateScaledHealth();
            }
            // CraftBukkit end

            if (this.experienceLevel != this.lastRecordedLevel) {
                this.lastRecordedLevel = this.experienceLevel;
                this.updateScoreForCriteria(ObjectiveCriteria.LEVEL, Mth.ceil((float)this.lastRecordedLevel));
            }

            if (this.totalExperience != this.lastSentExp) {
                this.lastSentExp = this.totalExperience;
                this.connection.send(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
            }

            if (this.tickCount % 20 == 0) {
                CriteriaTriggers.LOCATION.trigger(this);
            }

            // CraftBukkit start - initialize oldLevel, fire PlayerLevelChangeEvent, and tick client-sided world border
            if (this.oldLevel == -1) {
                this.oldLevel = this.experienceLevel;
            }

            if (this.oldLevel != this.experienceLevel) {
                org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerLevelChangeEvent(this.getBukkitEntity(), this.oldLevel, this.experienceLevel);
                this.oldLevel = this.experienceLevel;
            }

            // Folia - region threading - move to main tick loop where we can detect duplicates
            // CraftBukkit end
        } catch (Throwable var4) {
            CrashReport crashReport = CrashReport.forThrowable(var4, "Ticking player");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Player being ticked");
            this.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    private void synchronizeSpecialItemUpdates(ItemStack stack) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData savedData = MapItem.getSavedData(mapId, this.level());
        if (savedData != null) {
            Packet<?> updatePacket = savedData.getUpdatePacket(mapId, this);
            if (updatePacket != null) {
                this.connection.send(updatePacket);
            }
        }
    }

    @Override
    protected void tickRegeneration() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)) {
            if (this.tickCount % 20 == 0) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit - added regain reason of "REGEN" for filtering purposes.
                }

                float saturationLevel = this.foodData.getSaturationLevel();
                if (saturationLevel < 20.0F) {
                    this.foodData.setSaturation(saturationLevel + 1.0F);
                }
            }

            if (this.tickCount % 10 == 0 && this.foodData.needsFood()) {
                this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);
            }
        }
    }

    @Override
    public void handleShoulderEntities() {
        this.playShoulderEntityAmbientSound(this.getShoulderEntityLeft());
        this.playShoulderEntityAmbientSound(this.getShoulderEntityRight());
        if (this.fallDistance > 0.5 || this.isInWater() || this.getAbilities().flying || this.isSleeping() || this.isInPowderSnow) {
            if (!this.level().paperConfig().entities.behavior.parrotsAreUnaffectedByPlayerMovement) // Paper - Add option to make parrots stay
            this.removeEntitiesOnShoulder();
        }
    }

    private void playShoulderEntityAmbientSound(CompoundTag tag) {
        if (!tag.isEmpty() && !tag.getBooleanOr("Silent", false)) {
            if (this.random.nextInt(200) == 0) {
                EntityType<?> entityType = tag.read("id", EntityType.CODEC).orElse(null);
                if (entityType == EntityType.PARROT && !Parrot.imitateNearbyMobs(this.level(), this)) {
                    this.level()
                        .playSound(
                            null,
                            this.getX(),
                            this.getY(),
                            this.getZ(),
                            Parrot.getAmbient(this.level(), this.random),
                            this.getSoundSource(),
                            1.0F,
                            Parrot.getPitch(this.random)
                        );
                }
            }
        }
    }

    public boolean setEntityOnShoulder(CompoundTag tag) {
        if (this.isPassenger() || !this.onGround() || this.isInWater() || this.isInPowderSnow) {
            return false;
        } else if (this.getShoulderEntityLeft().isEmpty()) {
            this.setShoulderEntityLeft(tag);
            this.timeEntitySatOnShoulder = this.level().getGameTime();
            return true;
        } else if (this.getShoulderEntityRight().isEmpty()) {
            this.setShoulderEntityRight(tag);
            this.timeEntitySatOnShoulder = this.level().getGameTime();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void removeEntitiesOnShoulder() {
        if (this.timeEntitySatOnShoulder + 20L < this.level().getGameTime()) {
            // CraftBukkit start
            if (this.respawnEntityOnShoulder(this.getShoulderEntityLeft())) {
                this.setShoulderEntityLeft(new CompoundTag());
            }
            if (this.respawnEntityOnShoulder(this.getShoulderEntityRight())) {
                this.setShoulderEntityRight(new CompoundTag());
            }
            // CraftBukkit end
        }
    }

    // Paper start - release entity api
    public Entity releaseLeftShoulderEntity() {
        Entity entity = this.respawnEntityOnShoulder0(this.getShoulderEntityLeft());
        if (entity != null) {
            this.setShoulderEntityLeft(new CompoundTag());
        }
        return entity;
    }

    public Entity releaseRightShoulderEntity() {
        Entity entity = this.respawnEntityOnShoulder0(this.getShoulderEntityRight());
        if (entity != null) {
            this.setShoulderEntityRight(new CompoundTag());
        }
        return entity;
    }
    // Paper end - release entity api

    private boolean respawnEntityOnShoulder(CompoundTag tag) { // CraftBukkit - void -> boolean
    // Paper start - release entity api - return entity - overload
        return this.respawnEntityOnShoulder0(tag) != null;
    }
    @Nullable
    private Entity respawnEntityOnShoulder0(CompoundTag tag) { // CraftBukkit void->boolean
    // Paper end - release entity api - return entity - overload
        ServerLevel scopedCollector = this.level();
        if (scopedCollector instanceof ServerLevel) {
            ServerLevel serverLevel = scopedCollector;
            if (!tag.isEmpty()) {
                try (ProblemReporter.ScopedCollector scopedCollectorx = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
                    return EntityType.create( // Paper - release entity api
                            TagValueInput.create(scopedCollectorx.forChild(() -> ".shoulder"), serverLevel.registryAccess(), tag),
                            serverLevel,
                            EntitySpawnReason.LOAD
                        )
                        .map(entity -> { // Paper - release entity api
                            if (entity instanceof TamableAnimal tamableAnimal) {
                                tamableAnimal.setOwner(this);
                            }

                            entity.setPos(this.getX(), this.getY() + 0.7F, this.getZ());
                            return serverLevel.addWithUUID(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY) ? entity : null; // Paper - spawn reason
                        }).orElse(null); // Paper - release entity api - return entity
                }
            }
        }
        return null; // Paper - return null
    }

    @Override
    public void resetFallDistance() {
        if (this.getHealth() > 0.0F && this.startingToFallPosition != null) {
            CriteriaTriggers.FALL_FROM_HEIGHT.trigger(this, this.startingToFallPosition);
        }

        this.startingToFallPosition = null;
        super.resetFallDistance();
    }

    public void trackStartFallingPosition() {
        if (this.fallDistance > 0.0 && this.startingToFallPosition == null) {
            this.startingToFallPosition = this.position();
            if (this.currentImpulseImpactPos != null && this.currentImpulseImpactPos.y <= this.startingToFallPosition.y) {
                CriteriaTriggers.FALL_AFTER_EXPLOSION.trigger(this, this.currentImpulseImpactPos, this.currentExplosionCause);
            }
        }
    }

    public void trackEnteredOrExitedLavaOnVehicle() {
        if (this.getVehicle() != null && this.getVehicle().isInLava()) {
            if (this.enteredLavaOnVehiclePosition == null) {
                this.enteredLavaOnVehiclePosition = this.position();
            } else {
                CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(this, this.enteredLavaOnVehiclePosition);
            }
        }

        if (this.enteredLavaOnVehiclePosition != null && (this.getVehicle() == null || !this.getVehicle().isInLava())) {
            this.enteredLavaOnVehiclePosition = null;
        }
    }

    private void updateScoreForCriteria(ObjectiveCriteria criteria, int points) {
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(criteria, this, scoreAccess -> scoreAccess.set(points)); // CraftBukkit - Use our scores instead
    }

    // Paper start - PlayerDeathEvent#getItemsToKeep
    private static boolean shouldKeepDeathEventItem(
        final org.bukkit.event.entity.PlayerDeathEvent event,
        final ItemStack item
    ) {
        final List<org.bukkit.inventory.ItemStack> itemsToKeep = event.getItemsToKeep();
        if (EnchantmentHelper.has(item, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) || itemsToKeep.isEmpty() || item.isEmpty()) {
            return false;
        }

        final org.bukkit.inventory.ItemStack bukkitStack = item.getBukkitStack();
        final java.util.Iterator<org.bukkit.inventory.ItemStack> iterator = itemsToKeep.iterator();
        while (iterator.hasNext()) {
            final org.bukkit.inventory.ItemStack itemStack = iterator.next();
            if (bukkitStack.equals(itemStack)) {
                iterator.remove();
                return true;
            }
        }

        return false;
    }
    // Paper end - PlayerDeathEvent#getItemsToKeep
    // Paper start - Expand PlayerDeathEvent API
    private void sendClientboundPlayerCombatKillPacket(boolean displayMessage, Component deathMessage) {
        if (displayMessage && deathMessage != CommonComponents.EMPTY) {
            // Paper - moved from below die(DamageSource) method
            this.connection
                .send(
                    new ClientboundPlayerCombatKillPacket(this.getId(), deathMessage),
                    PacketSendListener.exceptionallySend(
                        () -> {
                            int i = 256;
                            String string = deathMessage.getString(256);
                            Component component = Component.translatable(
                                "death.attack.message_too_long", Component.literal(string).withStyle(ChatFormatting.YELLOW)
                            );
                            Component component1 = Component.translatable("death.attack.even_more_magic", this.getDisplayName())
                                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(component)));
                            return new ClientboundPlayerCombatKillPacket(this.getId(), component1);
                        }
                    )
                );
        } else {
            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }
    }
    // Paper end - Expand PlayerDeathEvent API
    @Override
    public void die(DamageSource damageSource) {
        // this.gameEvent(GameEvent.ENTITY_DIE); // Paper - move below event cancellation check
        boolean flag = this.level().getGameRules().get(GameRules.SHOW_DEATH_MESSAGES); final boolean showDeathMessage = flag; // Paper - OBFHELPER
        // CraftBukkit start - fire PlayerDeathEvent
        if (this.isRemoved()) {
            return;
        }
        List<DefaultDrop> loot = new java.util.ArrayList<>(this.getInventory().getContainerSize()); // Paper - Restore vanilla drops behavior
        boolean keepInventory = this.level().getGameRules().get(GameRules.KEEP_INVENTORY) || this.isSpectator();
        if (!keepInventory) {
            for (ItemStack item : this.getInventory().getContents()) {
                if (!item.isEmpty() && !EnchantmentHelper.has(item, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    loot.add(new DefaultDrop(item, stack -> this.drop(stack, true, false, false, null))); // Paper - Restore vanilla drops behavior; drop function taken from Inventory#dropAll (don't fire drop event)
                }
            }
        }
        if (!this.isSpectator() && this.shouldDropLoot(this.level())) { // Paper - fix player loottables running when mob loot gamerule is false
            // SPIGOT-5071: manually add player loot tables (SPIGOT-5195 - ignores keepInventory rule)
            this.dropFromLootTable(this.level(), damageSource, this.lastHurtByPlayerMemoryTime > 0);
            // Paper - Restore vanilla drops behaviour; custom death loot is a noop on server player, remove.
            loot.addAll(this.drops);
            this.drops.clear(); // SPIGOT-5188: make sure to clear
        } // Paper - fix player loottables running when mob loot gamerule is false

        Component defaultMessage = this.getCombatTracker().getDeathMessage();

        String deathmessage = defaultMessage.getString();
        this.keepLevel = keepInventory; // SPIGOT-2222: pre-set keepLevel
        org.bukkit.event.entity.PlayerDeathEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerDeathEvent(this, damageSource, loot, io.papermc.paper.adventure.PaperAdventure.asAdventure(defaultMessage), showDeathMessage, keepInventory); // Paper - Adventure; Expand PlayerDeathEvent API
        // Paper start - cancellable death event
        if (event.isCancelled()) {
            // make compatible with plugins that might have already set the health in an event listener
            if (this.getHealth() <= 0) {
                this.setHealth((float) event.getReviveHealth());
            }
            return;
        }
        this.gameEvent(GameEvent.ENTITY_DIE); // moved from the top of this method
        // Paper end

        // SPIGOT-943 - only call if they have an inventory open
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DEATH); // Paper - Inventory close reason
        }

        net.kyori.adventure.text.Component apiDeathMessage = event.deathMessage() != null ? event.deathMessage() : net.kyori.adventure.text.Component.empty(); // Paper - Adventure
        Component deathScreenMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(event.deathScreenMessageOverride() != null ? event.deathScreenMessageOverride() : apiDeathMessage); // Paper - Expand PlayerDeathEvent API

        if (apiDeathMessage != null && apiDeathMessage != net.kyori.adventure.text.Component.empty() && event.getShowDeathMessages()) { // Paper - Adventure; Expand PlayerDeathEvent API
            Component deathMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(apiDeathMessage); // Paper - Adventure

            // Paper - moved up to sendClientboundPlayerCombatKillPacket()
            sendClientboundPlayerCombatKillPacket(event.getShowDeathMessages(), deathScreenMessage); // Paper - Expand PlayerDeathEvent
            Team team = this.getTeam();
            if (team == null || team.getDeathMessageVisibility() == Team.Visibility.ALWAYS) {
                this.server.getPlayerList().broadcastSystemMessage(deathMessage, false);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                this.server.getPlayerList().broadcastSystemToTeam(this, deathMessage);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, deathMessage);
            }
        } else {
            sendClientboundPlayerCombatKillPacket(event.getShowDeathMessages(), deathScreenMessage); // Paper - Expand PlayerDeathEvent
        }

        this.removeEntitiesOnShoulder();
        if (this.level().getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }

        // SPIGOT-5478 must be called manually now
        if (shouldDropExperience(event.shouldDropExperience(), event.forceUseEventDropStatus())) this.dropExperience(this.level(), damageSource.getEntity()); // Paper - tie to event
        // we clean the player's inventory after the EntityDeathEvent is called so plugins can get the exact state of the inventory.
        if (!event.getKeepInventory()) {
            // Paper start - PlayerDeathEvent#getItemsToKeep
            for (int i = 0; i < this.getInventory().getNonEquipmentItems().size(); i++) {
                if (!shouldKeepDeathEventItem(event, this.getInventory().getNonEquipmentItems().get(i))) {
                    this.getInventory().getNonEquipmentItems().set(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
            for (final EquipmentSlot value : EquipmentSlot.VALUES) {
                if (this.getInventory().equipment.has(value) && !shouldKeepDeathEventItem(event, this.getInventory().equipment.get(value))) {
                    this.getInventory().equipment.set(value, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }

            // remainder of items left in toKeep - plugin added stuff on death that wasn't in the initial loot?
            if (!event.getItemsToKeep().isEmpty()) {
                for (org.bukkit.inventory.ItemStack itemStack : event.getItemsToKeep()) {
                    event.getEntity().getInventory().addItem(itemStack);
                }
            }
            // Paper end - PlayerDeathEvent#getItemsToKeep
        }

        this.setCamera(this); // Remove spectated target
        // CraftBukkit end

        this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment); // CraftBukkit - Get our scores instead
        LivingEntity killCredit = this.getKillCredit();
        if (killCredit != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(killCredit.getType()));
            killCredit.awardKillScore(this, damageSource);
            this.createWitherRose(killCredit);
        }

        this.level().broadcastEntityEvent(this, EntityEvent.DEATH);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
        this.connection.markClientUnloadedAfterDeath();
    }

    // Leaves start - exp fix
    private boolean shouldDropExperience(boolean eventResult, boolean forceUseEvent) {
        if (forceUseEvent) {
            return eventResult;
        }
        return wasExperienceConsumed() ? false : eventResult;
    }
    // Leaves end - exp fix

    private void tellNeutralMobsThatIDied() {
        AABB aabb = new AABB(this.blockPosition()).inflate(32.0, 10.0, 32.0);
        this.level()
            .getEntitiesOfClass(Mob.class, aabb, EntitySelector.NO_SPECTATORS)
            .stream()
            .filter(mob -> mob instanceof NeutralMob)
            .forEach(mob -> ((NeutralMob)mob).playerDied(this.level(), this));
    }

    @Override
    public void awardKillScore(Entity entity, DamageSource damageSource) {
        if (entity != this) {
            super.awardKillScore(entity, damageSource);
            Scoreboard scoreboard = this.level().getScoreboard();
            this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.KILL_COUNT_ALL, this, ScoreAccess::increment); // CraftBukkit - Get our scores instead
            if (entity instanceof Player) {
                this.awardStat(Stats.PLAYER_KILLS);
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.KILL_COUNT_PLAYERS, this, ScoreAccess::increment); // CraftBukkit - Get our scores instead
            } else {
                this.awardStat(Stats.MOB_KILLS);
            }

            this.handleTeamKill(this, entity, ObjectiveCriteria.TEAM_KILL);
            this.handleTeamKill(entity, this, ObjectiveCriteria.KILLED_BY_TEAM);
            CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(this, entity, damageSource);
        }
    }

    private void handleTeamKill(ScoreHolder scoreHolder, ScoreHolder teamMember, ObjectiveCriteria[] criteria) {
        PlayerTeam playersTeam = this.getBukkitEntity().getScoreboard().getHandle().getPlayersTeam(teamMember.getScoreboardName()); // CraftBukkit
        if (playersTeam != null) {
            int id = playersTeam.getColor().getId();
            if (id >= 0 && id < criteria.length) {
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(criteria[id], scoreHolder, ScoreAccess::increment); // CraftBukkit - Get our scores instead
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            Entity entity = damageSource.getEntity();
            if (!( // Paper - split the if statement. If below statement is false, hurtServer would not have been evaluated. Return false.
             !(entity instanceof Player player && !this.canHarmPlayer(player))
                && !(entity instanceof AbstractArrow abstractArrow && abstractArrow.getOwner() instanceof Player player1 && !this.canHarmPlayer(player1))
            )) return false; // Paper - split the if statement. If below statement is false, hurtServer would not have been evaluated. Return false.
            // Paper start - cancellable death events
            this.queueHealthUpdatePacket = true;
            boolean damaged = super.hurtServer(level, damageSource, amount);
            this.queueHealthUpdatePacket = false;
            if (this.queuedHealthUpdatePacket != null) {
                this.connection.send(this.queuedHealthUpdatePacket);
                this.queuedHealthUpdatePacket = null;
            }
            return damaged;
            // Paper end - cancellable death events
        }
    }

    @Override
    public boolean canHarmPlayer(Player other) {
        return this.isPvpAllowed() && super.canHarmPlayer(other);
    }

    private boolean isPvpAllowed() {
        return this.level().isPvpAllowed();
    }

    // Paper start
    public record RespawnResult(TeleportTransition transition, boolean isBedSpawn, boolean isAnchorSpawn) {
    }

    public @Nullable TeleportTransition findRespawnPositionAndUseSpawnBlock(boolean useCharge, TeleportTransition.PostTeleportTransition postTeleportTransition, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason respawnReason) {
        RespawnResult result = this.findRespawnPositionAndUseSpawnBlock0(useCharge, postTeleportTransition, respawnReason);
        return result == null ? null : result.transition();
    }

    public @Nullable RespawnResult findRespawnPositionAndUseSpawnBlock0(boolean useCharge, TeleportTransition.PostTeleportTransition postTeleportTransition, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason respawnReason) {
        TeleportTransition teleportTransition;
        boolean isBedSpawn = false;
        boolean isAnchorSpawn = false;
        Runnable consumeAnchorCharge = null;
        // Paper end
        ServerPlayer.RespawnConfig respawnConfig = this.getRespawnConfig();
        ServerLevel level = this.server.getLevel(ServerPlayer.RespawnConfig.getDimensionOrDefault(respawnConfig));
        if (level != null && respawnConfig != null) {
            Optional<ServerPlayer.RespawnPosAngle> optional = findRespawnAndUseSpawnBlock(level, respawnConfig, useCharge);
            if (optional.isPresent()) {
                ServerPlayer.RespawnPosAngle respawnPosAngle = optional.get();
                // CraftBukkit start
                isBedSpawn = respawnPosAngle.isBedSpawn();
                isAnchorSpawn = respawnPosAngle.isAnchorSpawn();
                consumeAnchorCharge = respawnPosAngle.consumeAnchorCharge();
                teleportTransition = new TeleportTransition(
                    level, respawnPosAngle.position(), Vec3.ZERO, respawnPosAngle.yaw(), respawnPosAngle.pitch(), postTeleportTransition
                );
                // CraftBukkit end
            } else {
                teleportTransition = TeleportTransition.missingRespawnBlock(this, postTeleportTransition); // CraftBukkit
            }
        } else {
            // CraftBukkit start
            teleportTransition = TeleportTransition.createDefault(this, postTeleportTransition);
        }

        org.bukkit.entity.Player respawnPlayer = this.getBukkitEntity();
        org.bukkit.Location location = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(
            teleportTransition.position(),
            teleportTransition.newLevel(),
            teleportTransition.yRot(),
            teleportTransition.xRot()
        );

        // Paper start - respawn flags
        org.bukkit.event.player.PlayerRespawnEvent respawnEvent = new org.bukkit.event.player.PlayerRespawnEvent(
            respawnPlayer,
            location,
            isBedSpawn,
            isAnchorSpawn,
            teleportTransition.missingRespawnBlock(),
            respawnReason
        );
        // Paper end - respawn flags
        this.level().getCraftServer().getPluginManager().callEvent(respawnEvent);
        // Spigot start
        if (this.connection.isDisconnected()) {
            return null;
        }
        // Spigot end

        // Paper start - consume anchor charge if location hasn't changed
        if (location.equals(respawnEvent.getRespawnLocation()) && consumeAnchorCharge != null) {
            consumeAnchorCharge.run();
        }
        // Paper end - consume anchor charge if location hasn't changed
        location = respawnEvent.getRespawnLocation();

        return new RespawnResult(
            new TeleportTransition(
                ((org.bukkit.craftbukkit.CraftWorld) location.getWorld()).getHandle(),
                org.bukkit.craftbukkit.util.CraftLocation.toVec3(location),
                teleportTransition.deltaMovement(),
                location.getYaw(),
                location.getPitch(),
                teleportTransition.missingRespawnBlock(),
                teleportTransition.asPassenger(),
                teleportTransition.relatives(),
                teleportTransition.postTeleportTransition(),
                teleportTransition.cause()
            ),
            isBedSpawn,
            isAnchorSpawn
        );
        // CraftBukkit end
    }

    public boolean isReceivingWaypoints() {
        return this.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE) > 0.0;
    }

    @Override
    protected void onAttributeUpdated(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.WAYPOINT_RECEIVE_RANGE)) {
            me.earthme.luminol.utils.FoliaServerWaypointManager waypointManager = this.level().getWaypointManager(); // Luminol - Restore waypoints
            if (this.getAttributes().getValue(attribute) > 0.0) {
                waypointManager.addPlayer(this);
            } else {
                waypointManager.removePlayer(this);
            }
        }

        super.onAttributeUpdated(attribute);
    }

    public static Optional<ServerPlayer.RespawnPosAngle> findRespawnAndUseSpawnBlock(
        ServerLevel level, ServerPlayer.RespawnConfig respawnConfig, boolean useCharge
    ) {
        LevelData.RespawnData respawnData = respawnConfig.respawnData;
        BlockPos blockPos = respawnData.pos();
        float yaw = respawnData.yaw();
        float pitch = respawnData.pitch();
        boolean flag = respawnConfig.forced;
        BlockState blockState = level.getBlockState(blockPos);
        Block block = blockState.getBlock();
        if (block instanceof RespawnAnchorBlock
            && (flag || blockState.getValue(RespawnAnchorBlock.CHARGE) > 0)
            && RespawnAnchorBlock.canSetSpawn(level, blockPos)) {
            Optional<Vec3> optional = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, blockPos);
            Runnable consumeAnchorCharge = null; // Paper - Fix SPIGOT-5989 (don't use charge until after respawn event)
            if (!flag && useCharge && optional.isPresent()) {
                consumeAnchorCharge = () -> level.setBlock(blockPos, blockState.setValue(RespawnAnchorBlock.CHARGE, blockState.getValue(RespawnAnchorBlock.CHARGE) - 1), Block.UPDATE_ALL); // Paper - Fix SPIGOT-5989 (don't use charge until after respawn event)
            }
            final Runnable finalConsumeAnchorCharge = consumeAnchorCharge; // Paper - Fix SPIGOT-5989

            return optional.map(pos -> ServerPlayer.RespawnPosAngle.of(pos, blockPos, 0.0F, false, true, finalConsumeAnchorCharge)); // Paper - Fix SPIGOT-5989 (don't use charge until after respawn event)
        } else if (block instanceof BedBlock && level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, blockPos).canSetSpawn(level)) {
            return BedBlock.findStandUpPosition(EntityType.PLAYER, level, blockPos, blockState.getValue(BedBlock.FACING), yaw)
                .map(pos -> ServerPlayer.RespawnPosAngle.of(pos, blockPos, 0.0F, true, false, null)); // Paper - Fix SPIGOT-5989
        } else if (!flag) {
            return Optional.empty();
        } else {
            boolean isPossibleToRespawnInThis = block.isPossibleToRespawnInThis(blockState);
            BlockState blockState1 = level.getBlockState(blockPos.above());
            boolean isPossibleToRespawnInThis1 = blockState1.getBlock().isPossibleToRespawnInThis(blockState1);
            return isPossibleToRespawnInThis && isPossibleToRespawnInThis1
                ? Optional.of(new ServerPlayer.RespawnPosAngle(new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.1, blockPos.getZ() + 0.5), yaw, pitch, false, false, null)) // Paper - Fix SPIGOT-5989
                : Optional.empty();
        }
    }

    public void showEndCredits() {
        this.unRide();
        this.level().removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
        if (!this.wonGame) {
            this.wonGame = true;
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 0.0F));
            this.seenCredits = true;
        }
    }

    // Folia start - region threading
    /**
     * Teleport flag indicating that the player is to be respawned, expected to only be used
     * internally for {@link #respawn(java.util.function.Consumer, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason)}
     */
    public static final long TELEPORT_FLAGS_PLAYER_RESPAWN    = Long.MIN_VALUE >>> 0;

    public void exitEndCredits() {
        if (!this.wonGame) {
            // not in the end credits anymore
            return;
        }
        this.wonGame = false;

        this.respawn((player) -> {
            CriteriaTriggers.CHANGED_DIMENSION.trigger(player, Level.END, Level.OVERWORLD);
        }, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.END_PORTAL, true);
    }

    public void respawn(java.util.function.Consumer<ServerPlayer> respawnComplete, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason reason) {
        this.respawn(respawnComplete, reason, false);
    }

    private void respawn(java.util.function.Consumer<ServerPlayer> respawnComplete, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason reason, boolean alive) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this, "Cannot respawn entity async");

        this.getBukkitEntity(); // force bukkit entity to be created before TPing

        if (alive != this.isAlive()) {
            throw new IllegalStateException("isAlive expected = " + alive);
        }

        if (!this.hasNullCallback()) {
            this.unRide();
        }

        if (this.isVehicle() || this.isPassenger()) {
            throw new IllegalStateException("Dead player should not be a vehicle or passenger");
        }

        ServerLevel origin = this.level();
        ServerPlayer.RespawnConfig respawnConfig = this.getRespawnConfig();
        ServerLevel respawnWorld = this.server.getLevel(ServerPlayer.RespawnConfig.getDimensionOrDefault(respawnConfig));

        // modified based off PlayerList#respawn

        EntityTreeNode passengerTree = this.makePassengerTree();

        this.isChangingDimension = true;
        origin.removePlayerImmediately(this, RemovalReason.CHANGED_DIMENSION);
        // reset player if needed, only after removal from world
        if (!alive) {
            ServerPlayer.this.reset();
        }
        // must be manually removed from connections, delay until after reset() so that we do not trip any thread checks
        this.level().getCurrentWorldData().connections.remove(this.connection.connection);

        ca.spottedleaf.concurrentutil.completable.CallbackCompletable<org.bukkit.Location> spawnPosComplete =
            new ca.spottedleaf.concurrentutil.completable.CallbackCompletable<>();
        boolean[] usedRespawnAnchor = new boolean[1];

        // set up post spawn location logic
        spawnPosComplete.addWaiter((spawnLoc, throwable) -> {
            // update pos and velocity
            ServerPlayer.this.setPosRaw(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());
            ServerPlayer.this.setYRot(spawnLoc.getYaw());
            ServerPlayer.this.setYHeadRot(spawnLoc.getYaw());
            ServerPlayer.this.setXRot(spawnLoc.getPitch());
            ServerPlayer.this.setDeltaMovement(Vec3.ZERO);
            // placeInAsync will update the world

            this.placeInAsync(
                origin,
                // use the load chunk flag just in case the spawn loc isn't loaded, and to ensure the chunks
                // stay loaded for a bit with the teleport ticket
                ((org.bukkit.craftbukkit.CraftWorld)spawnLoc.getWorld()).getHandle(),
                TELEPORT_FLAG_LOAD_CHUNK | TELEPORT_FLAGS_PLAYER_RESPAWN,
                passengerTree, // note: we expect this to just be the player, no passengers
                (entity) -> {
                    // now the player is in the world, and can receive sound
                    if (usedRespawnAnchor[0]) {
                        ServerPlayer.this.connection.send(
                            new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                                net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_DEPLETE, net.minecraft.sounds.SoundSource.BLOCKS,
                                ServerPlayer.this.getX(), ServerPlayer.this.getY(), ServerPlayer.this.getZ(),
                                1.0F, 1.0F, ServerPlayer.this.level().getRandom().nextLong()
                            )
                        );
                    }
                    ServerPlayer.this.connection.restartClientLoadTimerAfterRespawn();
                    // now the respawn logic is complete

                    // last, call the function callback
                    if (respawnComplete != null) {
                        respawnComplete.accept(ServerPlayer.this);
                    }
                    // Luminol - Add missing teleportation apis
                    new me.earthme.luminol.api.entity.player.PostPlayerRespawnEvent(ServerPlayer.this.getBukkitEntity()).callEvent();
                    // Luminol end
                }
            );
        });

        // find and modify respawn block state
        if (respawnWorld == null || respawnConfig == null) {
            // default to regular spawn
            fudgeSpawnLocation(this.server.getLevel(Level.OVERWORLD), spawnPosComplete);
        } else {
            // load chunk for block
            // give at least 1 radius of loaded chunks so that we do not sync load anything
            int radiusBlocks = 16;
            respawnWorld.moonrise$loadChunksAsync(respawnConfig.respawnData.pos(), radiusBlocks,
                ca.spottedleaf.concurrentutil.util.Priority.HIGHER,
                (chunks) -> {
                    ServerPlayer.RespawnPosAngle spawnPos = ServerPlayer.findRespawnAndUseSpawnBlock(
                        respawnWorld, respawnConfig, !alive
                    ).orElse(null);
                    if (spawnPos == null) {
                        // no spawn
                        ServerPlayer.this.connection.send(
                            new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F)
                        );
                        ServerPlayer.this.setRespawnPosition(
                            null, false, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN
                        );
                        // default to regular spawn
                        fudgeSpawnLocation(this.server.getLevel(Level.OVERWORLD), spawnPosComplete);
                        return;
                    }

                    boolean isRespawnAnchor = respawnWorld.getBlockState(respawnConfig.respawnData.pos()).is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR);
                    boolean isBed = respawnWorld.getBlockState(respawnConfig.respawnData.pos()).is(net.minecraft.tags.BlockTags.BEDS);
                    usedRespawnAnchor[0] = !alive && isRespawnAnchor;

                    // finished now, pass the location on
                    spawnPosComplete.complete(
                        io.papermc.paper.util.MCUtil.toLocation(respawnWorld, spawnPos.position(), spawnPos.yaw(), 0.0f)
                    );
                    return;
                }
            );
        }
    }

    @Override
    protected void teleportSyncSameRegion(Vec3 pos, Float yaw, Float pitch, Vec3 velocity) {
        if (yaw != null) {
            this.setYRot(yaw.floatValue());
            this.setYHeadRot(yaw.floatValue());
        }
        if (pitch != null) {
            this.setXRot(pitch.floatValue());
        }
        if (velocity != null) {
            this.setDeltaMovement(velocity);
        }
        this.connection.internalTeleport(
            new net.minecraft.world.entity.PositionMoveRotation(
                pos, this.getDeltaMovement(), this.getYRot(), this.getXRot()
            ),
            java.util.Collections.emptySet()
        );
        this.connection.resetPosition();
        this.setOldPosAndRot();
        this.resetStoredPositions();
    }

    @Override
    protected ServerPlayer transformForAsyncTeleport(ServerLevel destination, Vec3 pos, Float yaw, Float pitch, Vec3 velocity) {
        // must be manually removed from connections
        this.level().getCurrentWorldData().connections.remove(this.connection.connection);
        this.level().removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);

        this.spawnIn(destination);
        this.transform(pos, yaw, pitch, velocity);

        return this;
    }

    @Override
    public void preChangeDimension() {
        super.preChangeDimension();
        this.stopUsingItem();
    }

    @Override
    protected void placeSingleSync(ServerLevel originWorld, ServerLevel destination, EntityTreeNode treeNode, long teleportFlags) {
        if (destination == originWorld && (teleportFlags & TELEPORT_FLAGS_PLAYER_RESPAWN) == 0L) {
            this.unsetRemoved();
            destination.addDuringTeleport(this);

            // must be manually added to connections
            this.level().getCurrentWorldData().connections.add(this.connection.connection);

            // required to set up the pending teleport stuff to the client, and to actually update
            // the player's position clientside
            this.connection.internalTeleport(
                new net.minecraft.world.entity.PositionMoveRotation(
                    this.position(), this.getDeltaMovement(), this.getYRot(), this.getXRot()
                ),
                java.util.Collections.emptySet()
            );
            this.connection.resetPosition();

            this.postChangeDimension();
        } else {
            // Modelled after PlayerList#respawn

            // We avoid checking for disconnection here, which means we do not have to add/remove from
            // the player list here. We can let this be properly handled by the connection handler

            // pre-add logic
            PlayerList playerlist = this.server.getPlayerList();
            net.minecraft.world.level.storage.LevelData worlddata = destination.getLevelData();
            this.connection.send(
                new ClientboundRespawnPacket(
                    this.createCommonSpawnInfo(destination),
                    (teleportFlags & TELEPORT_FLAGS_PLAYER_RESPAWN) == 0L ? (byte)1 : (byte)0
                )
            );
            // don't bother with the chunk cache radius and simulation distance packets, they are handled
            // by the chunk loader
            this.spawnIn(destination); // important that destination != null
            // we can delay teleport until later, the player position is already set up at the target
            this.setShiftKeyDown(false);

            this.connection.send(new net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket(
                destination.getRespawnData()
            ));
            this.connection.send(new ClientboundChangeDifficultyPacket(
                worlddata.getDifficulty(), worlddata.isDifficultyLocked()
            ));
            this.connection.send(new ClientboundSetExperiencePacket(
                this.experienceProgress, this.totalExperience, this.experienceLevel
            ));

            playerlist.sendActivePlayerEffects(this);
            playerlist.sendLevelInfo(this, destination);
            playerlist.sendPlayerPermissionLevel(this);

            // regular world add logic
            this.unsetRemoved();
            destination.addDuringTeleport(this);

            // must be manually added to connections
            this.level().getCurrentWorldData().connections.add(this.connection.connection);

            // required to set up the pending teleport stuff to the client, and to actually update
            // the player's position clientside
            this.connection.internalTeleport(
                new net.minecraft.world.entity.PositionMoveRotation(
                    this.position(), this.getDeltaMovement(), this.getYRot(), this.getXRot()
                ),
                java.util.Collections.emptySet()
            );
            this.connection.resetPosition();
            this.stopUsingItem();

            // delay callback until after post add logic

            // post add logic

            // "Added from changeDimension"
            this.setHealth(this.getHealth());
            playerlist.sendAllPlayerInfo(this);
            this.onUpdateAbilities();
            /*for (MobEffectInstance mobEffect : this.getActiveEffects()) {
                this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), mobEffect, false));
            }*/ // handled by sendActivePlayerEffects

            this.lastSentExp = -1;
            this.lastSentHealth = -1.0F;
            this.lastSentFood = -1;

            this.triggerDimensionChangeTriggers(originWorld);

            // finished

            this.postChangeDimension();
        }
    }

    @Override
    public boolean endPortalLogicAsync(BlockPos portalPos) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this, "Cannot portal entity async");

        if (this.level().getTypeKey() == net.minecraft.world.level.dimension.LevelStem.END) {
            if (!this.canPortalAsync(null, false)) {
                return false;
            }
            this.wonGame = true;
            // TODO is there a better solution to this that DOESN'T skip the credits?
            this.seenCredits = true;
            if (!this.seenCredits) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 0.0F));
            }
            this.exitEndCredits();
            return true;
        } else {
            return super.endPortalLogicAsync(portalPos);
        }
    }

    @Override
    protected void prePortalLogic(ServerLevel origin, ServerLevel destination, PortalType type) {
        super.prePortalLogic(origin, destination, type);
        if (origin.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.OVERWORLD && destination.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.NETHER) {
            this.enteredNetherPosition = this.position();
        }
    }
    // Folia end - region threading

    @Override
    public @Nullable ServerPlayer teleport(TeleportTransition teleportTransition) {
        // Folia start - region threading
        if (true) {
            throw new UnsupportedOperationException("Must use teleportAsync while in region threading");
        }
        // Folia end - region threading
        if (this.isSleeping()) return null; // CraftBukkit - SPIGOT-3154
        if (this.isRemoved()) {
            return null;
        } else {
            if (teleportTransition.missingRespawnBlock()) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            }

            ServerLevel level = teleportTransition.newLevel();
            ServerLevel serverLevel = this.level();
            // CraftBukkit start
            ResourceKey<net.minecraft.world.level.dimension.LevelStem> resourceKey = serverLevel.getTypeKey();

            org.bukkit.Location enter = this.getBukkitEntity().getLocation();
            PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(this), PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
            org.bukkit.Location exit = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(absolutePosition.position(), level, absolutePosition.yRot(), absolutePosition.xRot());
            final org.bukkit.event.player.PlayerTeleportEvent tpEvent;
            // Paper start - gateway-specific teleport event
            if (this.portalProcess != null && this.portalProcess.isSamePortal(((net.minecraft.world.level.block.EndGatewayBlock) net.minecraft.world.level.block.Blocks.END_GATEWAY)) && this.level().getBlockEntity(this.portalProcess.getEntryPosition()) instanceof net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
                tpEvent = new com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent(this.getBukkitEntity(), enter, exit.clone(), new org.bukkit.craftbukkit.block.CraftEndGateway(this.level().getWorld(), theEndGatewayBlockEntity));
            } else {
                tpEvent = new org.bukkit.event.player.PlayerTeleportEvent(this.getBukkitEntity(), enter, exit.clone(), teleportTransition.cause());
            }
            // Paper end - gateway-specific teleport event
            org.bukkit.Bukkit.getServer().getPluginManager().callEvent(tpEvent);
            org.bukkit.Location newExit = tpEvent.getTo();
            if (tpEvent.isCancelled()) {
                return null;
            }
            if (!newExit.equals(exit)) {
                level = ((org.bukkit.craftbukkit.CraftWorld) newExit.getWorld()).getHandle();
                teleportTransition = new TeleportTransition(
                    level,
                    org.bukkit.craftbukkit.util.CraftLocation.toVec3(newExit),
                    Vec3.ZERO,
                    newExit.getYaw(),
                    newExit.getPitch(),
                    teleportTransition.missingRespawnBlock(),
                    teleportTransition.asPassenger(),
                    Set.of(),
                    teleportTransition.postTeleportTransition(),
                    teleportTransition.cause());
            }
            // CraftBukkit end
            if (!teleportTransition.asPassenger()) {
                this.removeVehicle();
            }

            // CraftBukkit start
            if (level.dimension() == serverLevel.dimension()) {
                this.connection.internalTeleport(PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
                // CraftBukkit end
                this.connection.resetPosition();
                teleportTransition.postTeleportTransition().onTransition(this);
                return this;
            } else {
                this.isChangingDimension = true;
                LevelData levelData = level.getLevelData();
                this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(level), ClientboundRespawnPacket.KEEP_ALL_DATA));
                this.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
                PlayerList playerList = this.server.getPlayerList();
                playerList.sendPlayerPermissionLevel(this);
                this.portalProcess = null; // SPIGOT-7785: there is no need to carry this over as it contains the old world/location and we might run into trouble if there is a portal in the same spot in both worlds
                serverLevel.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();
                ProfilerFiller profilerFiller = Profiler.get();
                profilerFiller.push("moving");
                if (resourceKey == net.minecraft.world.level.dimension.LevelStem.OVERWORLD && level.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.NETHER) { // CraftBukkit - empty to fall through to null to event
                    this.enteredNetherPosition = this.position();
                }

                profilerFiller.pop();
                profilerFiller.push("placing");
                this.setServerLevel(level);
                this.connection.internalTeleport(PositionMoveRotation.of(teleportTransition), teleportTransition.relatives()); // CraftBukkit - use internal teleport without event
                this.connection.resetPosition();
                level.addDuringTeleport(this);
                profilerFiller.pop();
                this.triggerDimensionChangeTriggers(serverLevel);
                this.stopUsingItem();
                this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
                playerList.sendLevelInfo(this, level);
                playerList.sendAllPlayerInfo(this);
                playerList.sendActivePlayerEffects(this);
                teleportTransition.postTeleportTransition().onTransition(this);
                this.lastSentExp = -1;
                this.lastSentHealth = -1.0F;
                this.lastSentFood = -1;
                this.teleportSpectators(teleportTransition, serverLevel);
                // CraftBukkit start
                org.bukkit.event.player.PlayerChangedWorldEvent changeEvent = new org.bukkit.event.player.PlayerChangedWorldEvent(this.getBukkitEntity(), serverLevel.getWorld());
                this.level().getCraftServer().getPluginManager().callEvent(changeEvent);
                // CraftBukkit end
                // Paper start - Reset shield blocking on dimension change
                if (this.isBlocking()) {
                    this.stopUsingItem();
                }
                // Paper end - Reset shield blocking on dimension change
                return this;
            }
        }
    }

    @Override
    public void forceSetRotation(float yRot, boolean yRelative, float xRot, boolean xRelative) {
        super.forceSetRotation(yRot, yRelative, xRot, xRelative);
        this.connection.send(new ClientboundPlayerRotationPacket(yRot, yRelative, xRot, xRelative));
    }

    public void triggerDimensionChangeTriggers(ServerLevel level) {
        ResourceKey<Level> resourceKey = level.dimension();
        ResourceKey<Level> resourceKey1 = this.level().dimension();
        // CraftBukkit start
        ResourceKey<Level> maindimensionkey = org.bukkit.craftbukkit.util.CraftDimensionUtil.getMainDimensionKey(level);
        ResourceKey<Level> maindimensionkey1 = org.bukkit.craftbukkit.util.CraftDimensionUtil.getMainDimensionKey(this.level());
        // Paper start - Add option for strict advancement dimension checks
        if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.strictAdvancementDimensionCheck) {
            maindimensionkey = resourceKey;
            maindimensionkey1 = resourceKey1;
        }
        // Paper end - Add option for strict advancement dimension checks
        CriteriaTriggers.CHANGED_DIMENSION.trigger(this, maindimensionkey, maindimensionkey1);
        if (maindimensionkey != resourceKey || maindimensionkey1 != resourceKey1) {
            CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourceKey, resourceKey1);
        }

        if (maindimensionkey == Level.NETHER && maindimensionkey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
            // CraftBukkit end
            CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (maindimensionkey1 != Level.NETHER) { // CraftBukkit
            this.enteredNetherPosition = null;
        }
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return player.isSpectator() ? this.getCamera() == this : !this.isSpectator() && super.broadcastToPlayer(player);
    }

    @Override
    public void take(Entity entity, int quantity) {
        super.take(entity, quantity);
        this.containerMenu.broadcastChanges();
    }

    // CraftBukkit start - moved bed result checks from below into separate method
    private Either<Player.BedSleepingProblem, Unit> getBedResult(BlockPos bedPos, Direction direction) {
        BedRule bedRule = this.level().environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, bedPos);
        if (bedRule.explodes()) {
            return Either.left(Player.BedSleepingProblem.EXPLOSION);
        } else if (!this.isSleeping() && this.isAlive()) {
            boolean canSleep = bedRule.canSleep(this.level());
            boolean canSetSpawn = bedRule.canSetSpawn(this.level());
            if (!canSetSpawn && !canSleep) {
                return Either.left(bedRule.asProblem());
            } else if (!this.bedInRange(bedPos, direction)) {
                return Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
            } else if (this.bedBlocked(bedPos, direction)) {
                return Either.left(Player.BedSleepingProblem.OBSTRUCTED);
            } else {
                if (canSetSpawn) {
                    this.setRespawnPosition(
                        new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(this.level().dimension(), bedPos, this.getYRot(), this.getXRot()), false), true, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.BED // Paper - Add PlayerSetSpawnEvent
                    );
                }

                if (!canSleep) {
                    return Either.left(bedRule.asProblem());
                } else {
                    if (!this.isCreative()) {
                        double d = 8.0;
                        double d1 = 5.0;
                        Vec3 vec3 = Vec3.atBottomCenterOf(bedPos);
                        List<Monster> entitiesOfClass = this.level()
                            .getEntitiesOfClass(
                                Monster.class,
                                new AABB(vec3.x() - 8.0, vec3.y() - 5.0, vec3.z() - 8.0, vec3.x() + 8.0, vec3.y() + 5.0, vec3.z() + 8.0),
                                monster -> monster.isPreventingPlayerRest(this.level(), this)
                            );
                        if (!entitiesOfClass.isEmpty()) {
                            return Either.left(Player.BedSleepingProblem.NOT_SAFE);
                        }
                    }

                    // CraftBukkit start
                    return Either.right(Unit.INSTANCE);
                }
            }
        } else {
            return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    @Override
    public Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos bedPos, boolean force) {
        Direction direction = this.level().getBlockState(bedPos).getValue(HorizontalDirectionalBlock.FACING);
        Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> bedResult = this.getBedResult(bedPos, direction);

        if (bedResult.left().orElse(null) == net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM) {
            return bedResult; // return immediately if the result is not bypassable by plugins
        }

        if (force) {
            bedResult = Either.right(Unit.INSTANCE);
        }

        bedResult = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedEnterEvent(this, bedPos, bedResult);
        if (bedResult.left().isPresent()) {
            return bedResult;
        }

        {
            {
                    Either<Player.BedSleepingProblem, Unit> either = super.startSleepInBed(bedPos, force).ifRight(unit -> {
                        // CraftBukkit end
                        this.awardStat(Stats.SLEEP_IN_BED);
                        CriteriaTriggers.SLEPT_IN_BED.trigger(this);
                    });
                    if (!this.level().canSleepThroughNights()) {
                        this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
                    }

                    this.level().updateSleepingPlayerList();
                    return either;
            }
        }
    }

    @Override
    public void startSleeping(BlockPos pos) {
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        super.startSleeping(pos);
    }

    private boolean bedInRange(BlockPos pos, Direction direction) {
        return this.isReachableBedBlock(pos) || this.isReachableBedBlock(pos.relative(direction.getOpposite()));
    }

    private boolean isReachableBedBlock(BlockPos pos) {
        Vec3 vec3 = Vec3.atBottomCenterOf(pos);
        return Math.abs(this.getX() - vec3.x()) <= 3.0 && Math.abs(this.getY() - vec3.y()) <= 2.0 && Math.abs(this.getZ() - vec3.z()) <= 3.0;
    }

    private boolean bedBlocked(BlockPos pos, Direction direction) {
        BlockPos blockPos = pos.above();
        return !this.freeAt(blockPos) || !this.freeAt(blockPos.relative(direction.getOpposite()));
    }

    @Override
    public void stopSleepInBed(boolean wakeImmediately, boolean updateLevelForSleepingPlayers) {
        if (!this.isSleeping()) return; // CraftBukkit - Can't leave bed if not in one!
        // CraftBukkit start - fire PlayerBedLeaveEvent
        org.bukkit.block.Block bed = org.bukkit.craftbukkit.block.CraftBlock.at(this.level(), this.getSleepingPos().orElse(this.blockPosition()));
        org.bukkit.event.player.PlayerBedLeaveEvent event = new org.bukkit.event.player.PlayerBedLeaveEvent(this.getBukkitEntity(), bed, true);
        if (!event.callEvent()) {
            return;
        }
        // CraftBukkit end
        if (this.isSleeping()) {
            this.level().getChunkSource().sendToTrackingPlayersAndSelf(this, new ClientboundAnimatePacket(this, ClientboundAnimatePacket.WAKE_UP));
        }

        super.stopSleepInBed(wakeImmediately, updateLevelForSleepingPlayers);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.EXIT_BED); // CraftBukkit
        }
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        return (super.isInvulnerableTo(level, damageSource) // Paper - disable player cramming;
            || this.isChangingDimension() && !damageSource.is(DamageTypes.ENDER_PEARL)
            || !this.connection.hasClientLoaded()) || (!this.level().paperConfig().collisions.allowPlayerCrammingDamage && damageSource.is(DamageTypes.CRAMMING)); // Paper - disable player cramming;
    }

    @Override
    protected void onChangedBlock(ServerLevel level, BlockPos pos) {
        if (!this.isSpectator()) {
            super.onChangedBlock(level, pos);
        }
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (this.spawnExtraParticlesOnFall && onGround && this.fallDistance > 0.0) {
            Vec3 vec3 = pos.getCenter().add(0.0, 0.5, 0.0);
            int i = (int)Mth.clamp(50.0 * this.fallDistance, 0.0, 200.0);
            this.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), vec3.x, vec3.y, vec3.z, i, 0.3F, 0.3F, 0.3F, 0.15F);
            this.spawnExtraParticlesOnFall = false;
        }

        super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public void onExplosionHit(@Nullable Entity entity) {
        super.onExplosionHit(entity);
        this.currentImpulseImpactPos = this.position();
        this.currentExplosionCause = entity;
        this.setIgnoreFallDamageFromCurrentImpulse(entity != null && entity.getType() == EntityType.WIND_CHARGE);
    }

    @Override
    protected void pushEntities() {
        if (this.level().tickRateManager().runsNormally()) {
            super.pushEntities();
        }
    }

    @Override
    public void openTextEdit(SignBlockEntity signEntity, boolean isFrontText) {
        this.connection.send(new ClientboundBlockUpdatePacket(this.level(), signEntity.getBlockPos()));
        this.connection.send(new ClientboundOpenSignEditorPacket(signEntity.getBlockPos(), isFrontText));
    }

    @Override
    public void openDialog(Holder<Dialog> dialog) {
        this.connection.send(new ClientboundShowDialogPacket(dialog));
    }

    public int nextContainerCounter() { // CraftBukkit - void -> int
        this.containerCounter = this.containerCounter % 100 + 1;
        return this.containerCounter; // CraftBukkit
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        if (menu == null) {
            return OptionalInt.empty();
        } else {
            if (false && this.containerMenu != this.inventoryMenu) { // CraftBukkit - SPIGOT-6552: Handle inventory closing in CraftEventFactory#callInventoryOpenEventWithTitle(...)
                this.closeContainer();
            }

            this.nextContainerCounter();
            AbstractContainerMenu abstractContainerMenu = menu.createMenu(this.containerCounter, this.getInventory(), this);
            Component title = null; // Paper - Add titleOverride to InventoryOpenEvent
            // CraftBukkit start - Inventory open hook
            if (abstractContainerMenu != null) {
                abstractContainerMenu.setTitle(menu.getDisplayName());

                boolean cancelled = false;
                // Paper start - Add titleOverride to InventoryOpenEvent
                final com.mojang.datafixers.util.Pair<net.kyori.adventure.text.Component, AbstractContainerMenu> result = org.bukkit.craftbukkit.event.CraftEventFactory.callInventoryOpenEventWithTitle(this, abstractContainerMenu, cancelled);
                abstractContainerMenu = result.getSecond();
                title = io.papermc.paper.adventure.PaperAdventure.asVanilla(result.getFirst());
                // Paper end - Add titleOverride to InventoryOpenEvent
                if (abstractContainerMenu == null && !cancelled) { // Let pre-cancelled events fall through
                    // SPIGOT-5263 - close chest if cancelled
                    if (menu instanceof Container container) {
                        container.stopOpen(this);
                    } else if (menu instanceof net.minecraft.world.level.block.ChestBlock.DoubleInventory doubleInventory) {
                        // SPIGOT-5355 - double chests too :(
                        doubleInventory.container.stopOpen(this);
                        // Paper start - Fix InventoryOpenEvent cancellation
                    } else if (!this.enderChestInventory.isActiveChest(null)) {
                        this.enderChestInventory.stopOpen(this);
                        // Paper end - Fix InventoryOpenEvent cancellation
                    }
                    return OptionalInt.empty();
                }
            }
            // CraftBukkit end
            if (abstractContainerMenu == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                // CraftBukkit start
                this.containerMenu = abstractContainerMenu; // Moved up
                if (!this.isImmobile())
                this.connection
                    .send(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(abstractContainerMenu.containerId, abstractContainerMenu.getType(), java.util.Objects.requireNonNullElseGet(title, abstractContainerMenu::getTitle))); // Paper - Add titleOverride to InventoryOpenEven
                // CraftBukkit end
                this.initMenu(abstractContainerMenu);
                // CraftBukkit - moved up
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Override
    public void sendMerchantOffers(int containerId, MerchantOffers offers, int level, int xp, boolean showProgress, boolean canRestock) {
        this.connection.send(new ClientboundMerchantOffersPacket(containerId, offers, level, xp, showProgress, canRestock));
    }

    @Override
    public void openHorseInventory(AbstractHorse horse, Container inventory) {
        // CraftBukkit start - Inventory open hook
        this.nextContainerCounter(); // Moved up from below
        AbstractContainerMenu container = new HorseInventoryMenu(this.containerCounter, this.getInventory(), inventory, horse, horse.getInventoryColumns());
        container.setTitle(horse.getDisplayName());
        container = org.bukkit.craftbukkit.event.CraftEventFactory.callInventoryOpenEvent(this, container);

        if (container == null) {
            inventory.stopOpen(this);
            return;
        }
        // CraftBukkit end
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.OPEN_NEW); // Paper - Inventory close reason
        }

        // this.nextContainerCounter(); // CraftBukkit - moved up
        int inventoryColumns = horse.getInventoryColumns();
        this.connection.send(new ClientboundMountScreenOpenPacket(this.containerCounter, inventoryColumns, horse.getId()));
        this.containerMenu = container; // CraftBukkit
        this.initMenu(this.containerMenu);
    }

    @Override
    public void openNautilusInventory(AbstractNautilus nautilus, Container inventory) {
        // Paper start - Inventory open hook
        this.nextContainerCounter(); // moved up from below
        AbstractContainerMenu menu = new NautilusInventoryMenu(this.containerCounter, this.getInventory(), inventory, nautilus, nautilus.getInventoryColumns());
        menu.setTitle(nautilus.getDisplayName());
        menu = org.bukkit.craftbukkit.event.CraftEventFactory.callInventoryOpenEvent(this, menu);

        if (menu == null) {
            inventory.stopOpen(this);
            return;
        }
        // Paper end
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        // this.nextContainerCounter(); Paper - Move Up
        int inventoryColumns = nautilus.getInventoryColumns();
        this.connection.send(new ClientboundMountScreenOpenPacket(this.containerCounter, inventoryColumns, nautilus.getId()));
        this.containerMenu = menu; // Paper - Moved up declaration
        this.initMenu(this.containerMenu);
    }

    @Override
    public void openItemGui(ItemStack stack, InteractionHand hand) {
        if (stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            if (WrittenBookContent.resolveForItem(stack, this.createCommandSourceStack(), this)) {
                this.containerMenu.broadcastChanges();
            }

            this.connection.send(new ClientboundOpenBookPacket(hand));
        }
    }

    @Override
    public void openCommandBlock(CommandBlockEntity commandBlock) {
        this.connection.send(ClientboundBlockEntityDataPacket.create(commandBlock, BlockEntity::saveCustomOnly));
    }

    @Override
    public void closeContainer() {
        // Paper start - Inventory close reason
        this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNKNOWN);
    }
    @Override
    public void closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        org.bukkit.craftbukkit.event.CraftEventFactory.handleInventoryCloseEvent(this, reason); // CraftBukkit
        // Paper end - Inventory close reason
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.doCloseContainer();
    }

    // Paper start - special close for unloaded inventory
    @Override
    public void closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        // copied from above
        org.bukkit.craftbukkit.event.CraftEventFactory.handleInventoryCloseEvent(this, reason); // CraftBukkit
        // Paper end
        // copied from below
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.containerMenu = this.inventoryMenu;
        // do not run close logic
    }
    // Paper end - special close for unloaded inventory

    @Override
    public void doCloseContainer() {
        this.containerMenu.removed(this);
        this.inventoryMenu.transferState(this.containerMenu);
        this.containerMenu = this.inventoryMenu;
    }

    @Override
    public void rideTick() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.rideTick();
        this.checkRidingStatistics(this.getX() - x, this.getY() - y, this.getZ() - z);
    }

    public void checkMovementStatistics(double dx, double dy, double dz) {
        if (!this.isPassenger() && !didNotMove(dx, dy, dz)) {
            if (this.isSwimming()) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                if (rounded > 0) {
                    this.awardStat(Stats.SWIM_ONE_CM, rounded);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.SWIM); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isEyeInFluid(FluidTags.WATER)) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                if (rounded > 0) {
                    this.awardStat(Stats.WALK_UNDER_WATER_ONE_CM, rounded);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK_UNDERWATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isInWater()) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (rounded > 0) {
                    this.awardStat(Stats.WALK_ON_WATER_ONE_CM, rounded);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK_ON_WATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.onClimbable()) {
                if (dy > 0.0) {
                    this.awardStat(Stats.CLIMB_ONE_CM, (int)Math.round(dy * 100.0));
                }
            } else if (this.onGround()) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (rounded > 0) {
                    if (this.isSprinting()) {
                        this.awardStat(Stats.SPRINT_ONE_CM, rounded);
                        this.causeFoodExhaustion(this.level().spigotConfig.sprintMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.SPRINT); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else if (this.isCrouching()) {
                        this.awardStat(Stats.CROUCH_ONE_CM, rounded);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.CROUCH); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else {
                        this.awardStat(Stats.WALK_ONE_CM, rounded);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK); // CraftBukkit - EntityExhaustionEvent // Spigot
                    }
                }
            } else if (this.isFallFlying()) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                this.awardStat(Stats.AVIATE_ONE_CM, rounded);
            } else {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (rounded > 25) {
                    this.awardStat(Stats.FLY_ONE_CM, rounded);
                }
            }
        }
    }

    public void checkRidingStatistics(double dx, double dy, double dz) { // Luminol - Fix riding statics desync (make public)
        if (this.isPassenger() && !didNotMove(dx, dy, dz)) {
            int rounded = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
            Entity vehicle = this.getVehicle();
            if (vehicle instanceof AbstractMinecart) {
                this.awardStat(Stats.MINECART_ONE_CM, rounded);
            } else if (vehicle instanceof AbstractBoat) {
                this.awardStat(Stats.BOAT_ONE_CM, rounded);
            } else if (vehicle instanceof Pig) {
                this.awardStat(Stats.PIG_ONE_CM, rounded);
            } else if (vehicle instanceof AbstractHorse) {
                this.awardStat(Stats.HORSE_ONE_CM, rounded);
            } else if (vehicle instanceof Strider) {
                this.awardStat(Stats.STRIDER_ONE_CM, rounded);
            } else if (vehicle instanceof HappyGhast) {
                this.awardStat(Stats.HAPPY_GHAST_ONE_CM, rounded);
            } else if (vehicle instanceof AbstractNautilus) {
                this.awardStat(Stats.NAUTILUS_ONE_CM, rounded);
            }
        }
    }

    private static boolean didNotMove(double dx, double dy, double dz) {
        return dx == 0.0 && dy == 0.0 && dz == 0.0;
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
        this.stats.increment(this, stat, amount);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(stat, this, scoreAccess -> scoreAccess.add(amount)); // CraftBukkit - Get our scores instead
    }

    @Override
    public void resetStat(Stat<?> stat) {
        this.stats.setValue(this, stat, 0);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(stat, this, ScoreAccess::reset); // CraftBukkit - Get our scores instead
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.addRecipes(recipes, this);
    }

    @Override
    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> items) {
        CriteriaTriggers.RECIPE_CRAFTED.trigger(this, recipe.id(), items);
    }

    @Override
    public void awardRecipesByKey(List<ResourceKey<Recipe<?>>> recipes) {
        List<RecipeHolder<?>> list = recipes.stream()
            .flatMap(key -> this.server.getRecipeManager().byKey((ResourceKey<Recipe<?>>)key).stream())
            .collect(Collectors.toList());
        this.awardRecipes(list);
    }

    @Override
    public int resetRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.removeRecipes(recipes, this);
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        this.awardStat(Stats.JUMP);
        if (this.isSprinting()) {
            this.causeFoodExhaustion(this.level().spigotConfig.jumpSprintExhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.JUMP_SPRINT); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
        } else {
            this.causeFoodExhaustion(this.level().spigotConfig.jumpWalkExhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.JUMP); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
        }
    }

    @Override
    public void giveExperiencePoints(int xpPoints) {
        if (xpPoints != 0) {
            super.giveExperiencePoints(xpPoints);
            this.lastSentExp = -1;
        }
    }

    public void disconnect() {
        this.disconnected = true;
        this.ejectPassengers();

        // Paper start - Workaround vehicle not tracking the passenger disconnection dismount
        if (this.isPassenger() && this.getVehicle() instanceof ServerPlayer) {
            this.stopRiding();
        }
        // Paper end - Workaround vehicle not tracking the passenger disconnection dismount

        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }
    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
        this.lastSentExp = -1; // CraftBukkit - Added to reset
    }

    @Override
    public void displayClientMessage(Component message, boolean overlay) {
        this.sendSystemMessage(message, overlay);
    }

    @Override
    public void completeUsingItem() {
        if (!this.useItem.isEmpty() && this.isUsingItem()) {
            this.connection.send(new ClientboundEntityEventPacket(this, EntityEvent.USE_ITEM_COMPLETE));
            super.completeUsingItem();
        }
    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target) {
        super.lookAt(anchor, target);
        this.connection.send(new ClientboundPlayerLookAtPacket(anchor, target.x, target.y, target.z));
    }

    public void lookAt(EntityAnchorArgument.Anchor fromAnchor, Entity entity, EntityAnchorArgument.Anchor toAnchor) {
        Vec3 vec3 = toAnchor.apply(entity);
        super.lookAt(fromAnchor, vec3);
        this.connection.send(new ClientboundPlayerLookAtPacket(fromAnchor, entity, toAnchor));
    }

    public void restoreFrom(ServerPlayer that, boolean keepEverything) {
        this.wardenSpawnTracker = that.wardenSpawnTracker;
        this.chatSession = that.chatSession;
        this.gameMode.setGameModeForPlayer(that.gameMode.getGameModeForPlayer(), that.gameMode.getPreviousGameModeForPlayer());
        this.onUpdateAbilities();
        this.getAttributes().assignBaseValues(that.getAttributes());
        if (keepEverything) {
            // this.getAttributes().assignPermanentModifiers(that.getAttributes()); // CraftBukkit
            this.setHealth(that.getHealth());
            this.foodData = that.foodData;

            for (MobEffectInstance mobEffectInstance : that.getActiveEffects()) {
                // this.addEffect(new MobEffectInstance(mobEffectInstance)); // CraftBukkit
            }

            this.transferInventoryXpAndScore(that);
            this.portalProcess = that.portalProcess;
        } else {
            // this.setHealth(this.getMaxHealth()); // CraftBukkit
            if (this.level().getGameRules().get(GameRules.KEEP_INVENTORY) || that.isSpectator()) {
                this.transferInventoryXpAndScore(that);
            }
        }

        this.enchantmentSeed = that.enchantmentSeed;
        this.enderChestInventory = that.enderChestInventory;
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, that.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
        this.lastSentExp = -1;
        this.lastSentHealth = -1.0F;
        this.lastSentFood = -1;
        // this.recipeBook.copyOverData(that.recipeBook); // CraftBukkit
        this.seenCredits = that.seenCredits;
        this.enteredNetherPosition = that.enteredNetherPosition;
        this.chunkTrackingView = that.chunkTrackingView;
        this.requestedDebugSubscriptions = that.requestedDebugSubscriptions;
        this.setShoulderEntityLeft(that.getShoulderEntityLeft());
        this.setShoulderEntityRight(that.getShoulderEntityRight());
        this.setLastDeathLocation(that.getLastDeathLocation());
        this.waypointIcon().copyFrom(that.waypointIcon());
    }

    private void transferInventoryXpAndScore(Player player) {
        this.getInventory().replaceWith(player.getInventory());
        this.experienceLevel = player.experienceLevel;
        this.totalExperience = player.totalExperience;
        this.experienceProgress = player.experienceProgress;
        this.setScore(player.getScore());
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effectInstance, @Nullable Entity entity) {
        super.onEffectAdded(effectInstance, entity);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, true));
        if (effectInstance.is(MobEffects.LEVITATION)) {
            this.levitationStartTime = this.tickCount;
            this.levitationStartPos = this.position();
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, entity);
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effectInstance, boolean forced, @Nullable Entity entity) {
        super.onEffectUpdated(effectInstance, forced, entity);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, false));
        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, entity);
    }

    @Override
    protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
        super.onEffectsRemoved(effects);

        for (MobEffectInstance mobEffectInstance : effects) {
            this.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), mobEffectInstance.getEffect()));
            if (mobEffectInstance.is(MobEffects.LEVITATION)) {
                this.levitationStartPos = null;
            }
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, null);
    }

    // Paper start - use dismount cause
    @Override
    public void dismountTo(double x, double y, double z) {
        this.teleportTo(x, y, z, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.DISMOUNT);
    }
    // Paper end - use dismount cause

    @Override
    public void teleportTo(double x, double y, double z) {
        // Paper start - pass cause
        this.teleportTo(x, y, z, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public void teleportTo(double x, double y, double z, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        this.connection.teleport(new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, 0.0F, 0.0F), Relative.union(Relative.DELTA, Relative.ROTATION), cause);
        // Paper end - pass cause
    }

    @Override
    public void teleportRelative(double dx, double dy, double dz) {
        this.connection.teleport(new PositionMoveRotation(new Vec3(dx, dy, dz), Vec3.ZERO, 0.0F, 0.0F), Relative.ALL);
    }

    @Override
    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<Relative> relativeMovements, float yaw, float pitch, boolean setCamera, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) { // CraftBukkit
        if (this.isSleeping()) {
            this.stopSleepInBed(true, true);
        }

        if (setCamera) {
            this.setCamera(this);
        }

        boolean flag = super.teleportTo(level, x, y, z, relativeMovements, yaw, pitch, setCamera, cause); // CraftBukkit
        if (flag) {
            this.setYHeadRot(relativeMovements.contains(Relative.Y_ROT) ? this.getYHeadRot() + yaw : yaw);
            this.connection.resetFlyingTicks();
        }

        return flag;
    }

    @Override
    public void snapTo(double x, double y, double z) {
        super.snapTo(x, y, z);
        this.connection.resetPosition();
    }

    @Override
    public void crit(Entity target) {
        this.level().getChunkSource().sendToTrackingPlayersAndSelf(this, new ClientboundAnimatePacket(target, ClientboundAnimatePacket.CRITICAL_HIT));
    }

    @Override
    public void magicCrit(Entity target) {
        this.level().getChunkSource().sendToTrackingPlayersAndSelf(this, new ClientboundAnimatePacket(target, ClientboundAnimatePacket.MAGIC_CRITICAL_HIT));
    }

    @Override
    public void onUpdateAbilities() {
        if (this.connection != null) {
            this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
            this.updateInvisibilityStatus();
        }
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel)super.level();
    }

    public boolean setGameMode(GameType gameMode) {
        // Paper start - Expand PlayerGameModeChangeEvent
        org.bukkit.event.player.PlayerGameModeChangeEvent event = this.setGameMode(gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.UNKNOWN, null);
        return event != null && !event.isCancelled();
    }
    public org.bukkit.event.player.@Nullable PlayerGameModeChangeEvent setGameMode(GameType gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause cause, net.kyori.adventure.text.@Nullable Component message) {
        boolean isSpectator = this.isSpectator();
        org.bukkit.event.player.PlayerGameModeChangeEvent event = this.gameMode.changeGameModeForPlayer(gameMode, cause, message);
        if (event == null) {
            return null;
        } else if (event.isCancelled()) {
            return event; // need to return the event for the cancel message
            // Paper end - Expand PlayerGameModeChangeEvent
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, gameMode.getId()));
            if (gameMode == GameType.SPECTATOR) {
                this.removeEntitiesOnShoulder();
                this.stopRiding();
                this.stopUsingItem();
                EnchantmentHelper.stopLocationBasedEffects(this);
            } else {
                this.setCamera(this);
                if (isSpectator) {
                    EnchantmentHelper.runLocationChangedEffects(this.level(), this);
                }
            }

            this.onUpdateAbilities();
            this.updateEffectVisibility();
            return event; // Paper - Expand PlayerGameModeChangeEvent
        }
    }

    @Override
    public GameType gameMode() {
        return this.gameMode.getGameModeForPlayer();
    }

    public CommandSource commandSource() {
        return this.commandSource;
    }

    public CommandSourceStack createCommandSourceStack() {
        return new CommandSourceStack(
            this.commandSource(),
            this.position(),
            this.getRotationVector(),
            this.level(),
            this.permissions(),
            this.getPlainTextName(),
            this.getDisplayName(),
            this.server,
            this
        );
    }

    public void sendSystemMessage(Component message) {
        this.sendSystemMessage(message, false);
    }

    public void sendSystemMessage(Component message, boolean overlay) {
        if (this.acceptsSystemMessages(overlay)) {
            this.connection
                .send(
                    new ClientboundSystemChatPacket(message, overlay),
                    PacketSendListener.exceptionallySend(
                        () -> {
                            if (this.acceptsSystemMessages(false)) {
                                int i = 256;
                                String string = message.getString(256);
                                Component component = Component.literal(string).withStyle(ChatFormatting.YELLOW);
                                return new ClientboundSystemChatPacket(
                                    Component.translatable("multiplayer.message_not_delivered", component).withStyle(ChatFormatting.RED), false
                                );
                            } else {
                                return null;
                            }
                        }
                    )
                );
        }
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean filtered, ChatType.Bound boundChatType) {
        // Paper start
        this.sendChatMessage(message, filtered, boundChatType, null);
    }
    public void sendChatMessage(OutgoingChatMessage message, boolean filtered, ChatType.Bound boundChatType, @Nullable Component unsigned) {
        // Paper end
        if (this.acceptsChatMessages()) {
            message.sendToPlayer(this, filtered, boundChatType, unsigned); // Paper
        }
    }

    public String getIpAddress() {
        return this.connection.getRemoteAddress() instanceof InetSocketAddress inetSocketAddress
            ? InetAddresses.toAddrString(inetSocketAddress.getAddress())
            : "<unknown>";
    }

    public void updateOptions(ClientInformation clientInformation) {
        // Paper start - settings event
        new com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent(this.getBukkitEntity(), Util.make(new java.util.IdentityHashMap<>(), map -> {
            map.put(com.destroystokyo.paper.ClientOption.LOCALE, clientInformation.language());
            map.put(com.destroystokyo.paper.ClientOption.VIEW_DISTANCE, clientInformation.viewDistance());
            map.put(com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY, com.destroystokyo.paper.ClientOption.ChatVisibility.valueOf(clientInformation.chatVisibility().name()));
            map.put(com.destroystokyo.paper.ClientOption.CHAT_COLORS_ENABLED, clientInformation.chatColors());
            map.put(com.destroystokyo.paper.ClientOption.SKIN_PARTS, new com.destroystokyo.paper.PaperSkinParts(clientInformation.modelCustomisation()));
            map.put(com.destroystokyo.paper.ClientOption.MAIN_HAND, clientInformation.mainHand() == net.minecraft.world.entity.HumanoidArm.LEFT ? org.bukkit.inventory.MainHand.LEFT : org.bukkit.inventory.MainHand.RIGHT);
            map.put(com.destroystokyo.paper.ClientOption.TEXT_FILTERING_ENABLED, clientInformation.textFilteringEnabled());
            map.put(com.destroystokyo.paper.ClientOption.ALLOW_SERVER_LISTINGS, clientInformation.allowsListing());
            map.put(com.destroystokyo.paper.ClientOption.PARTICLE_VISIBILITY, com.destroystokyo.paper.ClientOption.ParticleVisibility.valueOf(clientInformation.particleStatus().name()));
        })).callEvent();
        // Paper end - settings event
        // CraftBukkit start
        if (this.getMainArm() != clientInformation.mainHand()) {
            org.bukkit.event.player.PlayerChangedMainHandEvent event = new org.bukkit.event.player.PlayerChangedMainHandEvent(
                this.getBukkitEntity(),
                clientInformation.mainHand() == net.minecraft.world.entity.HumanoidArm.LEFT ? org.bukkit.inventory.MainHand.LEFT : org.bukkit.inventory.MainHand.RIGHT
            );
            this.server.server.getPluginManager().callEvent(event);
        }
        if (this.language == null || !this.language.equals(clientInformation.language())) { // Paper
            org.bukkit.event.player.PlayerLocaleChangeEvent event = new org.bukkit.event.player.PlayerLocaleChangeEvent(
                this.getBukkitEntity(),
                clientInformation.language()
            );
            this.server.server.getPluginManager().callEvent(event);
        }
        // CraftBukkit end
        // Paper start - don't call options events on login
        this.updateOptionsNoEvents(clientInformation);
    }
    public void updateOptionsNoEvents(ClientInformation clientInformation) {
        // Paper end
        this.language = clientInformation.language();
        this.adventure$locale = java.util.Objects.requireNonNullElse(net.kyori.adventure.translation.Translator.parseLocale(this.language), java.util.Locale.US); // Paper
        this.requestedViewDistance = clientInformation.viewDistance();
        this.chatVisibility = clientInformation.chatVisibility();
        this.canChatColor = clientInformation.chatColors();
        this.textFilteringEnabled = clientInformation.textFilteringEnabled();
        this.allowsListing = clientInformation.allowsListing();
        this.particleStatus = clientInformation.particleStatus();
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte)clientInformation.modelCustomisation());
        this.getEntityData().set(DATA_PLAYER_MAIN_HAND, clientInformation.mainHand());
    }

    public ClientInformation clientInformation() {
        int i = this.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION);
        return new ClientInformation(
            this.language,
            this.requestedViewDistance,
            this.chatVisibility,
            this.canChatColor,
            i,
            this.getMainArm(),
            this.textFilteringEnabled,
            this.allowsListing,
            this.particleStatus
        );
    }

    public boolean canChatInColor() {
        return this.canChatColor;
    }

    public ChatVisiblity getChatVisibility() {
        return this.chatVisibility;
    }

    private boolean acceptsSystemMessages(boolean overlay) {
        return this.chatVisibility != ChatVisiblity.HIDDEN || overlay;
    }

    private boolean acceptsChatMessages() {
        return this.chatVisibility == ChatVisiblity.FULL;
    }

    public int requestedViewDistance() {
        return this.requestedViewDistance;
    }

    public void sendServerStatus(ServerStatus serverStatus) {
        this.connection.send(new ClientboundServerDataPacket(serverStatus.description(), serverStatus.favicon().map(ServerStatus.Favicon::iconBytes)));
    }

    @Override
    public PermissionSet permissions() {
        return this.server.getProfilePermissions(this.nameAndId());
    }

    public void resetLastActionTime() {
        this.lastActionTime = Util.getMillis();
    }

    public ServerStatsCounter getStats() {
        return this.stats;
    }

    public ServerRecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    @Override
    protected void updateInvisibilityStatus() {
        if (this.isSpectator()) {
            this.removeEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateInvisibilityStatus();
        }
    }

    public Entity getCamera() {
        return (Entity)(this.camera == null ? this : this.camera);
    }

    // Folia start - region threading
    private void teleportToCameraOffRegion() {
        Entity cameraFinal = this.camera;
        // use the task scheduler, as we don't know where the caller is invoking from
        if (this != cameraFinal) {
            this.getBukkitEntity().taskScheduler.schedule((final ServerPlayer newPlayer) -> {
                io.papermc.paper.threadedregions.TeleportUtils.teleport(
                        newPlayer, false, cameraFinal, null, null, 0L,
                        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE, null,
                        (final ServerPlayer newerPlayer) -> {
                            return newerPlayer.camera == cameraFinal;
                        }
                );
            }, null, 1L);
        } // else: do not bother teleporting to self
    }
    // Folia end - region threading

    public void setCamera(@Nullable Entity entityToSpectate) {
        // Folia start - region threading
        if (entityToSpectate != null && (entityToSpectate != this && !entityToSpectate.canBeSpectated())) {
            return;
        }
        // Folia end - region threading
        Entity camera = this.getCamera();
        this.camera = (Entity)(entityToSpectate == null ? this : entityToSpectate);
        if (camera != this.camera) {
            // Paper start - Add PlayerStartSpectatingEntityEvent and PlayerStopSpectatingEntity
            if (this.camera == this) {
                com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent playerStopSpectatingEntityEvent = new com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent(this.getBukkitEntity(), camera.getBukkitEntity());
                if (!playerStopSpectatingEntityEvent.callEvent()) {
                    this.camera = camera; // rollback camera entity again
                    return;
                }
            } else {
                com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent playerStartSpectatingEntityEvent = new com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent(this.getBukkitEntity(), camera.getBukkitEntity(), entityToSpectate.getBukkitEntity());
                if (!playerStartSpectatingEntityEvent.callEvent()) {
                    this.camera = camera; // rollback camera entity again
                    return;
                }
            }
            // Paper end - Add PlayerStartSpectatingEntityEvent and PlayerStopSpectatingEntity
            // Folia - region threading - move down

            // Folia - region threading - not needed

            // Folia start - region threading - handle camera setting better
            if (this.camera == this
                || (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.camera) && this.camera.moonrise$getTrackedEntity() != null
                    && this.camera.moonrise$getTrackedEntity().seenBy.contains(this.connection))) {
                // Folia end - region threading - handle camera setting better
            this.connection.send(new ClientboundSetCameraPacket(this.camera));
            } // Folia - region threading - handle camera setting better
            //this.connection.resetPosition(); // Folia - region threading - not needed
            this.teleportToCameraOffRegion(); // Folia - region threading - moved down
        }
    }

    @Override
    protected void processPortalCooldown() {
        if (!this.isChangingDimension) {
            super.processPortalCooldown();
        }
    }

    @Override
    public void attack(Entity target) {
        if (this.isSpectator()) {
            this.setCamera(target);
        } else {
            super.attack(target);
        }
    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    public @Nullable Component getTabListDisplayName() {
        return this.listName; // CraftBukkit
    }

    public int getTabListOrder() {
        return this.listOrder; // CraftBukkit
    }

    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand);
        this.resetAttackStrengthTicker();
    }

    public boolean isChangingDimension() {
        return this.isChangingDimension;
    }

    public void hasChangedDimension() {
        this.isChangingDimension = false;
    }

    public PlayerAdvancements getAdvancements() {
        return this.advancements;
    }

    public ServerPlayer.@Nullable RespawnConfig getRespawnConfig() {
        return this.respawnConfig;
    }

    public void copyRespawnPosition(ServerPlayer player) {
        this.setRespawnPosition(player.respawnConfig, false);
    }

    public void setRespawnPosition(ServerPlayer.@Nullable RespawnConfig respawnConfig, boolean displayInChat) {
        // Paper start - Add PlayerSetSpawnEvent
        this.setRespawnPosition(respawnConfig, displayInChat, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.UNKNOWN);
    }

    public boolean setRespawnPosition(ServerPlayer.@Nullable RespawnConfig respawnConfig, boolean displayInChat, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause cause) {
        org.bukkit.Location spawnLoc = null;
        boolean actuallyDisplayInChat = false;
        if (respawnConfig != null) {
            actuallyDisplayInChat = displayInChat && !respawnConfig.isSamePosition(this.respawnConfig);
            spawnLoc = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(respawnConfig.respawnData().pos(), this.server.getLevel(respawnConfig.respawnData().dimension()));
            spawnLoc.setYaw(respawnConfig.respawnData().yaw());
            spawnLoc.setPitch(respawnConfig.respawnData().pitch());
        }
        org.bukkit.event.player.PlayerSpawnChangeEvent dumbEvent = new org.bukkit.event.player.PlayerSpawnChangeEvent(
            this.getBukkitEntity(),
            spawnLoc,
            respawnConfig != null && respawnConfig.forced(),
            cause == com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN
                ? org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.RESET
                : org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.valueOf(cause.name())
        );
        dumbEvent.callEvent();

        com.destroystokyo.paper.event.player.PlayerSetSpawnEvent event = new com.destroystokyo.paper.event.player.PlayerSetSpawnEvent(
            this.getBukkitEntity(),
            cause,
            dumbEvent.getNewSpawn(),
            dumbEvent.isForced(),
            actuallyDisplayInChat,
            actuallyDisplayInChat ? io.papermc.paper.adventure.PaperAdventure.asAdventure(SPAWN_SET_MESSAGE) : null
        );
        event.setCancelled(dumbEvent.isCancelled());
        if (!event.callEvent()) {
            return false;
        }

        if (event.getLocation() != null) {
            respawnConfig = new ServerPlayer.RespawnConfig(
                net.minecraft.world.level.storage.LevelData.RespawnData.of(
                    ((org.bukkit.craftbukkit.CraftWorld) event.getLocation().getWorld()).getHandle().dimension(),
                    org.bukkit.craftbukkit.util.CraftLocation.toBlockPosition(event.getLocation()),
                    event.getLocation().getYaw(),
                    event.getLocation().getPitch()
                ),
                event.isForced()
            );
            if (event.willNotifyPlayer() && event.getNotification() != null) {
                this.sendSystemMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.getNotification()));
            }
        }

        this.respawnConfig = respawnConfig;
        return true;
        // Paper end - Add PlayerSetSpawnEvent
    }

    public SectionPos getLastSectionPos() {
        return this.lastSectionPos;
    }

    public void setLastSectionPos(SectionPos sectionPos) {
        this.lastSectionPos = sectionPos;
    }

    public ChunkTrackingView getChunkTrackingView() {
        return this.chunkTrackingView;
    }

    public void setChunkTrackingView(ChunkTrackingView chunkTrackingView) {
        this.chunkTrackingView = chunkTrackingView;
    }

    @Override
    public ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean traceItem, boolean callEvent, java.util.function.@Nullable Consumer<org.bukkit.entity.Item> entityOperation) { // Paper - Extend dropItem API
        ItemEntity itemEntity = super.drop(droppedItem, dropAround, traceItem, callEvent, entityOperation); // Paper - Extend dropItem API
        ItemStack itemStack = itemEntity != null ? itemEntity.getItem() : ItemStack.EMPTY; // Paper - move up
        if (traceItem) {
            if (!itemStack.isEmpty()) {
                this.awardStat(Stats.ITEM_DROPPED.get(itemStack.getItem()), itemStack.getCount()); // Paper - use size from dropped item
                this.awardStat(Stats.DROP);
            }
        }
        // Paper start - remove player from map on drop
        if (itemStack.is(net.minecraft.world.item.Items.FILLED_MAP)) {
            final MapItemSavedData mapData = MapItem.getSavedData(itemStack, this.level());
            if (mapData != null) {
                mapData.tickCarriedBy(this, itemStack);
            }
        }
        // Paper end - remove player from map on drop
        return itemEntity;
    }

    public TextFilter getTextFilter() {
        return this.textFilter;
    }

    public void setServerLevel(ServerLevel level) {
        this.setLevel(level);
        this.gameMode.setLevel(level);
    }

    private static @Nullable GameType readPlayerMode(ValueInput input, String key) {
        return input.read(key, GameType.LEGACY_ID_CODEC).orElse(null);
    }

    private GameType calculateGameModeForNewPlayer(@Nullable GameType gameType) {
        GameType forcedGameType = this.server.getForcedGameType();
        if (forcedGameType != null) {
            return forcedGameType;
        } else {
            return gameType != null ? gameType : this.server.getDefaultGameType();
        }
    }

    private void storeGameTypes(ValueOutput output) {
        output.store("playerGameType", GameType.LEGACY_ID_CODEC, this.gameMode.getGameModeForPlayer());
        GameType previousGameModeForPlayer = this.gameMode.getPreviousGameModeForPlayer();
        output.storeNullable("previousPlayerGameType", GameType.LEGACY_ID_CODEC, previousGameModeForPlayer);
    }

    @Override
    public boolean isTextFilteringEnabled() {
        return this.textFilteringEnabled;
    }

    public boolean shouldFilterMessageTo(ServerPlayer player) {
        return player != this && (this.textFilteringEnabled || player.textFilteringEnabled);
    }

    @Override
    public boolean mayInteract(ServerLevel level, BlockPos pos) {
        return super.mayInteract(level, pos) && level.mayInteract(this, pos);
    }

    @Override
    protected void updateUsingItem(ItemStack usingItem) {
        CriteriaTriggers.USING_ITEM.trigger(this, usingItem);
        super.updateUsingItem(usingItem);
    }

    public boolean drop(boolean dropStack) { // Paper - add back success return
        Inventory inventory = this.getInventory();
        ItemStack itemStack = inventory.removeFromSelected(dropStack);
        this.containerMenu
            .findSlot(inventory, inventory.getSelectedSlot())
            .ifPresent(slot -> this.containerMenu.setRemoteSlot(slot, inventory.getSelectedItem()));
        if (this.useItem.isEmpty()) {
            this.stopUsingItem();
        }

        return this.drop(itemStack, false, true) != null; // Paper - add back success return
    }

    @Override
    public void handleExtraItemsCreatedOnUse(ItemStack stack) {
        if (!this.getInventory().add(stack)) {
            this.drop(stack, false);
        }
    }

    public boolean allowsListing() {
        return this.allowsListing;
    }

    @Override
    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.of(this.wardenSpawnTracker);
    }

    public void setSpawnExtraParticlesOnFall(boolean spawnExtraParticlesOnFall) {
        this.spawnExtraParticlesOnFall = spawnExtraParticlesOnFall;
    }

    @Override
    public void onItemPickup(ItemEntity itemEntity) {
        super.onItemPickup(itemEntity);
        Entity owner = itemEntity.getOwner();
        if (owner != null) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.trigger(this, itemEntity.getItem(), owner);
        }
    }

    public void setChatSession(RemoteChatSession chatSession) {
        this.chatSession = chatSession;
    }

    public @Nullable RemoteChatSession getChatSession() {
        return this.chatSession != null && this.chatSession.hasExpired() ? null : this.chatSession;
    }

    @Override
    public void indicateDamage(double xDistance, double zDistance) {
        this.hurtDir = (float)(Mth.atan2(zDistance, xDistance) * 180.0F / (float)Math.PI - this.getYRot());
        this.connection.send(new ClientboundHurtAnimationPacket(this));
    }

    @Override
    public boolean startRiding(Entity entity, boolean force, boolean triggerEvents) {
        if (super.startRiding(entity, force, triggerEvents)) {
            entity.positionRider(this);
            this.connection.teleport(new PositionMoveRotation(this.position(), Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
            if (entity instanceof LivingEntity livingEntity) {
                this.server.getPlayerList().sendActiveEffects(livingEntity, this.connection);
            }

            this.connection.send(new ClientboundSetPassengersPacket(entity));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void removeVehicle(final boolean suppressCancellation) { // Paper - Force entity dismount during teleportation
        Entity vehicle = this.getVehicle();
        super.removeVehicle(suppressCancellation); // Paper - Force entity dismount during teleportation
        if (vehicle instanceof LivingEntity livingEntity) {
            for (MobEffectInstance mobEffectInstance : livingEntity.getActiveEffects()) {
                this.connection.send(new ClientboundRemoveMobEffectPacket(vehicle.getId(), mobEffectInstance.getEffect()));
            }
        }

        if (vehicle != null) {
            this.connection.send(new ClientboundSetPassengersPacket(vehicle));
        }
    }

    public CommonPlayerSpawnInfo createCommonSpawnInfo(ServerLevel level) {
        return new CommonPlayerSpawnInfo(
            level.dimensionTypeRegistration(),
            level.dimension(),
            BiomeManager.obfuscateSeed(level.getSeed()),
            this.gameMode.getGameModeForPlayer(),
            this.gameMode.getPreviousGameModeForPlayer(),
            level.isDebug(),
            level.isFlat(),
            this.getLastDeathLocation(),
            this.getPortalCooldown(),
            level.getSeaLevel()
        );
    }

    public void setRaidOmenPosition(BlockPos raidOmenPosition) {
        this.raidOmenPosition = raidOmenPosition;
    }

    public void clearRaidOmenPosition() {
        this.raidOmenPosition = null;
    }

    public @Nullable BlockPos getRaidOmenPosition() {
        return this.raidOmenPosition;
    }

    @Override
    public Vec3 getKnownMovement() {
        Entity vehicle = this.getVehicle();
        return vehicle != null && vehicle.getControllingPassenger() != this ? vehicle.getKnownMovement() : this.lastKnownClientMovement;
    }

    @Override
    public Vec3 getKnownSpeed() {
        Entity vehicle = this.getVehicle();
        return vehicle != null && vehicle.getControllingPassenger() != this ? vehicle.getKnownSpeed() : this.lastKnownClientMovement;
    }

    public void setKnownMovement(Vec3 knownMovement) {
        this.lastKnownClientMovement = knownMovement;
    }

    @Override
    protected float getEnchantedDamage(Entity entity, float damage, DamageSource damageSource) {
        return EnchantmentHelper.modifyDamage(this.level(), this.getWeaponItem(), entity, damageSource, damage);
    }

    @Override
    public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
        super.onEquippedItemBroken(item, slot);
        this.awardStat(Stats.ITEM_BROKEN.get(item));
    }

    public Input getLastClientInput() {
        return this.lastClientInput;
    }

    public void setLastClientInput(Input lastClientInput) {
        this.lastClientInput = lastClientInput;
    }

    public Vec3 getLastClientMoveIntent() {
        float f = this.lastClientInput.left() == this.lastClientInput.right() ? 0.0F : (this.lastClientInput.left() ? 1.0F : -1.0F);
        float f1 = this.lastClientInput.forward() == this.lastClientInput.backward() ? 0.0F : (this.lastClientInput.forward() ? 1.0F : -1.0F);
        return getInputVector(new Vec3(f, 0.0, f1), 1.0F, this.getYRot());
    }

    public void registerEnderPearl(ThrownEnderpearl enderPearl) {
        //this.enderPearls.add(enderPearl); // Folia - region threading - do not track ender pearls
    }

    public void deregisterEnderPearl(ThrownEnderpearl enderPearl) {
        //this.enderPearls.remove(enderPearl); // Folia - region threading - do not track ender pearls
    }

    public Set<ThrownEnderpearl> getEnderPearls() {
        return this.enderPearls;
    }

    public CompoundTag getShoulderEntityLeft() {
        return this.shoulderEntityLeft;
    }

    public void setShoulderEntityLeft(CompoundTag tag) {
        this.shoulderEntityLeft = tag;
        this.setShoulderParrotLeft(extractParrotVariant(tag));
    }

    public CompoundTag getShoulderEntityRight() {
        return this.shoulderEntityRight;
    }

    public void setShoulderEntityRight(CompoundTag tag) {
        this.shoulderEntityRight = tag;
        this.setShoulderParrotRight(extractParrotVariant(tag));
    }

    public long registerAndUpdateEnderPearlTicket(ThrownEnderpearl enderPearl) {
        if (enderPearl.level() instanceof ServerLevel serverLevel) {
            ChunkPos chunkPos = enderPearl.chunkPosition();
            this.registerEnderPearl(enderPearl);
            serverLevel.resetEmptyTime();
            return placeEnderPearlTicket(serverLevel, chunkPos) - 1L;
        } else {
            return 0L;
        }
    }

    public static long placeEnderPearlTicket(ServerLevel level, ChunkPos pos) {
        if (!level.paperConfig().misc.legacyEnderPearlBehavior) level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, pos, 2); // Paper - Allow using old ender pearl behavior
        return TicketType.ENDER_PEARL.timeout();
    }

    public void requestDebugSubscriptions(Set<DebugSubscription<?>> subscriptions) {
        this.requestedDebugSubscriptions = Set.copyOf(subscriptions);
    }

    public Set<DebugSubscription<?>> debugSubscriptions() {
        // return !this.server.debugSubscribers().hasRequiredPermissions(this) ? Set.of() : this.requestedDebugSubscriptions; // Luminol - Do not enable any debug subscriptions
        return Set.of(); // Luminol - Do not enable any debug subscriptions
    }

    public record RespawnConfig(LevelData.RespawnData respawnData, boolean forced) {
        public static final Codec<ServerPlayer.RespawnConfig> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    LevelData.RespawnData.MAP_CODEC.forGetter(ServerPlayer.RespawnConfig::respawnData),
                    Codec.BOOL.optionalFieldOf("forced", false).forGetter(ServerPlayer.RespawnConfig::forced)
                )
                .apply(instance, ServerPlayer.RespawnConfig::new)
        );

        static ResourceKey<Level> getDimensionOrDefault(ServerPlayer.@Nullable RespawnConfig respawnConfig) {
            return respawnConfig != null ? respawnConfig.respawnData().dimension() : Level.OVERWORLD;
        }

        public boolean isSamePosition(ServerPlayer.@Nullable RespawnConfig respawnConfig) {
            return respawnConfig != null && this.respawnData.globalPos().equals(respawnConfig.respawnData.globalPos());
        }
    }

    // CraftBukkit start
    public record RespawnPosAngle(Vec3 position, float yaw, float pitch, boolean isBedSpawn, boolean isAnchorSpawn, @Nullable Runnable consumeAnchorCharge) {
        public static ServerPlayer.RespawnPosAngle of(Vec3 position, BlockPos towardsPos, float pitch, boolean isBedSpawn, boolean isAnchorSpawn, @Nullable Runnable consumeAnchorCharge) {
            return new ServerPlayer.RespawnPosAngle(position, calculateLookAtYaw(position, towardsPos), pitch, isBedSpawn, isAnchorSpawn, consumeAnchorCharge);
            // CraftBukkit end
        }

        private static float calculateLookAtYaw(Vec3 position, BlockPos towardsPos) {
            Vec3 vec3 = Vec3.atBottomCenterOf(towardsPos).subtract(position).normalize();
            return (float)Mth.wrapDegrees(Mth.atan2(vec3.z, vec3.x) * 180.0F / (float)Math.PI - 90.0);
        }
    }

    public record SavedPosition(Optional<ResourceKey<Level>> dimension, Optional<Vec3> position, Optional<Vec2> rotation) {
        public static final MapCodec<ServerPlayer.SavedPosition> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Level.RESOURCE_KEY_CODEC.optionalFieldOf("Dimension").forGetter(ServerPlayer.SavedPosition::dimension),
                    Vec3.CODEC.optionalFieldOf("Pos").forGetter(ServerPlayer.SavedPosition::position),
                    Vec2.CODEC.optionalFieldOf("Rotation").forGetter(ServerPlayer.SavedPosition::rotation)
                )
                .apply(instance, ServerPlayer.SavedPosition::new)
        );
        public static final ServerPlayer.SavedPosition EMPTY = new ServerPlayer.SavedPosition(Optional.empty(), Optional.empty(), Optional.empty());
    }

    // CraftBukkit start - Add per-player time and weather.
    public long timeOffset = 0;
    public boolean relativeTime = true;

    public long getPlayerTime() {
        if (this.relativeTime) {
            // Adds timeOffset to the current server time.
            return this.level().getDayTime() + this.timeOffset;
        } else {
            // Adds timeOffset to the beginning of this day.
            return this.level().getDayTime() - (this.level().getDayTime() % 24000) + this.timeOffset;
        }
    }

    public org.bukkit.@Nullable WeatherType weatherType = null;

    public void setPlayerWeather(org.bukkit.WeatherType type, boolean plugin) {
        if (!plugin && this.weatherType != null) {
            return;
        }

        if (plugin) {
            this.weatherType = type;
        }

        if (type == org.bukkit.WeatherType.DOWNFALL) {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
        }
    }

    private float pluginRainPosition;
    private float pluginRainPositionPrevious;

    public void updateWeather(float oldRain, float newRain, float oldThunder, float newThunder) {
        if (this.weatherType == null) {
            // Vanilla
            if (oldRain != newRain) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, newRain));
            }
        } else {
            // Plugin
            if (this.pluginRainPositionPrevious != this.pluginRainPosition) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.pluginRainPosition));
            }
        }

        if (oldThunder != newThunder) {
            if (this.weatherType == org.bukkit.WeatherType.DOWNFALL || this.weatherType == null) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, newThunder));
            } else {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0));
            }
        }
    }

    public void tickWeather() {
        if (this.weatherType == null) return;

        this.pluginRainPositionPrevious = this.pluginRainPosition;
        if (this.weatherType == org.bukkit.WeatherType.DOWNFALL) {
            this.pluginRainPosition += 0.01F;
        } else {
            this.pluginRainPosition -= 0.01F;
        }

        this.pluginRainPosition = Mth.clamp(this.pluginRainPosition, 0.0F, 1.0F);
    }

    public void resetPlayerWeather() {
        this.weatherType = null;
        this.setPlayerWeather(this.level().getLevelData().isRaining() ? org.bukkit.WeatherType.DOWNFALL : org.bukkit.WeatherType.CLEAR, false);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + this.getScoreboardName() + " at " + this.getX() + "," + this.getY() + "," + this.getZ() + ")";
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || (this.connection != null && this.connection.isDisconnected()); // Paper - Fix duplication bugs
    }

    public void reset() {
        float exp = 0;

        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            exp = this.experienceProgress;
            this.newTotalExp = this.totalExperience;
            this.newLevel = this.experienceLevel;
        }

        this.setHealth(this.getMaxHealth());
        this.stopUsingItem(); // CraftBukkit - SPIGOT-6682: Clear active item on reset
        this.setAirSupply(this.getMaxAirSupply()); // Paper - Reset players airTicks on respawn
        this.setRemainingFireTicks(0);
        this.fallDistance = 0;
        this.foodData = new net.minecraft.world.food.FoodData();
        this.experienceLevel = this.newLevel;
        this.totalExperience = this.newTotalExp;
        this.experienceProgress = 0;
        this.deathTime = 0; this.broadcastedDeath = false; // Folia - region threading
        this.setStingerCount(0);
        this.removeStingerTime = 0;
        this.setArrowCount(0, true); // CraftBukkit - ArrowBodyCountChangeEvent
        this.removeArrowTime = 0;
        this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DEATH);
        this.effectsDirty = true;
        this.containerMenu = this.inventoryMenu;
        this.lastHurtByPlayer = null;
        this.lastHurtByMob = null;
        this.combatTracker = new net.minecraft.world.damagesource.CombatTracker(this);
        this.lastSentExp = -1;
        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            this.experienceProgress = exp;
        } else {
            this.giveExperiencePoints(this.newExp);
        }
        this.keepLevel = false;
        this.setDeltaMovement(0, 0, 0); // CraftBukkit - SPIGOT-6948: Reset velocity on death
        this.skipDropExperience = false; // CraftBukkit - SPIGOT-7462: Reset experience drop skip, so that further deaths drop xp
    }

    @Override
    public org.bukkit.craftbukkit.entity.CraftPlayer getBukkitEntity() {
        return (org.bukkit.craftbukkit.entity.CraftPlayer) super.getBukkitEntity();
    }
    // CraftBukkit end
}
