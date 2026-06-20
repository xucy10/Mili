package net.minecraft.world.entity;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class AreaEffectCloud extends Entity implements TraceableEntity {
    private static final int TIME_BETWEEN_APPLICATIONS = 5;
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_WAITING = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ParticleOptions> DATA_PARTICLE = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.PARTICLE);
    private static final float MAX_RADIUS = 32.0F;
    private static final int DEFAULT_AGE = 0;
    private static final int DEFAULT_DURATION_ON_USE = 0;
    private static final float DEFAULT_RADIUS_ON_USE = 0.0F;
    private static final float DEFAULT_RADIUS_PER_TICK = 0.0F;
    private static final float DEFAULT_POTION_DURATION_SCALE = 1.0F;
    private static final float MINIMAL_RADIUS = 0.5F;
    private static final float DEFAULT_RADIUS = 3.0F;
    public static final float DEFAULT_WIDTH = 6.0F;
    public static final float HEIGHT = 0.5F;
    public static final int INFINITE_DURATION = -1;
    public static final int DEFAULT_LINGERING_DURATION = 600;
    private static final int DEFAULT_WAIT_TIME = 20;
    private static final int DEFAULT_REAPPLICATION_DELAY = 20;
    private static final ColorParticleOption DEFAULT_PARTICLE = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, -1);
    private @Nullable ParticleOptions customParticle;
    public PotionContents potionContents = PotionContents.EMPTY;
    private float potionDurationScale = 1.0F;
    private final Map<Entity, Integer> victims = Maps.newHashMap();
    private int duration = -1;
    public int waitTime = 20;
    public int reapplicationDelay = 20;
    public int durationOnUse = 0;
    public float radiusOnUse = 0.0F;
    public float radiusPerTick = 0.0F;
    public @Nullable EntityReference<LivingEntity> owner;

    public AreaEffectCloud(EntityType<? extends AreaEffectCloud> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public AreaEffectCloud(Level level, double x, double y, double z) {
        this(EntityType.AREA_EFFECT_CLOUD, level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 3.0F);
        builder.define(DATA_WAITING, false);
        builder.define(DATA_PARTICLE, DEFAULT_PARTICLE);
    }

    public void setRadius(float radius) {
        if (!this.level().isClientSide()) {
            this.getEntityData().set(DATA_RADIUS, Mth.clamp(radius, 0.0F, 32.0F));
        }
    }

    @Override
    public void refreshDimensions() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.refreshDimensions();
        this.setPos(x, y, z);
    }

    public float getRadius() {
        return this.getEntityData().get(DATA_RADIUS);
    }

    public void setPotionContents(PotionContents potionContents) {
        this.potionContents = potionContents;
        this.updateParticle();
    }

    public void setCustomParticle(@Nullable ParticleOptions customParticle) {
        this.customParticle = customParticle;
        this.updateParticle();
    }

    public void setPotionDurationScale(float potionDurationScale) {
        this.potionDurationScale = potionDurationScale;
    }

    private void updateParticle() {
        if (this.customParticle != null) {
            this.entityData.set(DATA_PARTICLE, this.customParticle);
        } else {
            int i = ARGB.opaque(this.potionContents.getColor());
            this.entityData.set(DATA_PARTICLE, ColorParticleOption.create(DEFAULT_PARTICLE.getType(), i));
        }
    }

    public void addEffect(MobEffectInstance effectInstance) {
        this.setPotionContents(this.potionContents.withEffectAdded(effectInstance));
    }

    public ParticleOptions getParticle() {
        return this.getEntityData().get(DATA_PARTICLE);
    }

    protected void setWaiting(boolean waiting) {
        this.getEntityData().set(DATA_WAITING, waiting);
    }

    public boolean isWaiting() {
        return this.getEntityData().get(DATA_WAITING);
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    // Paper start - EAR 2
    @Override
    public void inactiveTick() {
        super.inactiveTick();
        if (this.duration != INFINITE_DURATION && this.tickCount - this.waitTime >= this.duration) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }
    }
    // Paper end - EAR 2

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.serverTick(serverLevel);
        } else {
            this.clientTick();
        }
    }

    private void clientTick() {
        boolean isWaiting = this.isWaiting();
        float radius = this.getRadius();
        if (!isWaiting || !this.random.nextBoolean()) {
            ParticleOptions particle = this.getParticle();
            int i;
            float f;
            if (isWaiting) {
                i = 2;
                f = 0.2F;
            } else {
                i = Mth.ceil((float) Math.PI * radius * radius);
                f = radius;
            }

            for (int i1 = 0; i1 < i; i1++) {
                float f1 = this.random.nextFloat() * (float) (Math.PI * 2);
                float f2 = Mth.sqrt(this.random.nextFloat()) * f;
                double d = this.getX() + Mth.cos(f1) * f2;
                double y = this.getY();
                double d1 = this.getZ() + Mth.sin(f1) * f2;
                if (particle.getType() == ParticleTypes.ENTITY_EFFECT) {
                    if (isWaiting && this.random.nextBoolean()) {
                        this.level().addAlwaysVisibleParticle(DEFAULT_PARTICLE, d, y, d1, 0.0, 0.0, 0.0);
                    } else {
                        this.level().addAlwaysVisibleParticle(particle, d, y, d1, 0.0, 0.0, 0.0);
                    }
                } else if (isWaiting) {
                    this.level().addAlwaysVisibleParticle(particle, d, y, d1, 0.0, 0.0, 0.0);
                } else {
                    this.level()
                        .addAlwaysVisibleParticle(particle, d, y, d1, (0.5 - this.random.nextDouble()) * 0.15, 0.01F, (0.5 - this.random.nextDouble()) * 0.15);
                }
            }
        }
    }

    private void serverTick(ServerLevel level) {
        if (this.duration != -1 && this.tickCount - this.waitTime >= this.duration) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            boolean isWaiting = this.isWaiting();
            boolean flag = this.tickCount < this.waitTime;
            if (isWaiting != flag) {
                this.setWaiting(flag);
            }

            if (!flag) {
                float radius = this.getRadius();
                if (this.radiusPerTick != 0.0F) {
                    radius += this.radiusPerTick;
                    if (radius < 0.5F) {
                        this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                        return;
                    }

                    this.setRadius(radius);
                }

                if (this.tickCount % 5 == 0) {
                    this.victims.entrySet().removeIf(victim -> this.tickCount >= victim.getValue());
                    if (!this.potionContents.hasEffects()) {
                        this.victims.clear();
                    } else {
                        List<MobEffectInstance> list = new ArrayList<>();
                        this.potionContents.forEachEffect(list::add, this.potionDurationScale);
                        List<LivingEntity> entitiesOfClass = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox());
                        if (!entitiesOfClass.isEmpty()) {
                            List<org.bukkit.entity.LivingEntity> entities = new java.util.ArrayList<>(); // CraftBukkit
                            for (LivingEntity livingEntity : entitiesOfClass) {
                                if (!this.victims.containsKey(livingEntity)
                                    && livingEntity.isAffectedByPotions()
                                    && !list.stream().noneMatch(livingEntity::canBeAffected)) {
                                    double d = livingEntity.getX() - this.getX();
                                    double d1 = livingEntity.getZ() - this.getZ();
                                    double d2 = d * d + d1 * d1;
                                    if (d2 <= radius * radius) {
                                        // CraftBukkit start
                                        entities.add((org.bukkit.entity.LivingEntity) livingEntity.getBukkitEntity());
                                    }
                                }
                            }
                            org.bukkit.event.entity.AreaEffectCloudApplyEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callAreaEffectCloudApplyEvent(this, entities);
                            if (!event.isCancelled()) {
                                for (org.bukkit.entity.LivingEntity entity : event.getAffectedEntities()) {
                                    if (entity instanceof org.bukkit.craftbukkit.entity.CraftLivingEntity) {
                                        net.minecraft.world.entity.LivingEntity livingEntity = ((org.bukkit.craftbukkit.entity.CraftLivingEntity) entity).getHandle();
                                        // CraftBukkit end
                                        this.victims.put(livingEntity, this.tickCount + this.reapplicationDelay);

                                        for (MobEffectInstance mobEffectInstance : list) {
                                            if (mobEffectInstance.getEffect().value().isInstantenous()) {
                                                mobEffectInstance.getEffect()
                                                    .value()
                                                    .applyInstantenousEffect(level, this, this.getOwner(), livingEntity, mobEffectInstance.getAmplifier(), 0.5);
                                            } else {
                                                livingEntity.addEffect(new MobEffectInstance(mobEffectInstance), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD); // CraftBukkit
                                            }
                                        }

                                        if (this.radiusOnUse != 0.0F) {
                                            radius += this.radiusOnUse;
                                            if (radius < 0.5F) {
                                                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                                                return;
                                            }

                                            this.setRadius(radius);
                                        }

                                        if (this.durationOnUse != 0 && this.duration != -1) {
                                            this.duration = this.duration + this.durationOnUse;
                                            if (this.duration <= 0) {
                                                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public float getRadiusOnUse() {
        return this.radiusOnUse;
    }

    public void setRadiusOnUse(float radiusOnUse) {
        this.radiusOnUse = radiusOnUse;
    }

    public float getRadiusPerTick() {
        return this.radiusPerTick;
    }

    public void setRadiusPerTick(float radiusPerTick) {
        this.radiusPerTick = radiusPerTick;
    }

    public int getDurationOnUse() {
        return this.durationOnUse;
    }

    public void setDurationOnUse(int durationOnUse) {
        this.durationOnUse = durationOnUse;
    }

    public int getWaitTime() {
        return this.waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.owner = EntityReference.of(owner);
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        return EntityReference.getLivingEntity(this.owner, this.level());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.tickCount = input.getIntOr("Age", 0);
        this.duration = input.getIntOr("Duration", -1);
        this.waitTime = input.getIntOr("WaitTime", 20);
        this.reapplicationDelay = input.getIntOr("ReapplicationDelay", 20);
        this.durationOnUse = input.getIntOr("DurationOnUse", 0);
        this.radiusOnUse = input.getFloatOr("RadiusOnUse", 0.0F);
        this.radiusPerTick = input.getFloatOr("RadiusPerTick", 0.0F);
        this.setRadius(input.getFloatOr("Radius", 3.0F));
        this.owner = EntityReference.read(input, "Owner");
        this.setCustomParticle(input.read("custom_particle", ParticleTypes.CODEC).orElse(null));
        this.setPotionContents(input.read("potion_contents", PotionContents.CODEC).orElse(PotionContents.EMPTY));
        this.potionDurationScale = input.getFloatOr("potion_duration_scale", 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Age", this.tickCount);
        output.putInt("Duration", this.duration);
        output.putInt("WaitTime", this.waitTime);
        output.putInt("ReapplicationDelay", this.reapplicationDelay);
        output.putInt("DurationOnUse", this.durationOnUse);
        output.putFloat("RadiusOnUse", this.radiusOnUse);
        output.putFloat("RadiusPerTick", this.radiusPerTick);
        output.putFloat("Radius", this.getRadius());
        output.storeNullable("custom_particle", ParticleTypes.CODEC, this.customParticle);
        EntityReference.store(this.owner, output, "Owner");
        if (!this.potionContents.equals(PotionContents.EMPTY)) {
            output.store("potion_contents", PotionContents.CODEC, this.potionContents);
        }

        if (this.potionDurationScale != 1.0F) {
            output.putFloat("potion_duration_scale", this.potionDurationScale);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_RADIUS.equals(key)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(this.getRadius() * 2.0F, 0.5F);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        if (component == DataComponents.POTION_CONTENTS) {
            return castComponentValue((DataComponentType<T>)component, this.potionContents);
        } else {
            return component == DataComponents.POTION_DURATION_SCALE
                ? castComponentValue((DataComponentType<T>)component, this.potionDurationScale)
                : super.get(component);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.POTION_CONTENTS);
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.POTION_DURATION_SCALE);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.POTION_CONTENTS) {
            this.setPotionContents(castComponentValue(DataComponents.POTION_CONTENTS, value));
            return true;
        } else if (component == DataComponents.POTION_DURATION_SCALE) {
            this.setPotionDurationScale(castComponentValue(DataComponents.POTION_DURATION_SCALE, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    // KioCG start
    @Override
    public boolean shouldTickHot() {
        return false;
    }
    // KioCG end
}
