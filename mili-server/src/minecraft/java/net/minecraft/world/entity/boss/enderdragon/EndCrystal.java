package net.minecraft.world.entity.boss.enderdragon;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class EndCrystal extends Entity {
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_BEAM_TARGET = SynchedEntityData.defineId(
        EndCrystal.class, EntityDataSerializers.OPTIONAL_BLOCK_POS
    );
    private static final EntityDataAccessor<Boolean> DATA_SHOW_BOTTOM = SynchedEntityData.defineId(EndCrystal.class, EntityDataSerializers.BOOLEAN);
    private static final boolean DEFAULT_SHOW_BOTTOM = true;
    public int time;
    public boolean generatedByDragonFight = false; // Paper - Fix invulnerable end crystals

    public EndCrystal(EntityType<? extends EndCrystal> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.time = this.random.nextInt(100000);
    }

    public EndCrystal(Level level, double x, double y, double z) {
        this(EntityType.END_CRYSTAL, level);
        this.setPos(x, y, z);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BEAM_TARGET, Optional.empty());
        builder.define(DATA_SHOW_BOTTOM, true);
    }

    @Override
    public void tick() {
        this.time++;
        this.applyEffectsFromBlocks();
        //this.handlePortal(); // Folia - region threading
        if (this.level() instanceof ServerLevel) {
            BlockPos blockPos = this.blockPosition();
            if (((ServerLevel)this.level()).getDragonFight() != null && this.level().getBlockState(blockPos).isAir()) {
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level(), blockPos, this).isCancelled()) { // Paper
                this.level().setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level(), blockPos));
                } // Paper
            }
        }

        // Paper start - Fix invulnerable end crystals
        if (!fun.bm.mili.config.modules.function.TechnicalSurvivalModeConfig.enabled && this.level().paperConfig().unsupportedSettings.fixInvulnerableEndCrystalExploit && this.generatedByDragonFight && this.isInvulnerable()) { // Mili - mc technical survival mode
            if (!java.util.Objects.equals(((ServerLevel) this.level()).uuid, this.originWorld)
                || ((ServerLevel) this.level()).getDragonFight() == null
                || ((ServerLevel) this.level()).getDragonFight().respawnStage == null
                || ((ServerLevel) this.level()).getDragonFight().respawnStage.ordinal() > net.minecraft.world.level.dimension.end.DragonRespawnAnimation.SUMMONING_DRAGON.ordinal()) {
                this.setInvulnerable(false);
                this.setBeamTarget(null);
            }
        }
        // Paper end - Fix invulnerable end crystals
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.storeNullable("beam_target", BlockPos.CODEC, this.getBeamTarget());
        output.putBoolean("ShowBottom", this.showsBottom());
        output.putBoolean("Paper.GeneratedByDragonFight", this.generatedByDragonFight); // Paper - Fix invulnerable end crystals
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setBeamTarget(input.read("beam_target", BlockPos.CODEC).orElse(null));
        this.setShowBottom(input.getBooleanOr("ShowBottom", true));
        this.generatedByDragonFight = input.getBooleanOr("Paper.GeneratedByDragonFight", false); // Paper - Fix invulnerable end crystals
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public final boolean hurtClient(DamageSource damageSource) {
        return !this.isInvulnerableToBase(damageSource) && !(damageSource.getEntity() instanceof EnderDragon);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableToBase(damageSource)) {
            return false;
        } else if (damageSource.getEntity() instanceof EnderDragon) {
            return false;
        } else {
            if (!this.isRemoved()) {
                // CraftBukkit start - All non-living entities need this
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount, false)) {
                    return false;
                }
                // CraftBukkit end
                if (!damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
                    DamageSource damageSource1 = damageSource.getEntity() != null ? this.damageSources().explosion(this, damageSource.getEntity()) : null;
                    // CraftBukkit start
                    org.bukkit.event.entity.ExplosionPrimeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callExplosionPrimeEvent(this, 6.0F, false);
                    if (event.isCancelled()) {
                        return false;
                    }

                    this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.EXPLODE); // Paper - add Bukkit remove cause
                    level.explode(this, damageSource1, null, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), Level.ExplosionInteraction.BLOCK);
                } else {
                    this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // Paper - add Bukkit remove cause
                    // CraftBukkit end
                }

                this.onDestroyedBy(level, damageSource);
            }

            return true;
        }
    }

    @Override
    public void kill(ServerLevel level) {
        this.onDestroyedBy(level, this.damageSources().generic());
        super.kill(level);
    }

    private void onDestroyedBy(ServerLevel level, DamageSource damageSource) {
        EndDragonFight dragonFight = level.getDragonFight();
        if (dragonFight != null) {
            dragonFight.onCrystalDestroyed(this, damageSource);
        }
    }

    public void setBeamTarget(@Nullable BlockPos beamTarget) {
        this.getEntityData().set(DATA_BEAM_TARGET, Optional.ofNullable(beamTarget));
    }

    public @Nullable BlockPos getBeamTarget() {
        return this.getEntityData().get(DATA_BEAM_TARGET).orElse(null);
    }

    public void setShowBottom(boolean showBottom) {
        this.getEntityData().set(DATA_SHOW_BOTTOM, showBottom);
    }

    public boolean showsBottom() {
        return this.getEntityData().get(DATA_SHOW_BOTTOM);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return super.shouldRenderAtSqrDistance(distance) || this.getBeamTarget() != null;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.END_CRYSTAL);
    }
}
