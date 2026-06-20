package net.minecraft.world.entity.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Raids extends SavedData {
    private static final String RAID_FILE_ID = "raids";
    public static final Codec<Raids> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Raids.RaidWithId.CODEC
                    .listOf()
                    .optionalFieldOf("raids", List.of())
                    .forGetter(raids -> raids.raidMap.entrySet().stream().map(Raids.RaidWithId::from).toList()), // Folia - make raids thread-safe
                Codec.INT.fieldOf("next_id").forGetter(raids -> raids.nextAvailableID.get()), // Folia - make raids thread-safe
                Codec.INT.fieldOf("tick").forGetter(raids -> raids.tick)
            )
            .apply(instance, Raids::new)
    );
    public static final SavedDataType<Raids> TYPE = new SavedDataType<>("raids", Raids::new, CODEC, DataFixTypes.SAVED_DATA_RAIDS);
    public static final SavedDataType<Raids> TYPE_END = new SavedDataType<>("raids_end", Raids::new, CODEC, DataFixTypes.SAVED_DATA_RAIDS);
    public final java.util.Map<Integer, Raid> raidMap = new java.util.concurrent.ConcurrentHashMap<>(); // Folia - make raids thread-safe
    private final java.util.concurrent.atomic.AtomicInteger nextAvailableID = new java.util.concurrent.atomic.AtomicInteger(); // Folia - make raids thread-safe
    private int tick;

    public static SavedDataType<Raids> getType(Holder<DimensionType> dimension) {
        return dimension.is(BuiltinDimensionTypes.END) ? TYPE_END : TYPE;
    }

    public Raids() {
        this.nextAvailableID.set(1); // Folia - make raids thread-safe
        this.setDirty();
    }

    private Raids(List<Raids.RaidWithId> raids, int nextId, int tick) {
        for (Raids.RaidWithId raidWithId : raids) {
            this.raidMap.put(raidWithId.id, raidWithId.raid);
            raidWithId.raid.idOrNegativeOne = raidWithId.id; // Paper - expose id of raids while method is kept around as deprecated for removal
        }

        this.nextAvailableID.set(nextId); // Folia - make raids thread-safe
        this.tick = tick;
    }

    public @Nullable Raid get(int id) {
        return this.raidMap.get(id);
    }

    public OptionalInt getId(Raid raid) {
        for (java.util.Map.Entry<Integer, Raid> entry : this.raidMap.entrySet()) { // Folia - make raids thread-safe
            if (entry.getValue() == raid) {
                return OptionalInt.of(entry.getKey().intValue()); // Folia - make raids thread-safe
            }
        }

        return OptionalInt.empty();
    }

    // Folia start - make raids thread-safe
    public void globalTick() {
        this.tick++;
        if (this.tick % 200 == 0) {
            this.setDirty();
        }
    }

    public void tick(ServerLevel level) {
        // Folia end - make raids thread-safe
        Iterator<Raid> iterator = this.raidMap.values().iterator();

        while (iterator.hasNext()) {
            Raid raid = iterator.next();
            // Folia start - make raids thread-safe
            if (!raid.ownsRaid(level)) {
                continue;
            }
            // Folia end - make raids thread-safe
            if (!level.getGameRules().get(GameRules.RAIDS)) {
                raid.stop();
            }

            if (raid.isStopped()) {
                iterator.remove();
                this.setDirty();
            } else {
                raid.tick(level);
            }
        }

        // Folia - make raids thread-safe - move to globalTick()
    }

    public static boolean canJoinRaid(Raider raider) {
        return raider.isAlive() && raider.canJoinRaid() && raider.getNoActionTime() <= 2400;
    }

    public @Nullable Raid createOrExtendRaid(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator()) {
            return null;
        } else {
            ServerLevel serverLevel = player.level();
            if (!serverLevel.getGameRules().get(GameRules.RAIDS)|| !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player) || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(serverLevel, pos.getX() >> 4, pos.getZ() >> 4, 8) || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(serverLevel, player.chunkPosition().x, player.chunkPosition().z, 8)) { // Folia - region threading
                return null;
            } else if (!serverLevel.environmentAttributes().getValue(EnvironmentAttributes.CAN_START_RAID, pos)) {
                return null;
            } else {
                List<PoiRecord> list = serverLevel.getPoiManager()
                    .getInRange(holder -> holder.is(PoiTypeTags.VILLAGE), pos, 64, PoiManager.Occupancy.IS_OCCUPIED)
                    .toList();
                int i = 0;
                Vec3 vec3 = Vec3.ZERO;

                for (PoiRecord poiRecord : list) {
                    BlockPos pos1 = poiRecord.getPos();
                    vec3 = vec3.add(pos1.getX(), pos1.getY(), pos1.getZ());
                    i++;
                }

                BlockPos blockPos;
                if (i > 0) {
                    vec3 = vec3.scale(1.0 / i);
                    blockPos = BlockPos.containing(vec3);
                } else {
                    blockPos = pos;
                }

                Raid raid = this.getOrCreateRaid(serverLevel, blockPos);
                // CraftBukkit - moved down
                // if (!raid.isStarted() && !this.raidMap.containsValue(raid)) {
                //     this.raidMap.put(this.getUniqueId(), raid);
                // }

                if (!raid.isStarted() || (raid.isInProgress() && raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel())) { // CraftBukkit - fixed a bug with raid: players could add up Bad Omen level even when the raid had finished
                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callRaidTriggerEvent(serverLevel, raid, player)) {
                        player.removeEffect(net.minecraft.world.effect.MobEffects.RAID_OMEN);
                        return null;
                    }

                    if (!raid.isStarted() && !this.raidMap.containsValue(raid)) {
                        int id = this.getUniqueId(); this.raidMap.put(id, raid); // Folia - region threading
                        raid.idOrNegativeOne = id; // Paper - expose id of raids while method is kept around as deprecated for removal // Folia - region threading
                    }
                    // CraftBukkit end
                    raid.absorbRaidOmen(player);
                }

                this.setDirty();
                return raid;
            }
        }
    }

    private Raid getOrCreateRaid(ServerLevel level, BlockPos pos) {
        Raid raidAt = level.getRaidAt(pos);
        return raidAt != null ? raidAt : new Raid(pos, level.getDifficulty());
    }

    public static Raids load(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial().orElseGet(Raids::new);
    }

    private int getUniqueId() {
        return this.nextAvailableID.incrementAndGet(); // Folia - make raids thread-safe
    }

    public @Nullable Raid getNearbyRaid(ServerLevel world, BlockPos pos, int distance) { // Folia - make raids thread-safe - add ServerLevel param
        Raid raid = null;
        double d = distance;

        for (Raid raid1 : this.raidMap.values()) {
            // Folia start - make raids thread-safe
            if (!raid1.ownsRaid(world)) {
                continue;
            }
            // Folia end - make raids thread-safe
            double d1 = raid1.getCenter().distSqr(pos);
            if (raid1.isActive() && d1 < d) {
                raid = raid1;
                d = d1;
            }
        }

        return raid;
    }

    @VisibleForDebug
    public List<BlockPos> getRaidCentersInChunk(ChunkPos chunkPos) {
        return this.raidMap.values().stream().map(Raid::getCenter).filter(chunkPos::contains).toList();
    }

    record RaidWithId(int id, Raid raid) {
        public static final Codec<Raids.RaidWithId> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(Codec.INT.fieldOf("id").forGetter(Raids.RaidWithId::id), Raid.MAP_CODEC.forGetter(Raids.RaidWithId::raid))
                .apply(instance, Raids.RaidWithId::new)
        );

        public static Raids.RaidWithId from(java.util.Map.Entry<Integer, Raid> entry) { // Folia - make raids thread-safe
            return new Raids.RaidWithId(entry.getKey().intValue(), entry.getValue()); // Folia - make raids thread-safe
        }
    }
}
