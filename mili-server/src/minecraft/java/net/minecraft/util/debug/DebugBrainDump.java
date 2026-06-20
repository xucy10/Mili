package net.minecraft.util.debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;

public record DebugBrainDump(
    String name,
    String profession,
    int xp,
    float health,
    float maxHealth,
    String inventory,
    boolean wantsGolem,
    int angerLevel,
    List<String> activities,
    List<String> behaviors,
    List<String> memories,
    List<String> gossips,
    Set<BlockPos> pois,
    Set<BlockPos> potentialPois
) {
    public static final StreamCodec<FriendlyByteBuf, DebugBrainDump> STREAM_CODEC = StreamCodec.of((buffer, value) -> value.write(buffer), DebugBrainDump::new);

    public DebugBrainDump(FriendlyByteBuf buffer) {
        this(
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readInt(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readUtf(),
            buffer.readBoolean(),
            buffer.readInt(),
            buffer.readList(FriendlyByteBuf::readUtf),
            buffer.readList(FriendlyByteBuf::readUtf),
            buffer.readList(FriendlyByteBuf::readUtf),
            buffer.readList(FriendlyByteBuf::readUtf),
            buffer.readCollection(HashSet::new, BlockPos.STREAM_CODEC),
            buffer.readCollection(HashSet::new, BlockPos.STREAM_CODEC)
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.name);
        buffer.writeUtf(this.profession);
        buffer.writeInt(this.xp);
        buffer.writeFloat(this.health);
        buffer.writeFloat(this.maxHealth);
        buffer.writeUtf(this.inventory);
        buffer.writeBoolean(this.wantsGolem);
        buffer.writeInt(this.angerLevel);
        buffer.writeCollection(this.activities, FriendlyByteBuf::writeUtf);
        buffer.writeCollection(this.behaviors, FriendlyByteBuf::writeUtf);
        buffer.writeCollection(this.memories, FriendlyByteBuf::writeUtf);
        buffer.writeCollection(this.gossips, FriendlyByteBuf::writeUtf);
        buffer.writeCollection(this.pois, BlockPos.STREAM_CODEC);
        buffer.writeCollection(this.potentialPois, BlockPos.STREAM_CODEC);
    }

    public static DebugBrainDump takeBrainDump(ServerLevel level, LivingEntity entity) {
        String entityName = DebugEntityNameGenerator.getEntityName(entity);
        String registeredName;
        int villagerXp;
        if (entity instanceof Villager villager) {
            registeredName = villager.getVillagerData().profession().getRegisteredName();
            villagerXp = villager.getVillagerXp();
        } else {
            registeredName = "";
            villagerXp = 0;
        }

        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        Brain<?> brain = entity.getBrain();
        long gameTime = entity.level().getGameTime();
        String string;
        if (entity instanceof InventoryCarrier inventoryCarrier) {
            Container inventory = inventoryCarrier.getInventory();
            string = inventory.isEmpty() ? "" : inventory.toString();
        } else {
            string = "";
        }

        boolean flag = entity instanceof Villager villager1 && villager1.wantsToSpawnGolem(gameTime);
        int i = entity instanceof Warden warden ? warden.getClientAngerLevel() : -1;
        List<String> list = brain.getActiveActivities().stream().map(Activity::getName).toList();
        List<String> list1 = brain.getRunningBehaviors().stream().map(BehaviorControl::debugString).toList();
        List<String> list2 = getMemoryDescriptions(level, entity, gameTime).map(string1 -> StringUtil.truncateStringIfNecessary(string1, 255, true)).toList();
        Set<BlockPos> knownBlockPositions = getKnownBlockPositions(brain, MemoryModuleType.JOB_SITE, MemoryModuleType.HOME, MemoryModuleType.MEETING_POINT);
        Set<BlockPos> knownBlockPositions1 = getKnownBlockPositions(brain, MemoryModuleType.POTENTIAL_JOB_SITE);
        List<String> list3 = entity instanceof Villager villager2 ? getVillagerGossips(villager2) : List.of();
        return new DebugBrainDump(
            entityName, registeredName, villagerXp, health, maxHealth, string, flag, i, list, list1, list2, list3, knownBlockPositions, knownBlockPositions1
        );
    }

    @SafeVarargs
    private static Set<BlockPos> getKnownBlockPositions(Brain<?> brain, MemoryModuleType<GlobalPos>... memoryTypes) {
        return Stream.of(memoryTypes)
            .filter(brain::hasMemoryValue)
            .map(brain::getMemory)
            .flatMap(Optional::stream)
            .map(GlobalPos::pos)
            .collect(Collectors.toSet());
    }

    private static List<String> getVillagerGossips(Villager villager) {
        List<String> list = new ArrayList<>();
        villager.getGossips().getGossipEntries().forEach((uuid, map) -> {
            String entityName = DebugEntityNameGenerator.getEntityName(uuid);
            map.forEach((gossipType, i) -> list.add(entityName + ": " + gossipType + ": " + i));
        });
        return list;
    }

    private static Stream<String> getMemoryDescriptions(ServerLevel level, LivingEntity entity, long gameTime) {
        return entity.getBrain().getMemories().entrySet().stream().map(entry -> {
            MemoryModuleType<?> memoryModuleType = entry.getKey();
            Optional<? extends ExpirableValue<?>> optional = entry.getValue();
            return getMemoryDescription(level, gameTime, memoryModuleType, optional);
        }).sorted();
    }

    private static String getMemoryDescription(ServerLevel level, long gameTime, MemoryModuleType<?> memoryType, Optional<? extends ExpirableValue<?>> value) {
        String string;
        if (value.isPresent()) {
            ExpirableValue<?> expirableValue = (ExpirableValue<?>)value.get();
            Object value1 = expirableValue.getValue();
            if (memoryType == MemoryModuleType.HEARD_BELL_TIME) {
                long l = gameTime - (Long)value1;
                string = l + " ticks ago";
            } else if (expirableValue.canExpire()) {
                string = getShortDescription(level, value1) + " (ttl: " + expirableValue.getTimeToLive() + ")";
            } else {
                string = getShortDescription(level, value1);
            }
        } else {
            string = "-";
        }

        return BuiltInRegistries.MEMORY_MODULE_TYPE.getKey(memoryType).getPath() + ": " + string;
    }

    private static String getShortDescription(ServerLevel level, @Nullable Object object) {
        return switch (object) {
            case null -> "-";
            case UUID uuid -> getShortDescription(level, level.getEntity(uuid));
            case Entity entity -> DebugEntityNameGenerator.getEntityName(entity);
            case WalkTarget walkTarget -> getShortDescription(level, walkTarget.getTarget());
            case EntityTracker entityTracker -> getShortDescription(level, entityTracker.getEntity());
            case GlobalPos globalPos -> getShortDescription(level, globalPos.pos());
            case BlockPosTracker blockPosTracker -> getShortDescription(level, blockPosTracker.currentBlockPosition());
            case DamageSource damageSource -> {
                Entity entity1 = damageSource.getEntity();
                yield entity1 == null ? object.toString() : getShortDescription(level, entity1);
            }
            case Collection<?> collection -> "["
                + (String)collection.stream().map(object1 -> getShortDescription(level, object1)).collect(Collectors.joining(", "))
                + "]";
            default -> object.toString();
        };
    }

    public boolean hasPoi(BlockPos pos) {
        return this.pois.contains(pos);
    }

    public boolean hasPotentialPoi(BlockPos pos) {
        return this.potentialPois.contains(pos);
    }
}
