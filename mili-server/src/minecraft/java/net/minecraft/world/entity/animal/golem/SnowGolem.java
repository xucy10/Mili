package net.minecraft.world.entity.animal.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SnowGolem extends AbstractGolem implements Shearable, RangedAttackMob {
    private static final EntityDataAccessor<Byte> DATA_PUMPKIN_ID = SynchedEntityData.defineId(SnowGolem.class, EntityDataSerializers.BYTE);
    private static final byte PUMPKIN_FLAG = 16;
    private static final boolean DEFAULT_PUMPKIN = true;

    public SnowGolem(EntityType<? extends SnowGolem> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25, 20, 10.0F));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0, 1.0000001E-5F));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (entity, level) -> entity instanceof Enemy));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.2F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PUMPKIN_ID, (byte)16);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Pumpkin", this.hasPumpkin());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setPumpkin(input.getBooleanOr("Pumpkin", true));
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.environmentAttributes().getValue(EnvironmentAttributes.SNOW_GOLEM_MELTS, this.position())) {
                this.hurtServer(serverLevel, this.damageSources().onFire().knownCause(org.bukkit.event.entity.EntityDamageEvent.DamageCause.MELTING), 1.0F); // CraftBukkit
            }

            if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
                return;
            }

            BlockState blockState = Blocks.SNOW.defaultBlockState();

            for (int i = 0; i < 4; i++) {
                int floor = Mth.floor(this.getX() + (i % 2 * 2 - 1) * 0.25F);
                int floor1 = Mth.floor(this.getY());
                int floor2 = Mth.floor(this.getZ() + (i / 2 % 2 * 2 - 1) * 0.25F);
                BlockPos blockPos = new BlockPos(floor, floor1, floor2);
                if (this.level().getBlockState(blockPos).isAir() && blockState.canSurvive(this.level(), blockPos)) {
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this.level(), blockPos, blockState, net.minecraft.world.level.block.Block.UPDATE_ALL, this)) continue; // CraftBukkit
                    this.level().gameEvent(GameEvent.BLOCK_PLACE, blockPos, GameEvent.Context.of(this, blockState));
                }
            }
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        double d = target.getX() - this.getX();
        double d1 = target.getEyeY() - 1.1F;
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d * d + d2 * d2) * 0.2F;
        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack itemStack = new ItemStack(Items.SNOWBALL);
            Projectile.spawnProjectile(
                new Snowball(serverLevel, this, itemStack), serverLevel, itemStack, snowball -> snowball.shoot(d, d1 + d3 - snowball.getY(), d2, 1.6F, 12.0F)
            );
        }

        this.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 0.4F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.SHEARS) && this.readyForShearing()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                // CraftBukkit start
                // Paper start - custom shear drops
                java.util.List<ItemStack> drops = this.generateDefaultDrops(serverLevel, itemInHand);
                org.bukkit.event.player.PlayerShearEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerShearEntityEvent(player, this, itemInHand, hand, drops);
                if (event != null) {
                    if (event.isCancelled()) {
                        return InteractionResult.PASS;
                    }
                    drops = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getDrops());
                    // Paper end - custom shear drops
                }
                // CraftBukkit end
                this.shear(serverLevel, SoundSource.PLAYERS, itemInHand, drops); // Paper - custom shear drops
                this.gameEvent(GameEvent.SHEAR, player);
                itemInHand.hurtAndBreak(1, player, hand.asEquipmentSlot());
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears) {
        // Paper start - custom shear drops
        this.shear(level, source, shears, this.generateDefaultDrops(level, shears));
    }

    @Override
    public java.util.List<ItemStack> generateDefaultDrops(final ServerLevel level, final ItemStack shears) {
        final java.util.List<ItemStack> drops = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        this.dropFromShearingLootTable(level, BuiltInLootTables.SHEAR_SNOW_GOLEM, shears, (ignored, stack) -> {
            drops.add(stack);
        });
        return drops;
    }

    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears, java.util.List<ItemStack> drops) {
        // Paper end - custom shear drops
        level.playSound(null, this, SoundEvents.SNOW_GOLEM_SHEAR, source, 1.0F, 1.0F);
        this.setPumpkin(false);
        drops.forEach(itemStack -> { // Paper - custom shear drops
            this.forceDrops = true; // CraftBukkit
            this.spawnAtLocation(level, itemStack, this.getEyeHeight());
            this.forceDrops = false; // CraftBukkit
        });
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && this.hasPumpkin();
    }

    public boolean hasPumpkin() {
        return (this.entityData.get(DATA_PUMPKIN_ID) & 16) != 0;
    }

    public void setPumpkin(boolean pumpkinEquipped) {
        byte b = this.entityData.get(DATA_PUMPKIN_ID);
        if (pumpkinEquipped) {
            this.entityData.set(DATA_PUMPKIN_ID, (byte)(b | 16));
        } else {
            this.entityData.set(DATA_PUMPKIN_ID, (byte)(b & -17));
        }
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.SNOW_GOLEM_AMBIENT;
    }

    @Override
    public @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SNOW_GOLEM_HURT;
    }

    @Override
    public @Nullable SoundEvent getDeathSound() {
        return SoundEvents.SNOW_GOLEM_DEATH;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.75F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }
}
