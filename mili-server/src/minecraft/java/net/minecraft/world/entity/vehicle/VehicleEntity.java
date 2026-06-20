package net.minecraft.world.entity.vehicle;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;

public abstract class VehicleEntity extends Entity {
    protected static final EntityDataAccessor<Integer> DATA_ID_HURT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_ID_HURTDIR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> DATA_ID_DAMAGE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);

    public VehicleEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean hurtClient(DamageSource damageSource) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isRemoved()) {
            return true;
        } else if (this.isInvulnerableToBase(damageSource)) {
            return false;
        } else {
            // CraftBukkit start
            org.bukkit.entity.Vehicle vehicle = (org.bukkit.entity.Vehicle) this.getBukkitEntity();
            org.bukkit.entity.Entity attacker = (damageSource.getEntity() == null) ? null : damageSource.getEntity().getBukkitEntity();

            org.bukkit.event.vehicle.VehicleDamageEvent event = new org.bukkit.event.vehicle.VehicleDamageEvent(vehicle, attacker, amount);
            if (!event.callEvent()) return false;
            amount = (float) event.getDamage();
            // CraftBukkit end
            this.setHurtDir(-this.getHurtDir());
            this.setHurtTime(10);
            this.markHurt();
            this.setDamage(this.getDamage() + amount * 10.0F);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getEntity());
            boolean flag = damageSource.getEntity() instanceof Player player && player.getAbilities().instabuild;
            if ((flag || !(this.getDamage() > 40.0F)) && !this.shouldSourceDestroy(damageSource)) {
                if (flag) {
                    // CraftBukkit start
                    org.bukkit.event.vehicle.VehicleDestroyEvent destroyEvent = new org.bukkit.event.vehicle.VehicleDestroyEvent(vehicle, attacker);
                    if (!destroyEvent.callEvent()) {
                        this.setDamage(40.0F); // Maximize damage so this doesn't get triggered again right away
                        return true;
                    }
                    // CraftBukkit end
                    this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
                }
            } else {
                // CraftBukkit start
                org.bukkit.event.vehicle.VehicleDestroyEvent destroyEvent = new org.bukkit.event.vehicle.VehicleDestroyEvent(vehicle, attacker);
                if (!destroyEvent.callEvent()) {
                    this.setDamage(40.0F); // Maximize damage so this doesn't get triggered again right away
                    return true;
                }
                // CraftBukkit end
                this.destroy(level, damageSource);
            }

            return true;
        }
    }

    protected boolean shouldSourceDestroy(DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return explosion.getIndirectSourceEntity() instanceof Mob && !explosion.level().getGameRules().get(GameRules.MOB_GRIEFING);
    }

    public void destroy(ServerLevel level, Item dropItem) {
        this.kill(level);
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ItemStack itemStack = new ItemStack(dropItem);
            itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            this.spawnAtLocation(level, itemStack);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ID_HURT, 0);
        builder.define(DATA_ID_HURTDIR, 1);
        builder.define(DATA_ID_DAMAGE, 0.0F);
    }

    public void setHurtTime(int hurtTime) {
        this.entityData.set(DATA_ID_HURT, hurtTime);
    }

    public void setHurtDir(int hurtDir) {
        this.entityData.set(DATA_ID_HURTDIR, hurtDir);
    }

    public void setDamage(float damage) {
        this.entityData.set(DATA_ID_DAMAGE, damage);
    }

    public float getDamage() {
        return this.entityData.get(DATA_ID_DAMAGE);
    }

    public int getHurtTime() {
        return this.entityData.get(DATA_ID_HURT);
    }

    public int getHurtDir() {
        return this.entityData.get(DATA_ID_HURTDIR);
    }

    protected void destroy(ServerLevel level, DamageSource damageSource) {
        this.destroy(level, this.getDropItem());
    }

    @Override
    public int getDimensionChangingDelay() {
        return 10;
    }

    public abstract Item getDropItem();
}
