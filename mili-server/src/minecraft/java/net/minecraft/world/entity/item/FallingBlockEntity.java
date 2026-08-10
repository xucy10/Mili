package net.minecraft.world.entity.item;

import com.mojang.logging.LogUtils;
import java.util.function.Predicate;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class FallingBlockEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockState DEFAULT_BLOCK_STATE = Blocks.SAND.defaultBlockState();
    private static final int DEFAULT_TIME = 0;
    private static final float DEFAULT_FALL_DAMAGE_PER_DISTANCE = 0.0F;
    private static final int DEFAULT_MAX_FALL_DAMAGE = 40;
    private static final boolean DEFAULT_DROP_ITEM = true;
    private static final boolean DEFAULT_CANCEL_DROP = false;
    public BlockState blockState = DEFAULT_BLOCK_STATE;
    public int time = 0;
    public boolean dropItem = true;
    public boolean cancelDrop = false;
    public boolean hurtEntities;
    public int fallDamageMax = 40;
    public float fallDamagePerDistance = 0.0F;
    public @Nullable CompoundTag blockData;
    public boolean forceTickAfterTeleportToDuplicate = me.earthme.luminol.config.modules.fixes.UnsafeTeleportationConfig.enabled; // Luminol - Unsafe teleportation
    protected static final EntityDataAccessor<BlockPos> DATA_START_POS = SynchedEntityData.defineId(FallingBlockEntity.class, EntityDataSerializers.BLOCK_POS);
    public boolean autoExpire = true; // Paper - Expand FallingBlock API

    public FallingBlockEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    public FallingBlockEntity(Level level, double x, double y, double z, BlockState state) {
        this(EntityType.FALLING_BLOCK, level);
        this.blockState = state;
        this.blocksBuilding = true;
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setStartPos(this.blockPosition());
    }

    public static FallingBlockEntity fall(Level level, BlockPos pos, BlockState state) {
        FallingBlockEntity fallingBlockEntity = new FallingBlockEntity(
            level,
            pos.getX() + 0.5,
            pos.getY(),
            pos.getZ() + 0.5,
            state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state
        );
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(fallingBlockEntity, pos, state.getFluidState().createLegacyBlock())) return fallingBlockEntity; // CraftBukkit
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        level.addFreshEntity(fallingBlockEntity);
        return fallingBlockEntity;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (!this.isInvulnerableToBase(damageSource)) {
            this.markHurt();
        }

        return false;
    }

    public void setStartPos(BlockPos startPos) {
        this.entityData.set(DATA_START_POS, startPos);
    }

    public BlockPos getStartPos() {
        return this.entityData.get(DATA_START_POS);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_START_POS, BlockPos.ZERO);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void tick() {
        if (this.blockState.isAir()) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            Block block = this.blockState.getBlock();
            this.time++;
            this.applyGravity();
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.applyEffectsFromBlocks();
            // Paper start - Configurable falling blocks height nerf
            if (this.level().paperConfig().fixes.fallingBlockHeightNerf.test(v -> this.getY() > v)) {
                if (this.dropItem && this.level() instanceof final ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
                    this.spawnAtLocation(serverLevel, block);
                }
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.OUT_OF_WORLD);
                return;
            }
            // Paper end - Configurable falling blocks height nerf
            //this.handlePortal(); // Folia - region threading
            if (this.level() instanceof ServerLevel serverLevel && (this.isAlive() || this.forceTickAfterTeleportToDuplicate)) {
                BlockPos blockPos = this.blockPosition();
                boolean flag = this.blockState.getBlock() instanceof ConcretePowderBlock;
                boolean flag1 = flag && this.level().getFluidState(blockPos).is(FluidTags.WATER);
                double d = this.getDeltaMovement().lengthSqr();
                if (flag && d > 1.0) {
                    BlockHitResult blockHitResult = this.level()
                        .clip(
                            new ClipContext(
                                new Vec3(this.xo, this.yo, this.zo), this.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, this
                            )
                        );
                    if (blockHitResult.getType() != HitResult.Type.MISS && this.level().getFluidState(blockHitResult.getBlockPos()).is(FluidTags.WATER)) {
                        blockPos = blockHitResult.getBlockPos();
                        flag1 = true;
                    }
                }

                if (!this.onGround() && !flag1) {
                    if ((this.time > 100 && autoExpire) && (blockPos.getY() <= this.level().getMinY() || blockPos.getY() > this.level().getMaxY()) || (this.time > 600 && autoExpire)) { // Paper - Expand FallingBlock API
                        if (this.dropItem && serverLevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
                            this.spawnAtLocation(serverLevel, block);
                        }

                        this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                    }
                } else {
                    BlockState blockState = this.level().getBlockState(blockPos);
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
                    if (!blockState.is(Blocks.MOVING_PISTON)) {
                        if (!this.cancelDrop) {
                            boolean canBeReplaced = blockState.canBeReplaced(
                                new DirectionalPlaceContext(this.level(), blockPos, Direction.DOWN, ItemStack.EMPTY, Direction.UP)
                            );
                            boolean flag2 = FallingBlock.isFree(this.level().getBlockState(blockPos.below())) && (!flag || !flag1);
                            boolean flag3 = this.blockState.canSurvive(this.level(), blockPos) && !flag2;
                            if (canBeReplaced && flag3) {
                                if (this.blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                                    && this.level().getFluidState(blockPos).getType() == Fluids.WATER) {
                                    this.blockState = this.blockState.setValue(BlockStateProperties.WATERLOGGED, true);
                                }

                                // CraftBukkit start
                                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, blockPos, this.blockState)) {
                                    this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // SPIGOT-6586 called before the event in previous versions
                                    return;
                                }
                                // CraftBukkit end
                                if (this.level().setBlock(blockPos, this.blockState, Block.UPDATE_ALL)) {
                                    serverLevel.getChunkSource()
                                        .chunkMap
                                        .sendToTrackingPlayers(this, new ClientboundBlockUpdatePacket(blockPos, this.level().getBlockState(blockPos)));
                                    this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                                    if (block instanceof Fallable fallable) {
                                        fallable.onLand(this.level(), blockPos, this.blockState, blockState, this);
                                    }

                                    if (this.blockData != null && this.blockState.hasBlockEntity()) {
                                        BlockEntity blockEntity = this.level().getBlockEntity(blockPos);
                                        if (blockEntity != null) {
                                            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(
                                                    blockEntity.problemPath(), LOGGER
                                                )) {
                                                RegistryAccess registryAccess = this.level().registryAccess();
                                                TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, registryAccess);
                                                blockEntity.saveWithoutMetadata(tagValueOutput);
                                                CompoundTag compoundTag = tagValueOutput.buildResult();
                                                this.blockData.forEach((string, tag) -> compoundTag.put(string, tag.copy()));
                                                blockEntity.loadWithComponents(TagValueInput.create(scopedCollector, registryAccess, compoundTag));
                                            } catch (Exception var19) {
                                                LOGGER.error("Failed to load block entity from falling block", (Throwable)var19);
                                            }

                                            blockEntity.setChanged();
                                        }
                                    }
                                } else if (this.dropItem && serverLevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
                                    this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                                    this.callOnBrokenAfterFall(block, blockPos);
                                    this.spawnAtLocation(serverLevel, block);
                                }
                            } else {
                                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                                if (this.dropItem && serverLevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
                                    this.callOnBrokenAfterFall(block, blockPos);
                                    this.spawnAtLocation(serverLevel, block);
                                }
                            }
                        } else {
                            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                            this.callOnBrokenAfterFall(block, blockPos);
                        }
                    }
                }
            }

            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        }
    }

    public void callOnBrokenAfterFall(Block block, BlockPos pos) {
        if (block instanceof Fallable) {
            ((Fallable)block).onBrokenAfterFall(this.level(), pos, this);
        }
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (!this.hurtEntities) {
            return false;
        } else {
            int ceil = Mth.ceil(fallDistance - 1.0);
            if (ceil < 0) {
                return false;
            } else {
                Predicate<Entity> predicate = fun.bm.mili.config.modules.misc.OldMCConfig.allowAnvilDestroyItemEntities ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE); // Mili - Allow anvil destroy item entities
                DamageSource damageSource1 = this.blockState.getBlock() instanceof Fallable fallable
                    ? fallable.getFallDamageSource(this)
                    : this.damageSources().fallingBlock(this);
                float f = Math.min(Mth.floor(ceil * this.fallDamagePerDistance), this.fallDamageMax);
                this.level().getEntities(this, this.getBoundingBox(), predicate).forEach(entity -> entity.hurt(damageSource1, f));
                boolean isAnvil = this.blockState.is(BlockTags.ANVIL);
                if (isAnvil && f > 0.0F && this.random.nextFloat() < 0.05F + ceil * 0.05F) {
                    BlockState blockState = AnvilBlock.damage(this.blockState);
                    if (blockState == null) {
                        this.cancelDrop = true;
                    } else {
                        this.blockState = blockState;
                    }
                }

                return false;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("BlockState", BlockState.CODEC, this.blockState);
        output.putInt("Time", this.time);
        output.putBoolean("DropItem", this.dropItem);
        output.putBoolean("HurtEntities", this.hurtEntities);
        output.putFloat("FallHurtAmount", this.fallDamagePerDistance);
        output.putInt("FallHurtMax", this.fallDamageMax);
        if (this.blockData != null) {
            output.store("TileEntityData", CompoundTag.CODEC, this.blockData);
        }

        output.putBoolean("CancelDrop", this.cancelDrop);
        if (!this.autoExpire) output.putBoolean("Paper.AutoExpire", false); // Paper - Expand FallingBlock API
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.blockState = input.read("BlockState", BlockState.CODEC).orElse(DEFAULT_BLOCK_STATE);
        this.time = input.getIntOr("Time", 0);
        boolean isAnvil = this.blockState.is(BlockTags.ANVIL);
        this.hurtEntities = input.getBooleanOr("HurtEntities", isAnvil);
        this.fallDamagePerDistance = input.getFloatOr("FallHurtAmount", 0.0F);
        this.fallDamageMax = input.getIntOr("FallHurtMax", 40);
        this.dropItem = input.getBooleanOr("DropItem", true);
        this.blockData = input.read("TileEntityData", CompoundTag.CODEC).map(blockData -> this.level().paperConfig().entities.spawning.filterBadTileEntityNbtFromFallingBlocks && this.blockState.getBlock() instanceof net.minecraft.world.level.block.GameMasterBlock ? null : blockData).map(CompoundTag::copy).orElse(null); // Paper - Filter bad block entity nbt data from falling blocks
        this.cancelDrop = input.getBooleanOr("CancelDrop", false);
        this.autoExpire = input.getBooleanOr("Paper.AutoExpire", true); // Paper - Expand FallingBlock API
    }

    public void setHurtsEntities(float fallDamagePerDistance, int fallDamageMax) {
        this.hurtEntities = true;
        this.fallDamagePerDistance = fallDamagePerDistance;
        this.fallDamageMax = fallDamageMax;
    }

    public void disableDrop() {
        this.cancelDrop = true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory category) {
        super.fillCrashReportCategory(category);
        category.setDetail("Immitating BlockState", this.blockState.toString());
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.minecraft.falling_block_type", this.blockState.getBlock().getName());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity, Block.getId(this.getBlockState()));
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.blockState = Block.stateById(packet.getData());
        this.blocksBuilding = true;
        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();
        this.setPos(x, y, z);
        this.setStartPos(this.blockPosition());
    }

    @Override
    public @Nullable Entity teleport(TeleportTransition teleportTransition) {
        ResourceKey<Level> resourceKey = teleportTransition.newLevel().dimension();
        ResourceKey<Level> resourceKey1 = this.level().dimension();
        boolean flag = (resourceKey1 == Level.END || resourceKey == Level.END) && resourceKey1 != resourceKey;
        Entity entity = super.teleport(teleportTransition);
        this.forceTickAfterTeleportToDuplicate = (entity != null && flag) && (fun.bm.mili.config.modules.function.OldFeatureConfig.fixFallingBlockEntityDuplicate || io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowUnsafeEndPortalTeleportation || me.earthme.luminol.config.modules.fixes.UnsafeTeleportationConfig.enabled); // Paper // Luminol - Unsafe teleportation // Leaves
        return entity;
    }
}
