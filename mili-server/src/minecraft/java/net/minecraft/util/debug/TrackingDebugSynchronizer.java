package net.minecraft.util.debug;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public abstract class TrackingDebugSynchronizer<T> {
    protected final DebugSubscription<T> subscription;
    private final Set<UUID> subscribedPlayers = new ObjectOpenHashSet<>();

    public TrackingDebugSynchronizer(DebugSubscription<T> subscription) {
        this.subscription = subscription;
    }

    public final void tick(ServerLevel level) {
        for (ServerPlayer serverPlayer : level.players()) {
            boolean flag = this.subscribedPlayers.contains(serverPlayer.getUUID());
            boolean flag1 = serverPlayer.debugSubscriptions().contains(this.subscription);
            if (flag1 != flag) {
                if (flag1) {
                    this.addSubscriber(serverPlayer);
                } else {
                    this.subscribedPlayers.remove(serverPlayer.getUUID());
                }
            }
        }

        this.subscribedPlayers.removeIf(uuid -> level.getPlayerByUUID(uuid) == null);
        if (!this.subscribedPlayers.isEmpty()) {
            this.pollAndSendUpdates(level);
        }
    }

    private void addSubscriber(ServerPlayer player) {
        this.subscribedPlayers.add(player.getUUID());
        player.getChunkTrackingView().forEach(chunkPos -> {
            if (!player.connection.chunkSender.isPending(chunkPos.toLong())) {
                this.startTrackingChunk(player, chunkPos);
            }
        });
        player.level().getChunkSource().chunkMap.forEachEntityTrackedBy(player, entity -> this.startTrackingEntity(player, entity));
    }

    protected final void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;

        for (UUID uuid : this.subscribedPlayers) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer serverPlayer && chunkMap.isChunkTracked(serverPlayer, chunkPos.x, chunkPos.z)) {
                serverPlayer.connection.send(packet);
            }
        }
    }

    protected final void sendToPlayersTrackingEntity(ServerLevel level, Entity entity, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        chunkMap.sendToTrackingPlayersFiltered(entity, packet, serverPlayer -> this.subscribedPlayers.contains(serverPlayer.getUUID()));
    }

    public final void startTrackingChunk(ServerPlayer player, ChunkPos chunkPos) {
        if (this.subscribedPlayers.contains(player.getUUID())) {
            this.sendInitialChunk(player, chunkPos);
        }
    }

    public final void startTrackingEntity(ServerPlayer player, Entity entity) {
        if (this.subscribedPlayers.contains(player.getUUID())) {
            this.sendInitialEntity(player, entity);
        }
    }

    protected void clear() {
    }

    protected void pollAndSendUpdates(ServerLevel level) {
    }

    protected void sendInitialChunk(ServerPlayer player, ChunkPos chunkPos) {
    }

    protected void sendInitialEntity(ServerPlayer player, Entity entity) {
    }

    public static class PoiSynchronizer extends TrackingDebugSynchronizer<DebugPoiInfo> {
        public PoiSynchronizer() {
            super(DebugSubscriptions.POIS);
        }

        @Override
        protected void sendInitialChunk(ServerPlayer player, ChunkPos chunkPos) {
            ServerLevel serverLevel = player.level();
            PoiManager poiManager = serverLevel.getPoiManager();
            poiManager.getInChunk(holder -> true, chunkPos, PoiManager.Occupancy.ANY)
                .forEach(
                    poiRecord -> player.connection
                        .send(new ClientboundDebugBlockValuePacket(poiRecord.getPos(), this.subscription.packUpdate(new DebugPoiInfo(poiRecord))))
                );
        }

        public void onPoiAdded(ServerLevel level, PoiRecord _record) {
            this.sendToPlayersTrackingChunk(
                level,
                new ChunkPos(_record.getPos()),
                new ClientboundDebugBlockValuePacket(_record.getPos(), this.subscription.packUpdate(new DebugPoiInfo(_record)))
            );
        }

        public void onPoiRemoved(ServerLevel level, BlockPos pos) {
            this.sendToPlayersTrackingChunk(level, new ChunkPos(pos), new ClientboundDebugBlockValuePacket(pos, this.subscription.emptyUpdate()));
        }

        public void onPoiTicketCountChanged(ServerLevel level, BlockPos pos) {
            this.sendToPlayersTrackingChunk(
                level, new ChunkPos(pos), new ClientboundDebugBlockValuePacket(pos, this.subscription.packUpdate(level.getPoiManager().getDebugPoiInfo(pos)))
            );
        }
    }

    public static class SourceSynchronizer<T> extends TrackingDebugSynchronizer<T> {
        private final Map<ChunkPos, TrackingDebugSynchronizer.ValueSource<T>> chunkSources = new HashMap<>();
        private final Map<BlockPos, TrackingDebugSynchronizer.ValueSource<T>> blockEntitySources = new HashMap<>();
        private final Map<UUID, TrackingDebugSynchronizer.ValueSource<T>> entitySources = new HashMap<>();

        public SourceSynchronizer(DebugSubscription<T> subscription) {
            super(subscription);
        }

        @Override
        protected void clear() {
            this.chunkSources.clear();
            this.blockEntitySources.clear();
            this.entitySources.clear();
        }

        @Override
        protected void pollAndSendUpdates(ServerLevel level) {
            for (Entry<ChunkPos, TrackingDebugSynchronizer.ValueSource<T>> entry : this.chunkSources.entrySet()) {
                DebugSubscription.Update<T> update = entry.getValue().pollUpdate(this.subscription);
                if (update != null) {
                    ChunkPos chunkPos = entry.getKey();
                    this.sendToPlayersTrackingChunk(level, chunkPos, new ClientboundDebugChunkValuePacket(chunkPos, update));
                }
            }

            for (Entry<BlockPos, TrackingDebugSynchronizer.ValueSource<T>> entryx : this.blockEntitySources.entrySet()) {
                DebugSubscription.Update<T> update = entryx.getValue().pollUpdate(this.subscription);
                if (update != null) {
                    BlockPos blockPos = entryx.getKey();
                    ChunkPos chunkPos1 = new ChunkPos(blockPos);
                    this.sendToPlayersTrackingChunk(level, chunkPos1, new ClientboundDebugBlockValuePacket(blockPos, update));
                }
            }

            for (Entry<UUID, TrackingDebugSynchronizer.ValueSource<T>> entryxx : this.entitySources.entrySet()) {
                DebugSubscription.Update<T> update = entryxx.getValue().pollUpdate(this.subscription);
                if (update != null) {
                    Entity entity = Objects.requireNonNull(level.getEntity(entryxx.getKey()));
                    this.sendToPlayersTrackingEntity(level, entity, new ClientboundDebugEntityValuePacket(entity.getId(), update));
                }
            }
        }

        public void registerChunk(ChunkPos chunkPos, DebugValueSource.ValueGetter<T> getter) {
            this.chunkSources.put(chunkPos, new TrackingDebugSynchronizer.ValueSource<>(getter));
        }

        public void registerBlockEntity(BlockPos pos, DebugValueSource.ValueGetter<T> getter) {
            this.blockEntitySources.put(pos, new TrackingDebugSynchronizer.ValueSource<>(getter));
        }

        public void registerEntity(UUID id, DebugValueSource.ValueGetter<T> getter) {
            this.entitySources.put(id, new TrackingDebugSynchronizer.ValueSource<>(getter));
        }

        public void dropChunk(ChunkPos chunkPos) {
            this.chunkSources.remove(chunkPos);
            this.blockEntitySources.keySet().removeIf(chunkPos::contains);
        }

        public void dropBlockEntity(ServerLevel level, BlockPos pos) {
            TrackingDebugSynchronizer.ValueSource<T> valueSource = this.blockEntitySources.remove(pos);
            if (valueSource != null) {
                ChunkPos chunkPos = new ChunkPos(pos);
                this.sendToPlayersTrackingChunk(level, chunkPos, new ClientboundDebugBlockValuePacket(pos, this.subscription.emptyUpdate()));
            }
        }

        public void dropEntity(Entity entity) {
            this.entitySources.remove(entity.getUUID());
        }

        @Override
        protected void sendInitialChunk(ServerPlayer player, ChunkPos chunkPos) {
            TrackingDebugSynchronizer.ValueSource<T> valueSource = this.chunkSources.get(chunkPos);
            if (valueSource != null && valueSource.lastSyncedValue != null) {
                player.connection.send(new ClientboundDebugChunkValuePacket(chunkPos, this.subscription.packUpdate(valueSource.lastSyncedValue)));
            }

            for (Entry<BlockPos, TrackingDebugSynchronizer.ValueSource<T>> entry : this.blockEntitySources.entrySet()) {
                T object = entry.getValue().lastSyncedValue;
                if (object != null) {
                    BlockPos blockPos = entry.getKey();
                    if (chunkPos.contains(blockPos)) {
                        player.connection.send(new ClientboundDebugBlockValuePacket(blockPos, this.subscription.packUpdate(object)));
                    }
                }
            }
        }

        @Override
        protected void sendInitialEntity(ServerPlayer player, Entity entity) {
            TrackingDebugSynchronizer.ValueSource<T> valueSource = this.entitySources.get(entity.getUUID());
            if (valueSource != null && valueSource.lastSyncedValue != null) {
                player.connection.send(new ClientboundDebugEntityValuePacket(entity.getId(), this.subscription.packUpdate(valueSource.lastSyncedValue)));
            }
        }
    }

    static class ValueSource<T> {
        private final DebugValueSource.ValueGetter<T> getter;
        @Nullable T lastSyncedValue;

        ValueSource(DebugValueSource.ValueGetter<T> getter) {
            this.getter = getter;
        }

        public DebugSubscription.@Nullable Update<T> pollUpdate(DebugSubscription<T> subscription) {
            T object = this.getter.get();
            if (!Objects.equals(object, this.lastSyncedValue)) {
                this.lastSyncedValue = object;
                return subscription.packUpdate(object);
            } else {
                return null;
            }
        }
    }

    public static class VillageSectionSynchronizer extends TrackingDebugSynchronizer<Unit> {
        public VillageSectionSynchronizer() {
            super(DebugSubscriptions.VILLAGE_SECTIONS);
        }

        @Override
        protected void sendInitialChunk(ServerPlayer player, ChunkPos chunkPos) {
            ServerLevel serverLevel = player.level();
            PoiManager poiManager = serverLevel.getPoiManager();
            poiManager.getInChunk(holder -> true, chunkPos, PoiManager.Occupancy.ANY).forEach(poiRecord -> {
                SectionPos sectionPos = SectionPos.of(poiRecord.getPos());
                forEachVillageSectionUpdate(serverLevel, sectionPos, (sectionPos1, _boolean) -> {
                    BlockPos blockPos = sectionPos1.center();
                    player.connection.send(new ClientboundDebugBlockValuePacket(blockPos, this.subscription.packUpdate(_boolean ? Unit.INSTANCE : null)));
                });
            });
        }

        public void onPoiAdded(ServerLevel level, PoiRecord _record) {
            this.sendVillageSectionsPacket(level, _record.getPos());
        }

        public void onPoiRemoved(ServerLevel level, BlockPos pos) {
            this.sendVillageSectionsPacket(level, pos);
        }

        private void sendVillageSectionsPacket(ServerLevel level, BlockPos pos) {
            forEachVillageSectionUpdate(
                level,
                SectionPos.of(pos),
                (sectionPos, _boolean) -> {
                    BlockPos blockPos = sectionPos.center();
                    if (_boolean) {
                        this.sendToPlayersTrackingChunk(
                            level, new ChunkPos(blockPos), new ClientboundDebugBlockValuePacket(blockPos, this.subscription.packUpdate(Unit.INSTANCE))
                        );
                    } else {
                        this.sendToPlayersTrackingChunk(
                            level, new ChunkPos(blockPos), new ClientboundDebugBlockValuePacket(blockPos, this.subscription.emptyUpdate())
                        );
                    }
                }
            );
        }

        private static void forEachVillageSectionUpdate(ServerLevel level, SectionPos pos, BiConsumer<SectionPos, Boolean> action) {
            for (int i = -1; i <= 1; i++) {
                for (int i1 = -1; i1 <= 1; i1++) {
                    for (int i2 = -1; i2 <= 1; i2++) {
                        SectionPos sectionPos = pos.offset(i1, i2, i);
                        if (level.isVillage(sectionPos.center())) {
                            action.accept(sectionPos, true);
                        } else {
                            action.accept(sectionPos, false);
                        }
                    }
                }
            }
        }
    }
}
