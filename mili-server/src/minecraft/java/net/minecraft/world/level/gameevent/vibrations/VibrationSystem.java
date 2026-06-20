package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface VibrationSystem {
    List<ResourceKey<GameEvent>> RESONANCE_EVENTS = List.of(
        GameEvent.RESONATE_1.key(),
        GameEvent.RESONATE_2.key(),
        GameEvent.RESONATE_3.key(),
        GameEvent.RESONATE_4.key(),
        GameEvent.RESONATE_5.key(),
        GameEvent.RESONATE_6.key(),
        GameEvent.RESONATE_7.key(),
        GameEvent.RESONATE_8.key(),
        GameEvent.RESONATE_9.key(),
        GameEvent.RESONATE_10.key(),
        GameEvent.RESONATE_11.key(),
        GameEvent.RESONATE_12.key(),
        GameEvent.RESONATE_13.key(),
        GameEvent.RESONATE_14.key(),
        GameEvent.RESONATE_15.key()
    );
    int NO_VIBRATION_FREQUENCY = 0;
    ToIntFunction<ResourceKey<GameEvent>> VIBRATION_FREQUENCY_FOR_EVENT = Util.make(new Reference2IntOpenHashMap<>(), map -> {
        map.defaultReturnValue(0);
        map.put(GameEvent.STEP.key(), 1);
        map.put(GameEvent.SWIM.key(), 1);
        map.put(GameEvent.FLAP.key(), 1);
        map.put(GameEvent.PROJECTILE_LAND.key(), 2);
        map.put(GameEvent.HIT_GROUND.key(), 2);
        map.put(GameEvent.SPLASH.key(), 2);
        map.put(GameEvent.ITEM_INTERACT_FINISH.key(), 3);
        map.put(GameEvent.PROJECTILE_SHOOT.key(), 3);
        map.put(GameEvent.INSTRUMENT_PLAY.key(), 3);
        map.put(GameEvent.ENTITY_ACTION.key(), 4);
        map.put(GameEvent.ELYTRA_GLIDE.key(), 4);
        map.put(GameEvent.UNEQUIP.key(), 4);
        map.put(GameEvent.ENTITY_DISMOUNT.key(), 5);
        map.put(GameEvent.EQUIP.key(), 5);
        map.put(GameEvent.ENTITY_INTERACT.key(), 6);
        map.put(GameEvent.SHEAR.key(), 6);
        map.put(GameEvent.ENTITY_MOUNT.key(), 6);
        map.put(GameEvent.ENTITY_DAMAGE.key(), 7);
        map.put(GameEvent.DRINK.key(), 8);
        map.put(GameEvent.EAT.key(), 8);
        map.put(GameEvent.CONTAINER_CLOSE.key(), 9);
        map.put(GameEvent.BLOCK_CLOSE.key(), 9);
        map.put(GameEvent.BLOCK_DEACTIVATE.key(), 9);
        map.put(GameEvent.BLOCK_DETACH.key(), 9);
        map.put(GameEvent.CONTAINER_OPEN.key(), 10);
        map.put(GameEvent.BLOCK_OPEN.key(), 10);
        map.put(GameEvent.BLOCK_ACTIVATE.key(), 10);
        map.put(GameEvent.BLOCK_ATTACH.key(), 10);
        map.put(GameEvent.PRIME_FUSE.key(), 10);
        map.put(GameEvent.NOTE_BLOCK_PLAY.key(), 10);
        map.put(GameEvent.BLOCK_CHANGE.key(), 11);
        map.put(GameEvent.BLOCK_DESTROY.key(), 12);
        map.put(GameEvent.FLUID_PICKUP.key(), 12);
        map.put(GameEvent.BLOCK_PLACE.key(), 13);
        map.put(GameEvent.FLUID_PLACE.key(), 13);
        map.put(GameEvent.ENTITY_PLACE.key(), 14);
        map.put(GameEvent.LIGHTNING_STRIKE.key(), 14);
        map.put(GameEvent.TELEPORT.key(), 14);
        map.put(GameEvent.ENTITY_DIE.key(), 15);
        map.put(GameEvent.EXPLODE.key(), 15);

        for (int i = 1; i <= 15; i++) {
            map.put(getResonanceEventByFrequency(i), i);
        }
    });

    VibrationSystem.Data getVibrationData();

    VibrationSystem.User getVibrationUser();

    static int getGameEventFrequency(Holder<GameEvent> gameEvent) {
        return gameEvent.unwrapKey().map(VibrationSystem::getGameEventFrequency).orElse(0);
    }

    static int getGameEventFrequency(ResourceKey<GameEvent> eventKey) {
        return VIBRATION_FREQUENCY_FOR_EVENT.applyAsInt(eventKey);
    }

    static ResourceKey<GameEvent> getResonanceEventByFrequency(int frequency) {
        return RESONANCE_EVENTS.get(frequency - 1);
    }

    static int getRedstoneStrengthForDistance(float distance, int maxDistance) {
        double d = 15.0 / maxDistance;
        return Math.max(1, 15 - Mth.floor(d * distance));
    }

    public static final class Data {
        public static Codec<VibrationSystem.Data> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    VibrationInfo.CODEC.lenientOptionalFieldOf("event").forGetter(data -> Optional.ofNullable(data.currentVibration)),
                    VibrationSelector.CODEC.optionalFieldOf("selector").xmap(o -> o.orElseGet(VibrationSelector::new), Optional::of).forGetter(VibrationSystem.Data::getSelectionStrategy), // Paper - fix MapLike spam for missing "selector" in 1.19.2
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("event_delay").orElse(0).forGetter(VibrationSystem.Data::getTravelTimeInTicks)
                )
                .apply(
                    instance,
                    (vibrationInfo, vibrationSelector, eventDelay) -> new VibrationSystem.Data(vibrationInfo.orElse(null), vibrationSelector, eventDelay, true)
                )
        );
        public static final String NBT_TAG_KEY = "listener";
        @Nullable VibrationInfo currentVibration;
        private int travelTimeInTicks;
        final VibrationSelector selectionStrategy;
        private boolean reloadVibrationParticle;

        private Data(@Nullable VibrationInfo currentVibration, VibrationSelector selectionStrategy, int travelTimeInTicks, boolean reloadVibrationParticle) {
            this.currentVibration = currentVibration;
            this.travelTimeInTicks = travelTimeInTicks;
            this.selectionStrategy = selectionStrategy;
            this.reloadVibrationParticle = reloadVibrationParticle;
        }

        public Data() {
            this(null, new VibrationSelector(), 0, false);
        }

        public VibrationSelector getSelectionStrategy() {
            return this.selectionStrategy;
        }

        public @Nullable VibrationInfo getCurrentVibration() {
            return this.currentVibration;
        }

        public void setCurrentVibration(@Nullable VibrationInfo currentVibration) {
            this.currentVibration = currentVibration;
        }

        public int getTravelTimeInTicks() {
            return this.travelTimeInTicks;
        }

        public void setTravelTimeInTicks(int travelTimeInTicks) {
            this.travelTimeInTicks = travelTimeInTicks;
        }

        public void decrementTravelTime() {
            this.travelTimeInTicks = Math.max(0, this.travelTimeInTicks - 1);
        }

        public boolean shouldReloadVibrationParticle() {
            return this.reloadVibrationParticle;
        }

        public void setReloadVibrationParticle(boolean reloadVibrationParticle) {
            this.reloadVibrationParticle = reloadVibrationParticle;
        }
    }

    public static class Listener implements GameEventListener {
        private final VibrationSystem system;

        public Listener(VibrationSystem system) {
            this.system = system;
        }

        @Override
        public PositionSource getListenerSource() {
            return this.system.getVibrationUser().getPositionSource();
        }

        @Override
        public int getListenerRadius() {
            return this.system.getVibrationUser().getListenerRadius();
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos) {
            VibrationSystem.Data vibrationData = this.system.getVibrationData();
            VibrationSystem.User vibrationUser = this.system.getVibrationUser();
            if (vibrationData.getCurrentVibration() != null) {
                return false;
            } else if (!vibrationUser.isValidVibration(gameEvent, context)) {
                return false;
            } else {
                Optional<Vec3> position = vibrationUser.getPositionSource().getPosition(level);
                if (position.isEmpty()) {
                    return false;
                } else {
                    Vec3 vec3 = position.get();
                    // CraftBukkit start
                    boolean defaultCancel = !vibrationUser.canReceiveVibration(level, BlockPos.containing(pos), gameEvent, context);
                    Entity entity = context.sourceEntity();
                    org.bukkit.event.block.BlockReceiveGameEvent event1 = new org.bukkit.event.block.BlockReceiveGameEvent(org.bukkit.craftbukkit.CraftGameEvent.minecraftHolderToBukkit(gameEvent), org.bukkit.craftbukkit.block.CraftBlock.at(level, BlockPos.containing(vec3)), (entity == null) ? null : entity.getBukkitEntity());
                    event1.setCancelled(defaultCancel);
                    level.getCraftServer().getPluginManager().callEvent(event1);
                    if (event1.isCancelled()) {
                        // CraftBukkit end
                        return false;
                    } else if (isOccluded(level, pos, vec3)) {
                        return false;
                    } else {
                        this.scheduleVibration(level, vibrationData, gameEvent, context, pos, vec3);
                        return true;
                    }
                }
            }
        }

        public void forceScheduleVibration(ServerLevel level, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos) {
            this.system
                .getVibrationUser()
                .getPositionSource()
                .getPosition(level)
                .ifPresent(vec3 -> this.scheduleVibration(level, this.system.getVibrationData(), gameEvent, context, pos, vec3));
        }

        private void scheduleVibration(
            ServerLevel level, VibrationSystem.Data data, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos, Vec3 sensorPos
        ) {
            data.selectionStrategy
                .addCandidate(new VibrationInfo(gameEvent, (float)pos.distanceTo(sensorPos), pos, context.sourceEntity()), level.getGameTime());
        }

        public static float distanceBetweenInBlocks(BlockPos pos1, BlockPos pos2) {
            return (float)Math.sqrt(pos1.distSqr(pos2));
        }

        private static boolean isOccluded(Level level, Vec3 eventPos, Vec3 vibrationUserPos) {
            Vec3 vec3 = new Vec3(Mth.floor(eventPos.x) + 0.5, Mth.floor(eventPos.y) + 0.5, Mth.floor(eventPos.z) + 0.5);
            Vec3 vec31 = new Vec3(Mth.floor(vibrationUserPos.x) + 0.5, Mth.floor(vibrationUserPos.y) + 0.5, Mth.floor(vibrationUserPos.z) + 0.5);

            for (Direction direction : Direction.values()) {
                Vec3 vec32 = vec3.relative(direction, 1.0E-5F);
                if (level.isBlockInLine(new ClipBlockStateContext(vec32, vec31, state -> state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))).getType()
                    != HitResult.Type.BLOCK) {
                    return false;
                }
            }

            return true;
        }
    }

    public interface Ticker {
        static void tick(Level level, VibrationSystem.Data data, VibrationSystem.User user) {
            if (level instanceof ServerLevel serverLevel) {
                if (data.currentVibration == null) {
                    trySelectAndScheduleVibration(serverLevel, data, user);
                }

                if (data.currentVibration != null) {
                    boolean flag = data.getTravelTimeInTicks() > 0;
                    tryReloadVibrationParticle(serverLevel, data, user);
                    data.decrementTravelTime();
                    if (data.getTravelTimeInTicks() <= 0) {
                        flag = receiveVibration(serverLevel, data, user, data.currentVibration);
                    }

                    if (flag) {
                        user.onDataChanged();
                    }
                }
            }
        }

        private static void trySelectAndScheduleVibration(ServerLevel level, VibrationSystem.Data data, VibrationSystem.User user) {
            data.getSelectionStrategy()
                .chosenCandidate(level.getGameTime())
                .ifPresent(
                    vibrationInfo -> {
                        data.setCurrentVibration(vibrationInfo);
                        Vec3 vec3 = vibrationInfo.pos();
                        data.setTravelTimeInTicks(user.calculateTravelTimeInTicks(vibrationInfo.distance()));
                        level.sendParticles(
                            new VibrationParticleOption(user.getPositionSource(), data.getTravelTimeInTicks()), vec3.x, vec3.y, vec3.z, 1, 0.0, 0.0, 0.0, 0.0
                        );
                        user.onDataChanged();
                        data.getSelectionStrategy().startOver();
                    }
                );
        }

        private static void tryReloadVibrationParticle(ServerLevel level, VibrationSystem.Data data, VibrationSystem.User user) {
            if (data.shouldReloadVibrationParticle()) {
                if (data.currentVibration == null) {
                    data.setReloadVibrationParticle(false);
                } else {
                    Vec3 vec3 = data.currentVibration.pos();
                    PositionSource positionSource = user.getPositionSource();
                    Vec3 vec31 = positionSource.getPosition(level).orElse(vec3);
                    int travelTimeInTicks = data.getTravelTimeInTicks();
                    int i = user.calculateTravelTimeInTicks(data.currentVibration.distance());
                    double d = 1.0 - (double)travelTimeInTicks / i;
                    double d1 = Mth.lerp(d, vec3.x, vec31.x);
                    double d2 = Mth.lerp(d, vec3.y, vec31.y);
                    double d3 = Mth.lerp(d, vec3.z, vec31.z);
                    boolean flag = level.sendParticles(new VibrationParticleOption(positionSource, travelTimeInTicks), d1, d2, d3, 1, 0.0, 0.0, 0.0, 0.0) > 0;
                    if (flag) {
                        data.setReloadVibrationParticle(false);
                    }
                }
            }
        }

        private static boolean receiveVibration(ServerLevel level, VibrationSystem.Data data, VibrationSystem.User user, VibrationInfo vibrationInfo) {
            BlockPos blockPos = BlockPos.containing(vibrationInfo.pos());
            BlockPos blockPos1 = user.getPositionSource().getPosition(level).map(BlockPos::containing).orElse(blockPos);
            if (user.requiresAdjacentChunksToBeTicking() && !areAdjacentChunksTicking(level, blockPos1)) {
                return false;
            } else {
                user.onReceiveVibration(
                    level,
                    blockPos,
                    vibrationInfo.gameEvent(),
                    vibrationInfo.getEntity(level).orElse(null),
                    vibrationInfo.getProjectileOwner(level).orElse(null),
                    VibrationSystem.Listener.distanceBetweenInBlocks(blockPos, blockPos1)
                );
                data.setCurrentVibration(null);
                return true;
            }
        }

        private static boolean areAdjacentChunksTicking(Level level, BlockPos pos) {
            ChunkPos chunkPos = new ChunkPos(pos);

            for (int i = chunkPos.x - 1; i <= chunkPos.x + 1; i++) {
                for (int i1 = chunkPos.z - 1; i1 <= chunkPos.z + 1; i1++) {
                    if (!level.shouldTickBlocksAt(ChunkPos.asLong(i, i1)) || level.getChunkSource().getChunkNow(i, i1) == null) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public interface User {
        int getListenerRadius();

        PositionSource getPositionSource();

        boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, GameEvent.Context context);

        void onReceiveVibration(
            ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable Entity entity, @Nullable Entity playerEntity, float distance
        );

        default TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.VIBRATIONS;
        }

        default boolean canTriggerAvoidVibration() {
            return false;
        }

        default boolean requiresAdjacentChunksToBeTicking() {
            return false;
        }

        default int calculateTravelTimeInTicks(float distance) {
            return Mth.floor(distance);
        }

        default boolean isValidVibration(Holder<GameEvent> gameEvent, GameEvent.Context context) {
            if (!gameEvent.is(this.getListenableEvents())) {
                return false;
            } else {
                Entity entity = context.sourceEntity();
                if (entity != null) {
                    if (entity.isSpectator()) {
                        return false;
                    }

                    if (entity.isSteppingCarefully() && gameEvent.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) {
                        if (this.canTriggerAvoidVibration() && entity instanceof ServerPlayer serverPlayer) {
                            CriteriaTriggers.AVOID_VIBRATION.trigger(serverPlayer);
                        }

                        return false;
                    }

                    if (entity.dampensVibrations()) {
                        return false;
                    }
                }

                return context.affectedState() == null || !context.affectedState().is(BlockTags.DAMPENS_VIBRATIONS);
            }
        }

        default void onDataChanged() {
        }
    }
}
