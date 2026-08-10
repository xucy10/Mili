package net.minecraft.world.level.block.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class BeaconBlockEntity extends BlockEntity implements MenuProvider, Nameable, BeaconBeamOwner {
    private static final int MAX_LEVELS = 4;
    public static final List<List<Holder<MobEffect>>> BEACON_EFFECTS = List.of(
        List.of(MobEffects.SPEED, MobEffects.HASTE),
        List.of(MobEffects.RESISTANCE, MobEffects.JUMP_BOOST),
        List.of(MobEffects.STRENGTH),
        List.of(MobEffects.REGENERATION)
    );
    private static final Set<Holder<MobEffect>> VALID_EFFECTS = BEACON_EFFECTS.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    public static final int DATA_LEVELS = 0;
    public static final int DATA_PRIMARY = 1;
    public static final int DATA_SECONDARY = 2;
    public static final int NUM_DATA_VALUES = 3;
    private static final int BLOCKS_CHECK_PER_TICK = 10;
    private static final Component DEFAULT_NAME = Component.translatable("container.beacon");
    private static final String TAG_PRIMARY = "primary_effect";
    private static final String TAG_SECONDARY = "secondary_effect";
    List<BeaconBeamOwner.Section> beamSections = new ArrayList<>();
    private List<BeaconBeamOwner.Section> checkingBeamSections = new ArrayList<>();
    public int levels;
    private int lastCheckY;
    @Nullable public Holder<MobEffect> primaryPower;
    @Nullable public Holder<MobEffect> secondaryPower;
    public @Nullable Component name;
    public LockCode lockKey = LockCode.NO_LOCK;
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> BeaconBlockEntity.this.levels;
                case 1 -> BeaconMenu.encodeEffect(BeaconBlockEntity.this.primaryPower);
                case 2 -> BeaconMenu.encodeEffect(BeaconBlockEntity.this.secondaryPower);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0:
                    BeaconBlockEntity.this.levels = value;
                    break;
                case 1:
                    if (!BeaconBlockEntity.this.level.isClientSide() && !BeaconBlockEntity.this.beamSections.isEmpty()) {
                        BeaconBlockEntity.playSound(BeaconBlockEntity.this.level, BeaconBlockEntity.this.worldPosition, SoundEvents.BEACON_POWER_SELECT);
                    }

                    BeaconBlockEntity.this.primaryPower = BeaconBlockEntity.filterEffect(BeaconMenu.decodeEffect(value));
                    break;
                case 2:
                    BeaconBlockEntity.this.secondaryPower = BeaconBlockEntity.filterEffect(BeaconMenu.decodeEffect(value));
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };
    // CraftBukkit start - add fields and methods
    public org.bukkit.potion.@Nullable PotionEffect getPrimaryEffect() {
        return (this.primaryPower != null)
            ? org.bukkit.craftbukkit.potion.CraftPotionUtil.toBukkit(new MobEffectInstance(
                this.primaryPower,
                BeaconBlockEntity.computeEffectDuration(this.levels),
                BeaconBlockEntity.computeEffectAmplifier(this.levels, this.primaryPower, this.secondaryPower),
                true,
                true
            ))
            : null;
    }

    public org.bukkit.potion.@Nullable PotionEffect getSecondaryEffect() {
        return (BeaconBlockEntity.hasSecondaryEffect(this.levels, this.primaryPower, this.secondaryPower))
            ? org.bukkit.craftbukkit.potion.CraftPotionUtil.toBukkit(new MobEffectInstance(
                this.secondaryPower,
                BeaconBlockEntity.computeEffectDuration(this.levels),
                BeaconBlockEntity.computeEffectAmplifier(this.levels, this.primaryPower, this.secondaryPower),
                true,
                true
            ))
            : null;
    }
    // CraftBukkit end
    // Paper start - Custom beacon ranges
    private final String PAPER_RANGE_TAG = "Paper.Range";
    private double effectRange = -1;

    public double getEffectRange() {
        if (this.effectRange < 0) {
            return this.levels * 10 + 10;
        } else {
            return effectRange;
        }
    }

    public void setEffectRange(double range) {
        this.effectRange = range;
    }

    public void resetEffectRange() {
        this.effectRange = -1;
    }
    // Paper end - Custom beacon ranges

    static @Nullable Holder<MobEffect> filterEffect(@Nullable Holder<MobEffect> effect) {
        return VALID_EFFECTS.contains(effect) ? effect : null;
    }

    public BeaconBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.BEACON, pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BeaconBlockEntity blockEntity) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockPos blockPos;
        if (blockEntity.lastCheckY < y) {
            blockPos = pos;
            blockEntity.checkingBeamSections = Lists.newArrayList();
            blockEntity.lastCheckY = pos.getY() - 1;
        } else {
            blockPos = new BlockPos(x, blockEntity.lastCheckY + 1, z);
        }

        BeaconBeamOwner.Section section = blockEntity.checkingBeamSections.isEmpty()
            ? null
            : blockEntity.checkingBeamSections.get(blockEntity.checkingBeamSections.size() - 1);
        int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

        for (int i = 0; i < 10 && blockPos.getY() <= height; i++) {
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.getBlock() instanceof BeaconBeamBlock beaconBeamBlock) {
                int textureDiffuseColor = beaconBeamBlock.getColor().getTextureDiffuseColor();
                if (blockEntity.checkingBeamSections.size() <= 1) {
                    section = new BeaconBeamOwner.Section(textureDiffuseColor);
                    blockEntity.checkingBeamSections.add(section);
                } else if (section != null) {
                    if (textureDiffuseColor == section.getColor()) {
                        section.increaseHeight();
                    } else {
                        section = new BeaconBeamOwner.Section(ARGB.average(section.getColor(), textureDiffuseColor));
                        blockEntity.checkingBeamSections.add(section);
                    }
                }
            } else {
                if (section == null || blockState.getLightBlock() >= 15 && !blockState.is(Blocks.BEDROCK)) {
                    blockEntity.checkingBeamSections.clear();
                    blockEntity.lastCheckY = height;
                    break;
                }

                section.increaseHeight();
            }

            blockPos = blockPos.above();
            blockEntity.lastCheckY++;
        }

        int i = blockEntity.levels; final int originalLevels = i; // Paper - OBFHELPER
        if (level.getRedstoneGameTime() % 80L == 0L) { // Folia - region threading
            if (!blockEntity.beamSections.isEmpty()) {
                blockEntity.levels = updateBase(level, x, y, z);
            }

            if (blockEntity.levels > 0 && !blockEntity.beamSections.isEmpty()) {
                applyEffects(level, pos, blockEntity.levels, blockEntity.primaryPower, blockEntity.secondaryPower, blockEntity); // Paper - Custom beacon ranges
                playSound(level, pos, SoundEvents.BEACON_AMBIENT);
            }
        }
        // Paper start - beacon activation/deactivation events
        if (originalLevels <= 0 && blockEntity.levels > 0) {
            org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
            new io.papermc.paper.event.block.BeaconActivatedEvent(block).callEvent();
        } else if (originalLevels > 0 && blockEntity.levels <= 0) {
            org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
            new io.papermc.paper.event.block.BeaconDeactivatedEvent(block).callEvent();
        }
        // Paper end - beacon activation/deactivation events

        if (blockEntity.lastCheckY >= height) {
            blockEntity.lastCheckY = level.getMinY() - 1;
            boolean flag = i > 0;
            blockEntity.beamSections = blockEntity.checkingBeamSections;
            if (!level.isClientSide()) {
                boolean flag1 = blockEntity.levels > 0;
                if (!flag && flag1) {
                    playSound(level, pos, SoundEvents.BEACON_ACTIVATE);

                    for (ServerPlayer serverPlayer : level.getEntitiesOfClass(ServerPlayer.class, new AABB(x, y, z, x, y - 4, z).inflate(10.0, 5.0, 10.0))) {
                        CriteriaTriggers.CONSTRUCT_BEACON.trigger(serverPlayer, blockEntity.levels);
                    }
                } else if (flag && !flag1) {
                    playSound(level, pos, SoundEvents.BEACON_DEACTIVATE);
                }
            }
        }
    }

    private static int updateBase(Level level, int x, int y, int z) {
        int i = 0;

        for (int i1 = 1; i1 <= 4; i = i1++) {
            int i2 = y - i1;
            if (i2 < level.getMinY()) {
                break;
            }

            boolean flag = true;

            for (int i3 = x - i1; i3 <= x + i1 && flag; i3++) {
                for (int i4 = z - i1; i4 <= z + i1; i4++) {
                    if (!level.getBlockState(new BlockPos(i3, i2, i4)).is(BlockTags.BEACON_BASE_BLOCKS)) {
                        flag = false;
                        break;
                    }
                }
            }

            if (!flag) {
                break;
            }
        }

        return i;
    }

    @Override
    public void setRemoved() {
        // Paper start - beacon activation/deactivation events
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, worldPosition);
        new io.papermc.paper.event.block.BeaconDeactivatedEvent(block).callEvent();
        // Paper end - beacon activation/deactivation events
        // Paper start - fix MC-153086
        if (this.levels > 0 && !this.beamSections.isEmpty()) {
        playSound(this.level, this.worldPosition, SoundEvents.BEACON_DEACTIVATE);
        }
        // Paper end
        super.setRemoved();
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - pass beacon block entity
    private static void applyEffects(
        Level level, BlockPos pos, int beaconLevel, @Nullable Holder<MobEffect> primaryEffect, @Nullable Holder<MobEffect> secondaryEffect
    ) {
        // Paper start - pass beacon block entity
        applyEffects(level, pos, beaconLevel, primaryEffect, secondaryEffect, null);
    }

    private static void applyEffects(
        Level level, BlockPos pos, int beaconLevel, @Nullable Holder<MobEffect> primaryEffect, @Nullable Holder<MobEffect> secondaryEffect, @Nullable BeaconBlockEntity blockEntity
    ) {
        // Paper end - pass beacon block entity
        if (!level.isClientSide() && primaryEffect != null) {
            double d = computeBeaconRange(beaconLevel); // Paper - diff out applyEffects logic components - see below
            int i = computeEffectAmplifier(beaconLevel, primaryEffect, secondaryEffect); // Paper - diff out applyEffects logic components - see below

            int i1 = computeEffectDuration(beaconLevel); // Paper - diff out applyEffects logic components - see below
            List<Player> entitiesOfClass = getHumansInRange(level, pos, beaconLevel, blockEntity); // Paper - diff out applyEffects logic components - see below

            applyEffectsAndCallEvent(level, pos, entitiesOfClass, new MobEffectInstance(primaryEffect, i1, i, true, true), true); // Paper - BeaconEffectEvent

            if (hasSecondaryEffect(beaconLevel, primaryEffect, secondaryEffect)) { // Paper - diff out applyEffects logic components - see below
                applyEffectsAndCallEvent(level, pos, entitiesOfClass, new MobEffectInstance(secondaryEffect, i1, 0, true, true), false); // Paper - BeaconEffectEvent
            }
        }
    }

    // Paper start - diff out applyEffects logic components
    // Generally smarter than spigot trying to split the logic up, as that diff is giant.
    private static int computeEffectDuration(final int beaconLevel) {
        return (9 + beaconLevel * 2) * 20; // Diff from applyEffects
    }

    private static int computeEffectAmplifier(final int beaconLevel, @Nullable Holder<MobEffect> primaryEffect, @Nullable Holder<MobEffect> secondaryEffect) {
        int i = 0;
        if (beaconLevel >= 4 && Objects.equals(primaryEffect, secondaryEffect)) {
            i = 1;
        }
        return i;
    }

    private static double computeBeaconRange(final int beaconLevel) {
        return beaconLevel * 10 + 10; // Diff from applyEffects
    }

    public static List<Player> getHumansInRange(final Level level, final BlockPos pos, final int beaconLevel, final @Nullable BeaconBlockEntity blockEntity) {
        final double d = blockEntity != null ? blockEntity.getEffectRange() : computeBeaconRange(beaconLevel);
        AABB aabb = new AABB(pos).inflate(d).expandTowards(0.0, level.getHeight(), 0.0); // Diff from applyEffects
        // Improve performance of human lookup by switching to a global player iteration when searching over 128 blocks
        List<Player> list;
        if (d <= 128.0) {
            list = level.getEntitiesOfClass(Player.class, aabb); // Diff from applyEffect
        } else {
            list = new java.util.ArrayList<>();
            for (final Player player : level.getLocalPlayers()) { // Folia - region threading
                if (!net.minecraft.world.entity.EntitySelector.NO_SPECTATORS.test(player)) continue;
                if (player.getBoundingBox().intersects(aabb)) {
                    list.add(player);
                }
            }
        }
        return list;
    }

    private static boolean hasSecondaryEffect(final int beaconLevel, final Holder<MobEffect> primaryEffect, final @Nullable Holder<MobEffect> secondaryEffect) {
        return beaconLevel >= 4 && !Objects.equals(primaryEffect, secondaryEffect) && secondaryEffect != null;
    }
    // Paper end - diff out applyEffects logic components

    // Paper start - BeaconEffectEvent
    private static void applyEffectsAndCallEvent(final Level level, final BlockPos position, final List<Player> players, final MobEffectInstance mobEffectInstance, final boolean isPrimary) {
        final org.bukkit.potion.PotionEffect apiEffect = org.bukkit.craftbukkit.potion.CraftPotionUtil.toBukkit(mobEffectInstance);
        final org.bukkit.craftbukkit.block.CraftBlock apiBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, position);
        for (final Player player : players) {
            final com.destroystokyo.paper.event.block.BeaconEffectEvent event = new com.destroystokyo.paper.event.block.BeaconEffectEvent(
                apiBlock, apiEffect, (org.bukkit.entity.Player) player.getBukkitEntity(), isPrimary
            );
            if (!event.callEvent()) continue;
            player.addEffect(org.bukkit.craftbukkit.potion.CraftPotionUtil.fromBukkit(event.getEffect()), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.BEACON);
        }
    }
    // Paper end - BeaconEffectEvent

    public static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public List<BeaconBeamOwner.Section> getBeamSections() {
        return (List<BeaconBeamOwner.Section>)(this.levels == 0 ? ImmutableList.of() : this.beamSections);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    private static void storeEffect(ValueOutput output, String key, @Nullable Holder<MobEffect> effect) {
        if (effect != null) {
            effect.unwrapKey().ifPresent(resourceKey -> output.putString(key, resourceKey.identifier().toString()));
        }
    }

    private static @Nullable Holder<MobEffect> loadEffect(ValueInput input, String key) {
        return input.read(key, BuiltInRegistries.MOB_EFFECT.holderByNameCodec()).orElse(null); // CraftBukkit - persist manually set non-default beacon effects (SPIGOT-3598)
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.primaryPower = loadEffect(input, "primary_effect");
        this.secondaryPower = loadEffect(input, "secondary_effect");
        this.levels = input.getIntOr("Levels", 0); // CraftBukkit - SPIGOT-5053, use where available
        this.name = parseCustomNameSafe(input, "CustomName");
        this.lockKey = LockCode.fromTag(input);
        this.effectRange = input.getDoubleOr(PAPER_RANGE_TAG, -1); // Paper - Custom beacon ranges
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        storeEffect(output, "primary_effect", this.primaryPower);
        storeEffect(output, "secondary_effect", this.secondaryPower);
        output.putInt("Levels", this.levels);
        output.storeNullable("CustomName", ComponentSerialization.CODEC, this.name);
        this.lockKey.addToTag(output);
        output.putDouble(PAPER_RANGE_TAG, this.effectRange); // Paper - Custom beacon ranges
    }

    public void setCustomName(@Nullable Component name) {
        this.name = name;
    }

    @Override
    public @Nullable Component getCustomName() {
        return this.name;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockLockCheckEvent(this, this.lockKey, this.getDisplayName(), player)) { // Paper - Call BlockLockCheckEvent
            return new BeaconMenu(containerId, playerInventory, this.dataAccess, ContainerLevelAccess.create(this.level, this.getBlockPos()));
        } else {
            BaseContainerBlockEntity.sendChestLockedNotifications(this.getBlockPos().getCenter(), player, this.getDisplayName());
            return null;
        }
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : DEFAULT_NAME;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        super.applyImplicitComponents(componentGetter);
        this.name = componentGetter.get(DataComponents.CUSTOM_NAME);
        this.lockKey = componentGetter.getOrDefault(DataComponents.LOCK, LockCode.NO_LOCK);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.name);
        if (!this.lockKey.equals(LockCode.NO_LOCK)) {
            components.set(DataComponents.LOCK, this.lockKey);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("CustomName");
        output.discard("lock");
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.lastCheckY = level.getMinY() - 1;
    }
}
