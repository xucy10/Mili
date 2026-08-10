package net.minecraft.world.entity.animal.sheep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class Sheep extends Animal implements Shearable {
    private static final int EAT_ANIMATION_TICKS = 40;
    private static final EntityDataAccessor<Byte> DATA_WOOL_ID = SynchedEntityData.defineId(Sheep.class, EntityDataSerializers.BYTE);
    private static final DyeColor DEFAULT_COLOR = DyeColor.WHITE;
    private static final boolean DEFAULT_SHEARED = false;
    private int eatAnimationTick;
    private EatBlockGoal eatBlockGoal;

    public Sheep(EntityType<? extends Sheep> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.eatBlockGoal = new EatBlockGoal(this);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, stack -> stack.is(ItemTags.SHEEP_FOOD), false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        this.goalSelector.addGoal(5, this.eatBlockGoal);
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.SHEEP_FOOD);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.eatAnimationTick = this.eatBlockGoal.getEatAnimationTick();
        super.customServerAiStep(level);
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide()) {
            this.eatAnimationTick = Math.max(0, this.eatAnimationTick - 1);
        }

        super.aiStep();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.23F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WOOL_ID, (byte)0);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.EAT_GRASS) {
            this.eatAnimationTick = 40;
        } else {
            super.handleEntityEvent(id);
        }
    }

    public float getHeadEatPositionScale(float partialTick) {
        if (this.eatAnimationTick <= 0) {
            return 0.0F;
        } else if (this.eatAnimationTick >= 4 && this.eatAnimationTick <= 36) {
            return 1.0F;
        } else {
            return this.eatAnimationTick < 4 ? (this.eatAnimationTick - partialTick) / 4.0F : -(this.eatAnimationTick - 40 - partialTick) / 4.0F;
        }
    }

    public float getHeadEatAngleScale(float partialTick) {
        if (this.eatAnimationTick > 4 && this.eatAnimationTick <= 36) {
            float f = (this.eatAnimationTick - 4 - partialTick) / 32.0F;
            return (float) (Math.PI / 5) + 0.21991149F * Mth.sin(f * 28.7F);
        } else {
            return this.eatAnimationTick > 0 ? (float) (Math.PI / 5) : this.getXRot(partialTick) * (float) (Math.PI / 180.0);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.SHEARS)) {
            if (this.level() instanceof ServerLevel serverLevel && this.readyForShearing()) {
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
                return InteractionResult.SUCCESS_SERVER;
            } else {
                return InteractionResult.CONSUME;
            }
        } else {
            return super.mobInteract(player, hand);
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
        this.dropFromShearingLootTable(level, BuiltInLootTables.SHEAR_SHEEP, shears, (ignored, stack) -> {
            for (int i = 0; i < stack.getCount(); ++i) drops.add(stack.copyWithCount(1));
        });
        return drops;
    }

    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears, java.util.List<ItemStack> drops) {
        // Paper end - custom shear drops
        level.playSound(null, this, SoundEvents.SHEEP_SHEAR, source, 1.0F, 1.0F);
        drops.forEach(itemStack -> { // Paper - custom drops - loop in generated default drops
            { // Paper - custom drops - loop in generated default drops
                this.forceDrops = true; // CraftBukkit
                ItemEntity itemEntity = this.spawnAtLocation(level, itemStack, 1.0F); // Paper - custom drops - copy already done above
                this.forceDrops = false; // CraftBukkit
                    if (itemEntity != null) {
                        itemEntity.setDeltaMovement(
                            itemEntity.getDeltaMovement()
                                .add(
                                    (this.random.nextFloat() - this.random.nextFloat()) * 0.1F,
                                    this.random.nextFloat() * 0.05F,
                                    (this.random.nextFloat() - this.random.nextFloat()) * 0.1F
                                )
                        );
                    }
                }
            }
        );
        this.setSheared(true);
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isSheared() && !this.isBaby();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Sheared", this.isSheared());
        output.store("Color", DyeColor.LEGACY_ID_CODEC, this.getColor());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setSheared(input.getBooleanOr("Sheared", false));
        this.setColor(input.read("Color", DyeColor.LEGACY_ID_CODEC).orElse(DEFAULT_COLOR));
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.SHEEP_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SHEEP_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SHEEP_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SHEEP_STEP, 0.15F, 1.0F);
    }

    public DyeColor getColor() {
        return DyeColor.byId(this.entityData.get(DATA_WOOL_ID) & 15);
    }

    public void setColor(DyeColor color) {
        byte b = this.entityData.get(DATA_WOOL_ID);
        this.entityData.set(DATA_WOOL_ID, (byte)(b & 240 | color.getId() & 15));
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.SHEEP_COLOR ? castComponentValue((DataComponentType<T>)component, this.getColor()) : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.SHEEP_COLOR);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.SHEEP_COLOR) {
            this.setColor(castComponentValue(DataComponents.SHEEP_COLOR, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    public boolean isSheared() {
        return (this.entityData.get(DATA_WOOL_ID) & 16) != 0;
    }

    public void setSheared(boolean sheared) {
        byte b = this.entityData.get(DATA_WOOL_ID);
        if (sheared) {
            this.entityData.set(DATA_WOOL_ID, (byte)(b | 16));
        } else {
            this.entityData.set(DATA_WOOL_ID, (byte)(b & -17));
        }
    }

    public static DyeColor getRandomSheepColor(ServerLevelAccessor level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return SheepColorSpawnRules.getSheepColor(biome, level.getRandom());
    }

    @Override
    public @Nullable Sheep getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Sheep sheep = EntityType.SHEEP.create(level, EntitySpawnReason.BREEDING);
        if (sheep != null) {
            DyeColor color = this.getColor();
            DyeColor color1 = ((Sheep)partner).getColor();
            sheep.setColor(DyeColor.getMixedColor(level, color, color1));
        }

        return sheep;
    }

    @Override
    public void ate() {
        if (!new org.bukkit.event.entity.SheepRegrowWoolEvent((org.bukkit.entity.Sheep) this.getBukkitEntity()).callEvent()) return; // CraftBukkit
        super.ate();
        this.setSheared(false);
        if (this.isBaby()) {
            this.ageUp(60);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        this.setColor(getRandomSheepColor(level, this.blockPosition()));
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }
}
