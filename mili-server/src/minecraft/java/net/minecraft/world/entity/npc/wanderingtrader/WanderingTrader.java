package net.minecraft.world.entity.npc.wanderingtrader;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

public class WanderingTrader extends AbstractVillager implements Consumable.OverrideConsumeSound {
    private static final int DEFAULT_DESPAWN_DELAY = 0;
    private @Nullable BlockPos wanderTarget;
    private int despawnDelay = 0;
    // Paper start - Add more WanderingTrader API
    public boolean canDrinkPotion = true;
    public boolean canDrinkMilk = true;
    // Paper end - Add more WanderingTrader API

    public WanderingTrader(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector
            .addGoal(
                0,
                new UseItemGoal<>(
                    this,
                    PotionContents.createItemStack(Items.POTION, Potions.INVISIBILITY),
                    SoundEvents.WANDERING_TRADER_DISAPPEARED,
                    wanderingTrader -> this.canDrinkPotion && this.level().isDarkOutside() && !wanderingTrader.isInvisible() // Paper - Add more WanderingTrader API
                )
            );
        this.goalSelector
            .addGoal(
                0,
                new UseItemGoal<>(
                    this,
                    new ItemStack(Items.MILK_BUCKET),
                    SoundEvents.WANDERING_TRADER_REAPPEARED,
                    wanderingTrader -> this.canDrinkMilk && this.level().isBrightOutside() && wanderingTrader.isInvisible() // Paper - Add more WanderingTrader API
                )
            );
        this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Zombie.class, 8.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Evoker.class, 12.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Vindicator.class, 8.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Vex.class, 8.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Pillager.class, 15.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Illusioner.class, 12.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Zoglin.class, 10.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.5));
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(2, new WanderingTrader.WanderToPositionGoal(this, 2.0, 0.35));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.35));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.35));
        this.goalSelector.addGoal(9, new InteractGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (!itemInHand.is(Items.VILLAGER_SPAWN_EGG) && this.isAlive() && !this.isTrading() && !this.isBaby()) {
            if (hand == InteractionHand.MAIN_HAND) {
                player.awardStat(Stats.TALKED_TO_VILLAGER);
            }

            if (!this.level().isClientSide()) {
                if (this.getOffers().isEmpty()) {
                    return InteractionResult.CONSUME;
                }

                this.voidTrade = false; // Mili - force void trade
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    protected void updateTrades(ServerLevel level) {
        MerchantOffers offers = this.getOffers();

        for (Pair<VillagerTrades.ItemListing[], Integer> pair : VillagerTrades.WANDERING_TRADER_TRADES) {
            VillagerTrades.ItemListing[] itemListings = pair.getLeft();
            this.addOffersFromItemListings(level, offers, itemListings, pair.getRight());
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DespawnDelay", this.despawnDelay);
        output.storeNullable("wander_target", BlockPos.CODEC, this.wanderTarget);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.despawnDelay = input.getIntOr("DespawnDelay", 0);
        this.wanderTarget = input.read("wander_target", BlockPos.CODEC).orElse(null);
        this.setAge(Math.max(0, this.getAge()));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            int i = 3 + this.random.nextInt(4);
            this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), i, org.bukkit.entity.ExperienceOrb.SpawnReason.VILLAGER_TRADE, this.getTradingPlayer(), this)); // Paper
        }
    }

    @Override
    public SoundEvent getAmbientSound() {
        return this.isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }

    @Override
    public SoundEvent getConsumeSound(ItemStack stack) {
        return stack.is(Items.MILK_BUCKET) ? SoundEvents.WANDERING_TRADER_DRINK_MILK : SoundEvents.WANDERING_TRADER_DRINK_POTION;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean getYesSound) {
        return getYesSound ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.WANDERING_TRADER_YES;
    }

    public void setDespawnDelay(int despawnDelay) {
        this.despawnDelay = despawnDelay;
    }

    public int getDespawnDelay() {
        return this.despawnDelay;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) {
            this.maybeDespawn();
        }
    }

    private void maybeDespawn() {
        if (this.despawnDelay > 0 && !this.isTrading() && --this.despawnDelay == 0) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }
    }

    public void setWanderTarget(@Nullable BlockPos wanderTarget) {
        this.wanderTarget = wanderTarget;
    }

    @Nullable public BlockPos getWanderTarget() {
        return this.wanderTarget;
    }

    class WanderToPositionGoal extends Goal {
        final WanderingTrader trader;
        final double stopDistance;
        final double speedModifier;

        WanderToPositionGoal(final WanderingTrader trader, final double stopDistance, final double speedModifier) {
            this.trader = trader;
            this.stopDistance = stopDistance;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public void stop() {
            this.trader.setWanderTarget(null);
            WanderingTrader.this.navigation.stop();
        }

        @Override
        public boolean canUse() {
            BlockPos wanderTarget = this.trader.getWanderTarget();
            return wanderTarget != null && this.isTooFarAway(wanderTarget, this.stopDistance);
        }

        @Override
        public void tick() {
            BlockPos wanderTarget = this.trader.getWanderTarget();
            if (wanderTarget != null && WanderingTrader.this.navigation.isDone()) {
                if (this.isTooFarAway(wanderTarget, 10.0)) {
                    Vec3 vec3 = new Vec3(
                            wanderTarget.getX() - this.trader.getX(), wanderTarget.getY() - this.trader.getY(), wanderTarget.getZ() - this.trader.getZ()
                        )
                        .normalize();
                    Vec3 vec31 = vec3.scale(10.0).add(this.trader.getX(), this.trader.getY(), this.trader.getZ());
                    WanderingTrader.this.navigation.moveTo(vec31.x, vec31.y, vec31.z, this.speedModifier);
                } else {
                    WanderingTrader.this.navigation.moveTo(wanderTarget.getX(), wanderTarget.getY(), wanderTarget.getZ(), this.speedModifier);
                }
            }
        }

        private boolean isTooFarAway(BlockPos pos, double distance) {
            return !pos.closerToCenterThan(this.trader.position(), distance);
        }
    }

    // KioCG start
    @Override
    public boolean shouldTickHot() {
        return false;
    }
    // KioCG end
}
