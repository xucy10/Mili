package net.minecraft.world.entity.monster.zombie;

import com.google.common.annotations.VisibleForTesting;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class ZombieVillager extends Zombie implements VillagerDataHolder {
    public static final EntityDataAccessor<Boolean> DATA_CONVERTING_ID = SynchedEntityData.defineId(ZombieVillager.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(
        ZombieVillager.class, EntityDataSerializers.VILLAGER_DATA
    );
    private static final int VILLAGER_CONVERSION_WAIT_MIN = 3600;
    private static final int VILLAGER_CONVERSION_WAIT_MAX = 6000;
    private static final int MAX_SPECIAL_BLOCKS_COUNT = 14;
    private static final int SPECIAL_BLOCK_RADIUS = 4;
    private static final int NOT_CONVERTING = -1;
    private static final int DEFAULT_XP = 0;
    private static final Set<EntitySpawnReason> REASONS_NOT_TO_SET_TYPE = EnumSet.of(
        EntitySpawnReason.LOAD,
        EntitySpawnReason.DIMENSION_TRAVEL,
        EntitySpawnReason.CONVERSION,
        EntitySpawnReason.SPAWN_ITEM_USE,
        EntitySpawnReason.SPAWNER,
        EntitySpawnReason.TRIAL_SPAWNER
    );
    public int villagerConversionTime;
    public @Nullable UUID conversionStarter;
    private @Nullable GossipContainer gossips;
    private @Nullable MerchantOffers tradeOffers;
    private int villagerXp = 0;

    public ZombieVillager(EntityType<? extends ZombieVillager> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CONVERTING_ID, false);
        builder.define(DATA_VILLAGER_DATA, this.initializeVillagerData());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("VillagerData", VillagerData.CODEC, this.getVillagerData());
        output.storeNullable("Offers", MerchantOffers.CODEC, this.tradeOffers);
        output.storeNullable("Gossips", GossipContainer.CODEC, this.gossips);
        output.putInt("ConversionTime", this.isConverting() ? this.villagerConversionTime : -1);
        output.storeNullable("ConversionPlayer", UUIDUtil.CODEC, this.conversionStarter);
        output.putInt("Xp", this.villagerXp);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_VILLAGER_DATA, input.read("VillagerData", VillagerData.CODEC).orElseGet(this::initializeVillagerData));
        this.tradeOffers = input.read("Offers", MerchantOffers.CODEC).orElse(null);
        this.gossips = input.read("Gossips", GossipContainer.CODEC).orElse(null);
        int intOr = input.getIntOr("ConversionTime", -1);
        if (intOr != -1) {
            UUID uuid = input.read("ConversionPlayer", UUIDUtil.CODEC).orElse(null);
            this.startConverting(uuid, intOr);
        } else {
            this.getEntityData().set(DATA_CONVERTING_ID, false);
            this.villagerConversionTime = -1;
        }

        this.villagerXp = input.getIntOr("Xp", 0);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        if (!REASONS_NOT_TO_SET_TYPE.contains(spawnReason)) {
            this.setVillagerData(this.getVillagerData().withType(level.registryAccess(), VillagerType.byBiome(level.getBiome(this.blockPosition()))));
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    private VillagerData initializeVillagerData() {
        Optional<Holder.Reference<VillagerProfession>> random = BuiltInRegistries.VILLAGER_PROFESSION.getRandom(this.random);
        VillagerData villagerData = Villager.createDefaultVillagerData();
        if (random.isPresent()) {
            villagerData = villagerData.withProfession(random.get());
        }

        return villagerData;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.isAlive() && this.isConverting()) {
            int conversionProgress = this.getConversionProgress();
            this.villagerConversionTime -= conversionProgress;
            if (this.villagerConversionTime <= 0) {
                this.finishConversion((ServerLevel)this.level());
            }
        }

        super.tick();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.GOLDEN_APPLE)) {
            if (this.hasEffect(MobEffects.WEAKNESS)) {
                itemInHand.consume(1, player);
                if (!this.level().isClientSide()) {
                    this.startConverting(player.getUUID(), this.random.nextInt(2401) + 3600);
                }

                return InteractionResult.SUCCESS_SERVER;
            } else {
                return InteractionResult.CONSUME;
            }
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.isConverting() && this.villagerXp == 0;
    }

    public boolean isConverting() {
        return this.getEntityData().get(DATA_CONVERTING_ID);
    }

    public void startConverting(@Nullable UUID conversionStarter, int villagerConversionTime) {
        // Paper start - missing entity behaviour api - converting without entity event
        this.startConverting(conversionStarter, villagerConversionTime, true);
    }

    public void startConverting(@Nullable UUID conversionStarter, int villagerConversionTime, boolean broadcastEntityEvent) {
        // Paper end - missing entity behaviour api - converting without entity event
        this.conversionStarter = conversionStarter;
        this.villagerConversionTime = villagerConversionTime;
        this.getEntityData().set(DATA_CONVERTING_ID, true);
        // CraftBukkit start
        this.removeEffect(MobEffects.WEAKNESS, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, villagerConversionTime, Math.min(this.level().getDifficulty().getId() - 1, 0)), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        // CraftBukkit end
        if (broadcastEntityEvent) this.level().broadcastEntityEvent(this, EntityEvent.ZOMBIE_CONVERTING); // Paper - missing entity behaviour api - converting without entity event
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.ZOMBIE_CONVERTING) {
            if (!this.isSilent()) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getEyeY(),
                        this.getZ(),
                        SoundEvents.ZOMBIE_VILLAGER_CURE,
                        this.getSoundSource(),
                        1.0F + this.random.nextFloat(),
                        this.random.nextFloat() * 0.7F + 0.3F,
                        false
                    );
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    private void finishConversion(ServerLevel level) {
        Villager converted = this.convertTo( // CraftBukkit
            EntityType.VILLAGER,
            ConversionParams.single(this, false, false),
            mob -> {
                for (EquipmentSlot equipmentSlot : this.dropPreservedEquipment(
                    level, itemStack -> !EnchantmentHelper.has(itemStack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
                )) {
                    SlotAccess slot = mob.getSlot(equipmentSlot.getIndex() + 300);
                    if (slot != null) {
                        slot.set(this.getItemBySlot(equipmentSlot));
                    }
                }

                mob.setVillagerData(this.getVillagerData());
                if (this.gossips != null) {
                    mob.setGossips(this.gossips);
                }

                if (this.tradeOffers != null) {
                    mob.setOffers(this.tradeOffers.copy());
                }

                mob.setVillagerXp(this.villagerXp);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.CONVERSION, null);
                mob.refreshBrain(level);
                if (this.conversionStarter != null) {
                    Player playerByUuid = level.getGlobalPlayerByUUID(this.conversionStarter); // Paper - check global player list where appropriate
                    if (playerByUuid instanceof ServerPlayer) {
                        CriteriaTriggers.CURED_ZOMBIE_VILLAGER.trigger((ServerPlayer)playerByUuid, this, mob);
                        level.onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, playerByUuid, mob);
                    }
                }

                mob.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION); // CraftBukkit
                if (!this.isSilent()) {
                    level.levelEvent(null, LevelEvent.SOUND_ZOMBIE_CONVERTED, this.blockPosition(), 0);
                }
                // CraftBukkit start
            }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.CURED, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CURED // CraftBukkit
        );
        if (converted == null) {
            ((org.bukkit.entity.ZombieVillager) this.getBukkitEntity()).setConversionTime(-1); // SPIGOT-5208: End conversion to stop event spam
        }
        // CraftBukkit end
    }

    @VisibleForTesting
    public void setVillagerConversionTime(int villagerConversionTime) {
        this.villagerConversionTime = villagerConversionTime;
    }

    private int getConversionProgress() {
        int i = 1;
        if (this.random.nextFloat() < 0.01F) {
            int i1 = 0;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int i2 = (int)this.getX() - 4; i2 < (int)this.getX() + 4 && i1 < 14; i2++) {
                for (int i3 = (int)this.getY() - 4; i3 < (int)this.getY() + 4 && i1 < 14; i3++) {
                    for (int i4 = (int)this.getZ() - 4; i4 < (int)this.getZ() + 4 && i1 < 14; i4++) {
                        BlockState blockState = this.level().getBlockState(mutableBlockPos.set(i2, i3, i4));
                        if (blockState.is(Blocks.IRON_BARS) || blockState.getBlock() instanceof BedBlock) {
                            if (this.random.nextFloat() < 0.3F) {
                                i++;
                            }

                            i1++;
                        }
                    }
                }
            }
        }

        return i;
    }

    @Override
    public float getVoicePitch() {
        return this.isBaby()
            ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 2.0F
            : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    public SoundEvent getStepSound() {
        return SoundEvents.ZOMBIE_VILLAGER_STEP;
    }

    public void setTradeOffers(MerchantOffers tradeOffers) {
        this.tradeOffers = tradeOffers;
    }

    public void setGossips(GossipContainer gossips) {
        this.gossips = gossips;
    }

    @Override
    public void setVillagerData(VillagerData data) {
        VillagerData villagerData = this.getVillagerData();
        if (!villagerData.profession().equals(data.profession())) {
            this.tradeOffers = null;
        }

        this.entityData.set(DATA_VILLAGER_DATA, data);
    }

    @Override
    public VillagerData getVillagerData() {
        return this.entityData.get(DATA_VILLAGER_DATA);
    }

    public int getVillagerXp() {
        return this.villagerXp;
    }

    public void setVillagerXp(int villagerXp) {
        this.villagerXp = villagerXp;
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.VILLAGER_VARIANT
            ? castComponentValue((DataComponentType<T>)component, this.getVillagerData().type())
            : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.VILLAGER_VARIANT);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.VILLAGER_VARIANT) {
            Holder<VillagerType> holder = castComponentValue(DataComponents.VILLAGER_VARIANT, value);
            this.setVillagerData(this.getVillagerData().withType(holder));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }
}
