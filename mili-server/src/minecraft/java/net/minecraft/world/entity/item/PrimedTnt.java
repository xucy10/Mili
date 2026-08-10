package net.minecraft.world.entity.item;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class PrimedTnt extends Entity implements TraceableEntity {
    private static final EntityDataAccessor<Integer> DATA_FUSE_ID = SynchedEntityData.defineId(PrimedTnt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE_ID = SynchedEntityData.defineId(PrimedTnt.class, EntityDataSerializers.BLOCK_STATE);
    private static final short DEFAULT_FUSE_TIME = 80;
    private static final float DEFAULT_EXPLOSION_POWER = 4.0F;
    private static final BlockState DEFAULT_BLOCK_STATE = Blocks.TNT.defaultBlockState();
    private static final String TAG_BLOCK_STATE = "block_state";
    public static final String TAG_FUSE = "fuse";
    private static final String TAG_EXPLOSION_POWER = "explosion_power";
    private static final ExplosionDamageCalculator USED_PORTAL_DAMAGE_CALCULATOR = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
            return !state.is(Blocks.NETHER_PORTAL) && super.shouldBlockExplode(explosion, level, pos, state, power);
        }

        @Override
        public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid) {
            return state.is(Blocks.NETHER_PORTAL) ? Optional.empty() : super.getBlockExplosionResistance(explosion, level, pos, state, fluid);
        }
    };
    public @Nullable EntityReference<LivingEntity> owner;
    private boolean usedPortal;
    public float explosionPower = 4.0F;
    public boolean isIncendiary = false; // CraftBukkit

    public PrimedTnt(EntityType<? extends PrimedTnt> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public PrimedTnt(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
        this(EntityType.TNT, level);
        this.setPos(x, y, z);
        double d = this.random.nextDouble() * (float) (Math.PI * 2); // Paper - Don't use level random in entity constructors
        if (fun.bm.mili.carpet.config.modules.GeneralCompatConfig.tntPrimerMomentumRemoved) {
            this.setDeltaMovement(0.0, 0.20000000298023224D, 0.0);
        } else {
            this.setDeltaMovement(-Math.sin(d) * 0.02, 0.2F, -Math.cos(d) * 0.02);
        }
        this.setFuse(getConfiguredFuseTime());
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.owner = EntityReference.of(owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUSE_ID, getConfiguredFuseTime());
        builder.define(DATA_BLOCK_STATE_ID, DEFAULT_BLOCK_STATE);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
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
        if (this.level().spigotConfig.maxTntTicksPerTick > 0 && ++this.level().getCurrentWorldData().currentPrimedTnt > (fun.bm.mili.config.modules.function.TechnicalSurvivalModeConfig.enabled ? 2000 : this.level().spigotConfig.maxTntTicksPerTick)) { return; } // Spigot // Folia - region threading // Mili - mc technical survival mode
        //this.handlePortal(); // Folia - region threading
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.applyEffectsFromBlocks();
        // Paper start - Configurable TNT height nerf
        if (this.level().paperConfig().fixes.tntEntityHeightNerf.test(v -> this.getY() > v)) {
            this.discard(EntityRemoveEvent.Cause.OUT_OF_WORLD);
            return;
        }
        // Paper end - Configurable TNT height nerf
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }

        int i = this.getFuse() - 1;
        this.setFuse(i);
        if (i <= 0) {
            // CraftBukkit start - Need to reverse the order of the explosion and the entity death so we have a location for the event
            //this.discard();
            if (!this.level().isClientSide()) {
                this.explode();
            }
            this.discard(EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
            // CraftBukkit end
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide()) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
            }
        }
        // Paper start - Option to prevent TNT from moving in water
        if (!this.isRemoved() && this.wasTouchingWater && this.level().paperConfig().fixes.preventTntFromMovingInWater) {
            this.hurtMarked = true;
            this.needsSync = true;
        }
        // Paper end - Option to prevent TNT from moving in water
    }

    private void explode() {
        if (this.level() instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
            // CraftBukkit start
            ExplosionPrimeEvent event = CraftEventFactory.callExplosionPrimeEvent((org.bukkit.entity.Explosive) this.getBukkitEntity());
            if (event.isCancelled()) {
                return;
            }
            // CraftBukkit end
            this.level()
                .explode(
                    this,
                    Explosion.getDefaultDamageSource(this.level(), this),
                    this.usedPortal ? USED_PORTAL_DAMAGE_CALCULATOR : null,
                    this.getX(),
                    this.getY(0.0625),
                    this.getZ(),
                    event.getRadius(), // CraftBukkit
                    event.getFire(), // CraftBukkit
                    Level.ExplosionInteraction.TNT
                );
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putShort("fuse", (short)this.getFuse());
        output.store("block_state", BlockState.CODEC, this.getBlockState());
        if (this.explosionPower != 4.0F) {
            output.putFloat("explosion_power", this.explosionPower);
        }

        EntityReference.store(this.owner, output, "owner");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setFuse(input.getShortOr("fuse", (short)getConfiguredFuseTime()));
        this.setBlockState(input.read("block_state", BlockState.CODEC).orElse(DEFAULT_BLOCK_STATE));
        this.explosionPower = Mth.clamp(input.getFloatOr("explosion_power", 4.0F), 0.0F, 128.0F);
        this.owner = EntityReference.read(input, "owner");
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        return EntityReference.getLivingEntity(this.owner, this.level());
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof PrimedTnt primedTnt) {
            this.owner = primedTnt.owner;
        }
    }

    public void setFuse(int life) {
        this.entityData.set(DATA_FUSE_ID, life);
    }

    private static int getConfiguredFuseTime() {
        return fun.bm.mili.carpet.config.modules.GeneralCompatConfig.normalizedTntFuseDuration();
    }

    public int getFuse() {
        return this.entityData.get(DATA_FUSE_ID);
    }

    public void setBlockState(BlockState state) {
        this.entityData.set(DATA_BLOCK_STATE_ID, state);
    }

    public BlockState getBlockState() {
        return this.entityData.get(DATA_BLOCK_STATE_ID);
    }

    private void setUsedPortal(boolean usedPortal) {
        this.usedPortal = usedPortal;
    }

    @Override
    public @Nullable Entity teleport(TeleportTransition teleportTransition) {
        Entity entity = super.teleport(teleportTransition);
        // Folia - region threading - move to post change

        return entity;
    }

    // Folia start - region threading
    @Override
    public void postChangeDimension() {
        super.postChangeDimension();
        if (this instanceof PrimedTnt primedTnt) { // Folia - region threading - move to post change
            primedTnt.setUsedPortal(true);
        }
    }
    // Folia end - region threading

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    // Paper start - Option to prevent TNT from moving in water
    @Override
    public boolean isPushedByFluid() {
        return !this.level().paperConfig().fixes.preventTntFromMovingInWater && super.isPushedByFluid();
    }
    // Paper end - Option to prevent TNT from moving in water
}
