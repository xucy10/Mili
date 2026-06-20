package net.minecraft.world.entity.npc.villager;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractVillager extends AgeableMob implements InventoryCarrier, Npc, Merchant {
    private static final EntityDataAccessor<Integer> DATA_UNHAPPY_COUNTER = SynchedEntityData.defineId(AbstractVillager.class, EntityDataSerializers.INT);
    public static final int VILLAGER_SLOT_OFFSET = 300;
    private static final int VILLAGER_INVENTORY_SIZE = 8;
    private @Nullable Player tradingPlayer;
    protected @Nullable MerchantOffers offers;
    private final SimpleContainer inventory = new SimpleContainer(8, (org.bukkit.craftbukkit.entity.CraftAbstractVillager) this.getBukkitEntity()); // CraftBukkit - add argument

    public AbstractVillager(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        if (spawnGroupData == null) {
            spawnGroupData = new AgeableMob.AgeableMobGroupData(false);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    public int getUnhappyCounter() {
        return this.entityData.get(DATA_UNHAPPY_COUNTER);
    }

    public void setUnhappyCounter(int unhappyCounter) {
        this.entityData.set(DATA_UNHAPPY_COUNTER, unhappyCounter);
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_UNHAPPY_COUNTER, 0);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Override
    public @Nullable Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    public boolean isTrading() {
        return this.tradingPlayer != null;
    }

    // CraftBukkit start
    @Override
    public org.bukkit.craftbukkit.inventory.CraftMerchant getCraftMerchant() {
        return (org.bukkit.craftbukkit.entity.CraftAbstractVillager) this.getBukkitEntity();
    }
    // CraftBukkit end

    // Paper start - Villager#resetOffers
    public void resetOffers() {
        this.offers = new MerchantOffers();
        this.updateTrades((net.minecraft.server.level.ServerLevel) this.level());
    }
    // Paper end - Villager#resetOffers

    @Override
    public MerchantOffers getOffers() {
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.offers == null) {
                this.offers = new MerchantOffers();
                this.updateTrades(serverLevel);
            }

            return this.offers;
        } else {
            throw new IllegalStateException("Cannot load Villager offers on the client");
        }
    }

    @Override
    public void overrideOffers(@Nullable MerchantOffers offers) {
    }

    @Override
    public void overrideXp(int xp) {
    }

    // Paper start - Add PlayerTradeEvent and PlayerPurchaseEvent
    @Override
    public void processTrade(MerchantOffer offer, io.papermc.paper.event.player.@Nullable PlayerPurchaseEvent event) { // The MerchantRecipe passed in here is the one set by the PlayerPurchaseEvent
        if (event == null || event.willIncreaseTradeUses()) {
            offer.increaseUses();
        }
        if (event == null || event.isRewardingExp()) {
            this.rewardTradeXp(offer);
        }
        this.notifyTrade(offer);
    }
    // Paper end - Add PlayerTradeEvent and PlayerPurchaseEvent

    @Override
    public void notifyTrade(MerchantOffer offer) {
        // offer.increaseUses(); // Paper - Add PlayerTradeEvent and PlayerPurchaseEvent
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        // this.rewardTradeXp(offer); // Paper - Add PlayerTradeEvent and PlayerPurchaseEvent
        if (this.tradingPlayer instanceof ServerPlayer) {
            CriteriaTriggers.TRADE.trigger((ServerPlayer)this.tradingPlayer, this, offer.getResult());
        }
    }

    protected abstract void rewardTradeXp(MerchantOffer offer);

    @Override
    public boolean showProgressBar() {
        return true;
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (!this.level().isClientSide() && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundTime = -this.getAmbientSoundInterval();
            this.makeSound(this.getTradeUpdatedSound(!stack.isEmpty()));
        }
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    protected SoundEvent getTradeUpdatedSound(boolean isYesSound) {
        return isYesSound ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO;
    }

    public void playCelebrateSound() {
        this.makeSound(SoundEvents.VILLAGER_CELEBRATE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (!this.level().isClientSide()) {
            MerchantOffers offers = this.getOffers();
            if (!offers.isEmpty()) {
                output.store("Offers", MerchantOffers.CODEC, offers);
            }
        }

        this.writeInventoryToTag(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.offers = input.read("Offers", MerchantOffers.CODEC).orElse(null);
        this.readInventoryFromTag(input);
    }

    // Folia start - region threading
    @Override
    public void preChangeDimension() {
        super.preChangeDimension();
        this.stopTrading();
    }
    // Folia end - region threading

    @Override
    public @Nullable Entity teleport(TeleportTransition teleportTransition) {
        this.preChangeDimension(); // Folia - region threading - move into preChangeDimension
        return super.teleport(teleportTransition);
    }

    protected void stopTrading() {
        this.setTradingPlayer(null);
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        this.stopTrading();
    }

    protected void addParticlesAroundSelf(ParticleOptions options) {
        for (int i = 0; i < 5; i++) {
            double d = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            this.level().addParticle(options, this.getRandomX(1.0), this.getRandomY() + 1.0, this.getRandomZ(1.0), d, d1, d2);
        }
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        int i = slot - 300;
        return i >= 0 && i < this.inventory.getContainerSize() ? this.inventory.getSlot(i) : super.getSlot(slot);
    }

    protected abstract void updateTrades(ServerLevel level);

    protected void addOffersFromItemListings(ServerLevel level, MerchantOffers givenMerchantOffers, VillagerTrades.ItemListing[] newTrades, int maxNumbers) {
        ArrayList<VillagerTrades.ItemListing> list = Lists.newArrayList(newTrades);
        int i = 0;

        while (i < maxNumbers && !list.isEmpty()) {
            MerchantOffer offer = list.remove(this.random.nextInt(list.size())).getOffer(level, this, this.random);
            if (offer != null) {
                // CraftBukkit start
                org.bukkit.event.entity.VillagerAcquireTradeEvent event = new org.bukkit.event.entity.VillagerAcquireTradeEvent((org.bukkit.entity.AbstractVillager) this.getBukkitEntity(), offer.asBukkit());
                // Suppress during worldgen
                if (this.valid) {
                    event.callEvent();
                }
                if (!event.isCancelled()) {
                    // Paper start - Fix crash from invalid ingredient list
                    final org.bukkit.craftbukkit.inventory.CraftMerchantRecipe craftMerchantRecipe = org.bukkit.craftbukkit.inventory.CraftMerchantRecipe.fromBukkit(event.getRecipe());
                    if (craftMerchantRecipe.getIngredients().isEmpty()) return;
                    givenMerchantOffers.add(craftMerchantRecipe.toMinecraft());
                    // Paper end - Fix crash from invalid ingredient list
                }
                // CraftBukkit end
                i++;
            }
        }
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTick) {
        float f = Mth.lerp(partialTick, this.yBodyRotO, this.yBodyRot) * (float) (Math.PI / 180.0);
        Vec3 vec3 = new Vec3(0.0, this.getBoundingBox().getYsize() - 1.0, 0.2);
        return this.getPosition(partialTick).add(vec3.yRot(-f));
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.getTradingPlayer() == player && this.isAlive() && player.isWithinEntityInteractionRange(this, 4.0);
    }
}
