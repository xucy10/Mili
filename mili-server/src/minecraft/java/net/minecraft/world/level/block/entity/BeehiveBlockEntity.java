package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.debug.DebugHiveInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class BeehiveBlockEntity extends BlockEntity {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_FLOWER_POS = "flower_pos";
    private static final String BEES = "bees";
    static final List<String> IGNORED_BEE_TAGS = Arrays.asList(
        "Air",
        "drop_chances",
        "equipment",
        "Brain",
        "CanPickUpLoot",
        "DeathTime",
        "fall_distance",
        "FallFlying",
        "Fire",
        "HurtByTimestamp",
        "HurtTime",
        "LeftHanded",
        "Motion",
        "NoGravity",
        "OnGround",
        "PortalCooldown",
        "Pos",
        "Rotation",
        "sleeping_pos",
        "CannotEnterHiveTicks",
        "TicksSincePollination",
        "CropsGrownSincePollination",
        "hive_pos",
        "Passengers",
        "leash",
        "UUID"
    );
    public static final int MAX_OCCUPANTS = 3;
    private static final int MIN_TICKS_BEFORE_REENTERING_HIVE = 400;
    private static final int MIN_OCCUPATION_TICKS_NECTAR = 2400;
    public static final int MIN_OCCUPATION_TICKS_NECTARLESS = 600;
    private List<BeehiveBlockEntity.BeeData> stored = Lists.newArrayList();
    public @Nullable BlockPos savedFlowerPos;
    public int maxBees = MAX_OCCUPANTS; // CraftBukkit - allow setting max amount of bees a hive can hold

    public BeehiveBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.BEEHIVE, pos, blockState);
    }

    @Override
    public void setChanged() {
        if (this.isFireNearby()) {
            this.emptyAllLivingFromHive(null, this.level.getBlockState(this.getBlockPos()), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }

        super.setChanged();
    }

    public boolean isFireNearby() {
        if (this.level == null) {
            return false;
        } else {
            for (BlockPos blockPos : BlockPos.betweenClosed(this.worldPosition.offset(-1, -1, -1), this.worldPosition.offset(1, 1, 1))) {
                if (this.level.getBlockState(blockPos).getBlock() instanceof FireBlock) {
                    return true;
                }
            }

            return false;
        }
    }

    public boolean isEmpty() {
        return this.stored.isEmpty();
    }

    public boolean isFull() {
        return this.stored.size() == this.maxBees; // CraftBukkit
    }

    public void emptyAllLivingFromHive(@Nullable Player player, BlockState state, BeehiveBlockEntity.BeeReleaseStatus releaseStatus) {
        List<Entity> list = this.releaseAllOccupants(state, releaseStatus);
        if (player != null) {
            for (Entity entity : list) {
                if (entity instanceof Bee bee && player.position().distanceToSqr(entity.position()) <= 16.0) {
                    if (!this.isSedated()) {
                        bee.setTarget(player, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER); // CraftBukkit
                    } else {
                        bee.setStayOutOfHiveCountdown(400);
                    }
                }
            }
        }
    }

    private List<Entity> releaseAllOccupants(BlockState state, BeehiveBlockEntity.BeeReleaseStatus releaseStatus) {
        // CraftBukkit start - This allows us to bypass the night/rain/emergency check
        return this.releaseBees(state, releaseStatus, false);
    }

    public List<Entity> releaseBees(BlockState state, BeehiveBlockEntity.BeeReleaseStatus releaseStatus, boolean force) {
        // CraftBukkit end - This allows us to bypass t he night/rain/emergecny check
        List<Entity> list = Lists.newArrayList();
        this.stored.removeIf(data -> releaseOccupant(this.level, this.worldPosition, state, data.toOccupant(), list, releaseStatus, this.savedFlowerPos, force)); // CraftBukkit - This allows us to bypass t he night/rain/emergecny check
        if (!list.isEmpty()) {
            super.setChanged();
        }

        return list;
    }

    @VisibleForDebug
    public int getOccupantCount() {
        return this.stored.size();
    }

    // Paper start - Add EntityBlockStorage clearEntities
    public void clearBees() {
        this.stored.clear();
    }
    // Paper end - Add EntityBlockStorage clearEntities
    public static int getHoneyLevel(BlockState state) {
        return state.getValue(BeehiveBlock.HONEY_LEVEL);
    }

    @VisibleForDebug
    public boolean isSedated() {
        return CampfireBlock.isSmokeyPos(this.level, this.getBlockPos());
    }

    public void addOccupant(Bee bee) {
        if (this.stored.size() < this.maxBees) { // CraftBukkit
            // CraftBukkit start
            if (this.level != null) {
                org.bukkit.event.entity.EntityEnterBlockEvent event = new org.bukkit.event.entity.EntityEnterBlockEvent(bee.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.getBlockPos()));
                if (!event.callEvent()) {
                    bee.setStayOutOfHiveCountdown(MIN_TICKS_BEFORE_REENTERING_HIVE);
                    return;
                }
            }
            // CraftBukkit end
            bee.stopRiding();
            bee.ejectPassengers();
            bee.dropLeash();
            this.storeBee(BeehiveBlockEntity.Occupant.of(bee));
            if (this.level != null) {
                if (bee.hasSavedFlowerPos() && (!this.hasSavedFlowerPos() || this.level.random.nextBoolean())) {
                    this.savedFlowerPos = bee.getSavedFlowerPos();
                }

                BlockPos blockPos = this.getBlockPos();
                this.level
                    .playSound(
                        null,
                        (double)blockPos.getX(),
                        (double)blockPos.getY(),
                        (double)blockPos.getZ(),
                        SoundEvents.BEEHIVE_ENTER,
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F
                    );
                this.level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(bee, this.getBlockState()));
            }

            bee.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.ENTER_BLOCK); // CraftBukkit - add Bukkit remove cause
            super.setChanged();
        }
    }

    public void storeBee(BeehiveBlockEntity.Occupant occupant) {
        this.stored.add(new BeehiveBlockEntity.BeeData(occupant));
    }

    private static boolean releaseOccupant(
        Level level,
        BlockPos pos,
        BlockState state,
        BeehiveBlockEntity.Occupant occupant,
        @Nullable List<Entity> storedInHives,
        BeehiveBlockEntity.BeeReleaseStatus releaseStatus,
        @Nullable BlockPos storedFlowerPos
    ) {
        // CraftBukkit start
        return releaseOccupant(level, pos, state, occupant, storedInHives, releaseStatus, storedFlowerPos, false);
    }
    private static boolean releaseOccupant(
        Level level,
        BlockPos pos,
        BlockState state,
        BeehiveBlockEntity.Occupant occupant,
        @Nullable List<Entity> storedInHives,
        BeehiveBlockEntity.BeeReleaseStatus releaseStatus,
        @Nullable BlockPos storedFlowerPos,
        boolean force
    ) {
        if (!force && level.environmentAttributes().getValue(EnvironmentAttributes.BEES_STAY_IN_HIVE, pos) && releaseStatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
        // CraftBukkit end
            return false;
        } else {
            Direction direction = state.getValue(BeehiveBlock.FACING);
            BlockPos blockPos = pos.relative(direction);
            boolean flag = !level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty();
            if (flag && releaseStatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
                return false;
            } else {
                Entity entity = occupant.createEntity(level, pos);
                if (entity != null) {
                    // CraftBukkit start
                    if (entity instanceof Bee) {
                        float bbWidth = entity.getBbWidth();
                        double d = flag ? 0.0 : 0.55 + bbWidth / 2.0F;
                        double d1 = pos.getX() + 0.5 + d * direction.getStepX();
                        double d2 = pos.getY() + 0.5 - entity.getBbHeight() / 2.0F;
                        double d3 = pos.getZ() + 0.5 + d * direction.getStepZ();
                        entity.snapTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                    }
                    if (!level.addFreshEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BEEHIVE)) return false; // CraftBukkit - SpawnReason, moved from below
                    // CraftBukkit end
                    if (entity instanceof Bee bee) {
                        if (storedFlowerPos != null && !bee.hasSavedFlowerPos() && level.random.nextFloat() < 0.9F) {
                            bee.setSavedFlowerPos(storedFlowerPos);
                        }

                        if (releaseStatus == BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED) {
                            bee.dropOffNectar();
                            if (state.is(BlockTags.BEEHIVES, blockStateBase -> blockStateBase.hasProperty(BeehiveBlock.HONEY_LEVEL))) {
                                int honeyLevel = getHoneyLevel(state);
                                if (honeyLevel < 5) {
                                    int i = level.random.nextInt(100) == 0 ? 2 : 1;
                                    if (honeyLevel + i > 5) {
                                        i--;
                                    }

                                    // Paper start - Fire EntityChangeBlockEvent in more places
                                    BlockState newBlockState = state.setValue(BeehiveBlock.HONEY_LEVEL, honeyLevel + i);

                                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, newBlockState)) {
                                        level.setBlockAndUpdate(pos, newBlockState);
                                    }
                                    // Paper end - Fire EntityChangeBlockEvent in more places
                                }
                            }
                        }

                        if (storedInHives != null) {
                            storedInHives.add(bee);
                        }

                        /* CraftBukkit start - move up
                        float bbWidth = entity.getBbWidth();
                        double d = flag ? 0.0 : 0.55 + bbWidth / 2.0F;
                        double d1 = pos.getX() + 0.5 + d * direction.getStepX();
                        double d2 = pos.getY() + 0.5 - entity.getBbHeight() / 2.0F;
                        double d3 = pos.getZ() + 0.5 + d * direction.getStepZ();
                        entity.snapTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                        */ // CraftBukkit end
                    }

                    level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, level.getBlockState(pos)));
                    return true; // CraftBukkit - moved up
                } else {
                    return false;
                }
            }
        }
    }

    private boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    private static void tickOccupants(Level level, BlockPos pos, BlockState state, List<BeehiveBlockEntity.BeeData> data, @Nullable BlockPos savedFlowerPos) {
        boolean flag = false;
        Iterator<BeehiveBlockEntity.BeeData> iterator = data.iterator();

        while (iterator.hasNext()) {
            BeehiveBlockEntity.BeeData beeData = iterator.next();
            if (beeData.tick()) {
                BeehiveBlockEntity.BeeReleaseStatus beeReleaseStatus = beeData.hasNectar()
                    ? BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED
                    : BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED;
                if (releaseOccupant(level, pos, state, beeData.toOccupant(), null, beeReleaseStatus, savedFlowerPos)) {
                    flag = true;
                    iterator.remove();
                }
                // Paper start - Fix bees aging inside; use exitTickCounter to keep actual bee life
                else if (level.paperConfig().entities.behavior.cooldownFailedBeehiveReleases) {
                    beeData.exitTickCounter = beeData.occupant.minTicksInHive / 2;
                }
                // Paper end - Fix bees aging inside; use exitTickCounter to keep actual bee life
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BeehiveBlockEntity beehive) {
        tickOccupants(level, pos, state, beehive.stored, beehive.savedFlowerPos);
        if (!beehive.stored.isEmpty() && level.getRandom().nextDouble() < 0.005) {
            double d = pos.getX() + 0.5;
            double d1 = pos.getY();
            double d2 = pos.getZ() + 0.5;
            level.playSound(null, d, d1, d2, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.stored = Lists.newArrayList(); // CraftBukkit - SPIGOT-7790: create new copy (may be modified in physics event triggered by honey change)
        input.read("bees", BeehiveBlockEntity.Occupant.LIST_CODEC).orElse(List.of()).forEach(this::storeBee);
        this.savedFlowerPos = input.read("flower_pos", BlockPos.CODEC).orElse(null);
        this.maxBees = input.getIntOr("Bukkit.MaxEntities", MAX_OCCUPANTS); // Paper - persist max bukkit occupants
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("bees", BeehiveBlockEntity.Occupant.LIST_CODEC, this.getBees());
        output.storeNullable("flower_pos", BlockPos.CODEC, this.savedFlowerPos);
        output.putInt("Bukkit.MaxEntities", this.maxBees); // Paper - persist max bukkit occupants
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        super.applyImplicitComponents(componentGetter);
        this.stored = Lists.newArrayList(); // CraftBukkit - SPIGOT-7790: create new copy (may be modified in physics event triggered by honey change)
        List<BeehiveBlockEntity.Occupant> list = componentGetter.getOrDefault(DataComponents.BEES, Bees.EMPTY).bees();
        list.forEach(this::storeBee);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.BEES, new Bees(this.getBees()));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("bees");
    }

    private List<BeehiveBlockEntity.Occupant> getBees() {
        return this.stored.stream().map(BeehiveBlockEntity.BeeData::toOccupant).toList();
    }

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registrar) {
        registrar.register(DebugSubscriptions.BEE_HIVES, () -> DebugHiveInfo.pack(this));
    }

    static class BeeData {
        private final BeehiveBlockEntity.Occupant occupant;
        private int exitTickCounter; // Paper - Fix bees aging inside hives; separate counter for checking if bee should exit to reduce exit attempts
        private int ticksInHive;

        BeeData(BeehiveBlockEntity.Occupant occupant) {
            this.occupant = occupant;
            this.ticksInHive = occupant.ticksInHive();
            this.exitTickCounter = this.ticksInHive; // Paper - Fix bees aging inside hives
        }

        public boolean tick() {
            this.ticksInHive++; // Paper - Fix bees aging inside hives
            return this.exitTickCounter++ > this.occupant.minTicksInHive; // Paper - Fix bees aging inside hives
        }

        public BeehiveBlockEntity.Occupant toOccupant() {
            return new BeehiveBlockEntity.Occupant(this.occupant.entityData, this.ticksInHive, this.occupant.minTicksInHive);
        }

        public boolean hasNectar() {
            return this.occupant.entityData.getUnsafe().getBooleanOr("HasNectar", false);
        }
    }

    public static enum BeeReleaseStatus {
        HONEY_DELIVERED,
        BEE_RELEASED,
        EMERGENCY;
    }

    public record Occupant(TypedEntityData<EntityType<?>> entityData, int ticksInHive, int minTicksInHive) {
        public static final Codec<BeehiveBlockEntity.Occupant> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    TypedEntityData.codec(EntityType.CODEC).fieldOf("entity_data").forGetter(BeehiveBlockEntity.Occupant::entityData),
                    Codec.INT.fieldOf("ticks_in_hive").forGetter(BeehiveBlockEntity.Occupant::ticksInHive),
                    Codec.INT.fieldOf("min_ticks_in_hive").forGetter(BeehiveBlockEntity.Occupant::minTicksInHive)
                )
                .apply(instance, BeehiveBlockEntity.Occupant::new)
        );
        public static final Codec<List<BeehiveBlockEntity.Occupant>> LIST_CODEC = CODEC.listOf();
        public static final StreamCodec<RegistryFriendlyByteBuf, BeehiveBlockEntity.Occupant> STREAM_CODEC = StreamCodec.composite(
            TypedEntityData.streamCodec(EntityType.STREAM_CODEC),
            BeehiveBlockEntity.Occupant::entityData,
            ByteBufCodecs.VAR_INT,
            BeehiveBlockEntity.Occupant::ticksInHive,
            ByteBufCodecs.VAR_INT,
            BeehiveBlockEntity.Occupant::minTicksInHive,
            BeehiveBlockEntity.Occupant::new
        );

        public static BeehiveBlockEntity.Occupant of(Entity entity) {
            BeehiveBlockEntity.Occupant var5;
            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), BeehiveBlockEntity.LOGGER)) {
                TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
                entity.save(tagValueOutput);
                BeehiveBlockEntity.IGNORED_BEE_TAGS.forEach(tagValueOutput::discard);
                CompoundTag compoundTag = tagValueOutput.buildResult();
                boolean booleanOr = compoundTag.getBooleanOr("HasNectar", false);
                var5 = new BeehiveBlockEntity.Occupant(TypedEntityData.of(entity.getType(), compoundTag), 0, booleanOr ? 2400 : 600);
            }

            return var5;
        }

        public static BeehiveBlockEntity.Occupant create(int ticksInHive) {
            return new BeehiveBlockEntity.Occupant(TypedEntityData.of(EntityType.BEE, new CompoundTag()), ticksInHive, 600);
        }

        public @Nullable Entity createEntity(Level level, BlockPos pos) {
            CompoundTag compoundTag = this.entityData.copyTagWithoutId();
            BeehiveBlockEntity.IGNORED_BEE_TAGS.forEach(compoundTag::remove);
            Entity entity = EntityType.loadEntityRecursive(this.entityData.type(), compoundTag, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
            if (entity != null && entity.getType().is(EntityTypeTags.BEEHIVE_INHABITORS)) {
                entity.setNoGravity(true);
                if (entity instanceof Bee bee) {
                    bee.setHivePos(pos);
                    setBeeReleaseData(this.ticksInHive, bee);
                }

                return entity;
            } else {
                return null;
            }
        }

        private static void setBeeReleaseData(int ticksInHive, Bee bee) {
            if (!bee.ageLocked) { // Paper - Honor ageLock
            int age = bee.getAge();
            if (age < 0) {
                bee.setAge(Math.min(0, age + ticksInHive));
            } else if (age > 0) {
                bee.setAge(Math.max(0, age - ticksInHive));
            }

            bee.setInLoveTime(Math.max(0, bee.getInLoveTime() - ticksInHive));
            } // Paper - Honor ageLock
        }
    }
}
