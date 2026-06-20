package net.minecraft.world.entity;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class OminousItemSpawner extends Entity {
    private static final int SPAWN_ITEM_DELAY_MIN = 60;
    private static final int SPAWN_ITEM_DELAY_MAX = 120;
    private static final String TAG_SPAWN_ITEM_AFTER_TICKS = "spawn_item_after_ticks";
    private static final String TAG_ITEM = "item";
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(OminousItemSpawner.class, EntityDataSerializers.ITEM_STACK);
    public static final int TICKS_BEFORE_ABOUT_TO_SPAWN_SOUND = 36;
    public long spawnItemAfterTicks;

    public OminousItemSpawner(EntityType<? extends OminousItemSpawner> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static OminousItemSpawner create(Level level, ItemStack item) {
        OminousItemSpawner ominousItemSpawner = new OminousItemSpawner(EntityType.OMINOUS_ITEM_SPAWNER, level);
        ominousItemSpawner.spawnItemAfterTicks = level.random.nextIntBetweenInclusive(60, 120);
        ominousItemSpawner.setItem(item);
        return ominousItemSpawner;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.tickServer(serverLevel);
        } else {
            this.tickClient();
        }
    }

    private void tickServer(ServerLevel level) {
        if (this.tickCount == this.spawnItemAfterTicks - 36L) {
            level.playSound(null, this.blockPosition(), SoundEvents.TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, SoundSource.NEUTRAL);
        }

        if (this.tickCount >= this.spawnItemAfterTicks) {
            this.spawnItem();
            this.kill(level);
        }
    }

    private void tickClient() {
        if (this.level().getGameTime() % 5L == 0L) {
            this.addParticles();
        }
    }

    private void spawnItem() {
        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack item = this.getItem();
            if (!item.isEmpty()) {
                Entity entity;
                if (item.getItem() instanceof ProjectileItem projectileItem) {
                    entity = this.spawnProjectile(serverLevel, projectileItem, item);
                } else {
                    entity = new ItemEntity(serverLevel, this.getX(), this.getY(), this.getZ(), item);
                    serverLevel.addFreshEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.OMINOUS_ITEM_SPAWNER); // Paper - fixes and addition to spawn reason API
                }

                serverLevel.levelEvent(LevelEvent.PARTICLES_TRIAL_SPAWNER_SPAWN_ITEM, this.blockPosition(), 1);
                serverLevel.gameEvent(entity, GameEvent.ENTITY_PLACE, this.position());
                this.setItem(ItemStack.EMPTY);
            }
        }
    }

    private Entity spawnProjectile(ServerLevel level, ProjectileItem projectileItem, ItemStack stack) {
        ProjectileItem.DispenseConfig dispenseConfig = projectileItem.createDispenseConfig();
        dispenseConfig.overrideDispenseEvent().ifPresent(i -> level.levelEvent(i, this.blockPosition(), 0));
        Direction direction = Direction.DOWN;
        Projectile projectile = Projectile.spawnProjectileUsingShootDelayed( // Paper - fixes and addition to spawn reason API
            projectileItem.asProjectile(level, this.position(), stack, direction),
            level,
            stack,
            direction.getStepX(),
            direction.getStepY(),
            direction.getStepZ(),
            dispenseConfig.power(),
            dispenseConfig.uncertainty()
        ).spawn(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.OMINOUS_ITEM_SPAWNER); // Paper - fixes and addition to spawn reason API
        projectile.setOwner(this);
        return projectile;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setItem(input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        this.spawnItemAfterTicks = input.getLongOr("spawn_item_after_ticks", 0L);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (!this.getItem().isEmpty()) {
            output.store("item", ItemStack.CODEC, this.getItem());
        }

        output.putLong("spawn_item_after_ticks", this.spawnItemAfterTicks);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    protected boolean couldAcceptPassenger() {
        return false;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        throw new IllegalStateException("Should never addPassenger without checking couldAcceptPassenger()");
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    public void addParticles() {
        Vec3 vec3 = this.position();
        int randomInt = this.random.nextIntBetweenInclusive(1, 3);

        for (int i = 0; i < randomInt; i++) {
            double d = 0.4;
            Vec3 vec31 = new Vec3(
                this.getX() + 0.4 * (this.random.nextGaussian() - this.random.nextGaussian()),
                this.getY() + 0.4 * (this.random.nextGaussian() - this.random.nextGaussian()),
                this.getZ() + 0.4 * (this.random.nextGaussian() - this.random.nextGaussian())
            );
            Vec3 vec32 = vec3.vectorTo(vec31);
            this.level().addParticle(ParticleTypes.OMINOUS_SPAWNING, vec3.x(), vec3.y(), vec3.z(), vec32.x(), vec32.y(), vec32.z());
        }
    }

    public ItemStack getItem() {
        return this.getEntityData().get(DATA_ITEM);
    }

    public void setItem(ItemStack item) {
        this.getEntityData().set(DATA_ITEM, item);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }
}
