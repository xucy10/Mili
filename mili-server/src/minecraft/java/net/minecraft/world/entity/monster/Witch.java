package net.minecraft.world.entity.monster;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableWitchTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestHealableRaiderTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class Witch extends Raider implements RangedAttackMob {
    private static final Identifier SPEED_MODIFIER_DRINKING_ID = Identifier.withDefaultNamespace("drinking");
    private static final AttributeModifier SPEED_MODIFIER_DRINKING = new AttributeModifier(
        SPEED_MODIFIER_DRINKING_ID, -0.25, AttributeModifier.Operation.ADD_VALUE
    );
    private static final EntityDataAccessor<Boolean> DATA_USING_ITEM = SynchedEntityData.defineId(Witch.class, EntityDataSerializers.BOOLEAN);
    public int usingTime;
    private NearestHealableRaiderTargetGoal<Raider> healRaidersGoal;
    private NearestAttackableWitchTargetGoal<Player> attackPlayersGoal;

    public Witch(EntityType<? extends Witch> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.healRaidersGoal = new NearestHealableRaiderTargetGoal<>(
            this, Raider.class, true, (entity, level) -> this.hasActiveRaid() && entity.getType() != EntityType.WITCH
        );
        this.attackPlayersGoal = new NearestAttackableWitchTargetGoal<>(this, Player.class, 10, true, false, null);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 60, 10.0F));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class));
        this.targetSelector.addGoal(2, this.healRaidersGoal);
        this.targetSelector.addGoal(3, this.attackPlayersGoal);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_USING_ITEM, false);
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.WITCH_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WITCH_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WITCH_DEATH;
    }

    public void setUsingItem(boolean usingItem) {
        this.getEntityData().set(DATA_USING_ITEM, usingItem);
    }

    public boolean isDrinkingPotion() {
        return this.getEntityData().get(DATA_USING_ITEM);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide() && this.isAlive()) {
            this.healRaidersGoal.decrementCooldown();
            if (this.healRaidersGoal.getCooldown() <= 0) {
                this.attackPlayersGoal.setCanAttack(true);
            } else {
                this.attackPlayersGoal.setCanAttack(false);
            }

            if (this.isDrinkingPotion()) {
                if (this.usingTime-- <= 0) {
                    this.setUsingItem(false);
                    ItemStack mainHandItem = this.getMainHandItem();
                    this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    PotionContents potionContents = mainHandItem.get(DataComponents.POTION_CONTENTS);
                    // Paper start - WitchConsumePotionEvent
                    if (mainHandItem.is(Items.POTION)) {
                        com.destroystokyo.paper.event.entity.WitchConsumePotionEvent event = new com.destroystokyo.paper.event.entity.WitchConsumePotionEvent((org.bukkit.entity.Witch) this.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(mainHandItem));
                        potionContents = event.callEvent() ? org.bukkit.craftbukkit.inventory.CraftItemStack.unwrap(event.getPotion()).get(DataComponents.POTION_CONTENTS) : null;
                    }
                    // Paper end - WitchConsumePotionEvent
                    if (mainHandItem.is(Items.POTION) && potionContents != null) {
                        potionContents.forEachEffect(effect -> this.addEffect(effect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK), mainHandItem.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F)); // CraftBukkit
                    }

                    this.gameEvent(GameEvent.DRINK);
                    this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_MODIFIER_DRINKING.id());
                }
            } else {
                Holder<Potion> holder = null;
                if (this.random.nextFloat() < 0.15F && this.isEyeInFluid(FluidTags.WATER) && !this.hasEffect(MobEffects.WATER_BREATHING)) {
                    holder = Potions.WATER_BREATHING;
                } else if (this.random.nextFloat() < 0.15F
                    && (this.isOnFire() || this.getLastDamageSource() != null && this.getLastDamageSource().is(DamageTypeTags.IS_FIRE))
                    && !this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                    holder = Potions.FIRE_RESISTANCE;
                } else if (this.random.nextFloat() < 0.05F && this.getHealth() < this.getMaxHealth()) {
                    holder = Potions.HEALING;
                } else if (this.random.nextFloat() < 0.5F
                    && this.getTarget() != null
                    && !this.hasEffect(MobEffects.SPEED)
                    && this.getTarget().distanceToSqr(this) > 121.0) {
                    holder = Potions.SWIFTNESS;
                }

                if (holder != null) {
                    this.setDrinkingPotion(PotionContents.createItemStack(Items.POTION, holder)); // Paper - logic moved into setDrinkingPotion, copy exact impl into the method and then comment out
                }
            }

            if (this.random.nextFloat() < 7.5E-4F) {
                this.level().broadcastEntityEvent(this, EntityEvent.WITCH_HAT_MAGIC);
            }
        }

        super.aiStep();
    }

    // Paper start - moved to its own method
    public void setDrinkingPotion(ItemStack potion) {
        potion = org.bukkit.craftbukkit.event.CraftEventFactory.handleWitchReadyPotionEvent(this, potion);
        this.setItemSlot(EquipmentSlot.MAINHAND, potion);
        this.usingTime = this.getMainHandItem().getUseDuration(this);
        this.setUsingItem(true);
        if (!this.isSilent()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_DRINK, this.getSoundSource(), 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
        }

        AttributeInstance attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);

        attribute.removeModifier(Witch.SPEED_MODIFIER_DRINKING_ID);
        attribute.addTransientModifier(Witch.SPEED_MODIFIER_DRINKING);
    }
    // Paper end

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.WITCH_CELEBRATE;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.WITCH_HAT_MAGIC) {
            for (int i = 0; i < this.random.nextInt(35) + 10; i++) {
                this.level()
                    .addParticle(
                        ParticleTypes.WITCH,
                        this.getX() + this.random.nextGaussian() * 0.13F,
                        this.getBoundingBox().maxY + 0.5 + this.random.nextGaussian() * 0.13F,
                        this.getZ() + this.random.nextGaussian() * 0.13F,
                        0.0,
                        0.0,
                        0.0
                    );
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource damageSource, float damageAmount) {
        damageAmount = super.getDamageAfterMagicAbsorb(damageSource, damageAmount);
        if (damageSource.getEntity() == this) {
            damageAmount = 0.0F;
        }

        if (damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            damageAmount *= 0.15F;
        }

        return damageAmount;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (!this.isDrinkingPotion()) {
            Vec3 deltaMovement = target.getDeltaMovement();
            double d = target.getX() + deltaMovement.x - this.getX();
            double d1 = target.getEyeY() - 1.1F - this.getY();
            double d2 = target.getZ() + deltaMovement.z - this.getZ();
            double squareRoot = Math.sqrt(d * d + d2 * d2);
            Holder<Potion> holder = Potions.HARMING;
            if (target instanceof Raider) {
                if (target.getHealth() <= 4.0F) {
                    holder = Potions.HEALING;
                } else {
                    holder = Potions.REGENERATION;
                }

                this.setTarget(null);
            } else if (squareRoot >= 8.0 && !target.hasEffect(MobEffects.SLOWNESS)) {
                holder = Potions.SLOWNESS;
            } else if (target.getHealth() >= 8.0F && !target.hasEffect(MobEffects.POISON)) {
                holder = Potions.POISON;
            } else if (squareRoot <= 3.0 && !target.hasEffect(MobEffects.WEAKNESS) && this.random.nextFloat() < 0.25F) {
                holder = Potions.WEAKNESS;
            }

            if (this.level() instanceof ServerLevel serverLevel) {
                ItemStack itemStack = PotionContents.createItemStack(Items.SPLASH_POTION, holder);
                // Paper start - WitchThrowPotionEvent
                com.destroystokyo.paper.event.entity.WitchThrowPotionEvent event = new com.destroystokyo.paper.event.entity.WitchThrowPotionEvent((org.bukkit.entity.Witch) this.getBukkitEntity(), (org.bukkit.entity.LivingEntity) target.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack));
                if (!event.callEvent()) {
                    return;
                }
                itemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getPotion());
                // Paper end - WitchThrowPotionEven
                Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, serverLevel, itemStack, this, d, d1 + squareRoot * 0.2, d2, 0.75F, 8.0F);
            }

            if (!this.isSilent()) {
                this.level()
                    .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.WITCH_THROW,
                        this.getSoundSource(),
                        1.0F,
                        0.8F + this.random.nextFloat() * 0.4F
                    );
            }
        }
    }

    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean flag) {
    }

    @Override
    public boolean canBeLeader() {
        return false;
    }
}
