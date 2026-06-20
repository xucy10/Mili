package net.minecraft.world.food;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class FoodData {
    private static final int DEFAULT_TICK_TIMER = 0;
    private static final float DEFAULT_EXHAUSTION_LEVEL = 0.0F;
    public int foodLevel = 20;
    public float saturationLevel = 5.0F;
    public float exhaustionLevel;
    private int tickTimer;
    // CraftBukkit start
    public int saturatedRegenRate = 10;
    public int unsaturatedRegenRate = 80;
    public int starvationRate = 80;
    // CraftBukkit end

    private void add(int nutrition, float saturationLevel) {
        this.foodLevel = Mth.clamp(nutrition + this.foodLevel, 0, 20);
        this.saturationLevel = Mth.clamp(saturationLevel + this.saturationLevel, 0.0F, (float)this.foodLevel);
    }

    public void eat(int nutrition, float saturationModifier) {
        this.add(nutrition, FoodConstants.saturationByModifier(nutrition, saturationModifier));
    }

    public void eat(FoodProperties foodProperties) {
        this.add(foodProperties.nutrition(), foodProperties.saturation());
    }

    // CraftBukkit start
    public void eat(FoodProperties foodProperties, net.minecraft.world.item.ItemStack stack, ServerPlayer serverPlayer) {
        int oldFoodLevel = this.foodLevel;
        org.bukkit.event.entity.FoodLevelChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callFoodLevelChangeEvent(serverPlayer, foodProperties.nutrition() + oldFoodLevel, stack);
        if (!event.isCancelled()) {
            this.add(event.getFoodLevel() - oldFoodLevel, foodProperties.saturation());
        }
        serverPlayer.getBukkitEntity().sendHealthUpdate();
    }
    // CraftBukkit end

    public void tick(ServerPlayer player) {
        ServerLevel serverLevel = player.level();
        Difficulty difficulty = serverLevel.getDifficulty();
        if (this.exhaustionLevel > 4.0F) {
            this.exhaustionLevel -= 4.0F;
            if (this.saturationLevel > 0.0F) {
                this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
            } else if (difficulty != Difficulty.PEACEFUL) {
                // CraftBukkit start
                org.bukkit.event.entity.FoodLevelChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callFoodLevelChangeEvent(player, Math.max(this.foodLevel - 1, 0));

                if (!event.isCancelled()) {
                    this.foodLevel = event.getFoodLevel();
                }

                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(player.getBukkitEntity().getScaledHealth(), this.foodLevel, this.saturationLevel));
                // CraftBukkit end
            }
        }

        boolean flag = serverLevel.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        if (flag && this.saturationLevel > 0.0F && player.isHurt() && this.foodLevel >= 20) {
            this.tickTimer++;
            if (this.tickTimer >= this.saturatedRegenRate) { // CraftBukkit
                float min = Math.min(this.saturationLevel, 6.0F);
                player.heal(min / 6.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.SATIATED, true); // CraftBukkit - added RegainReason // Paper - This is fast regen
                // this.addExhaustion(min); CraftBukkit - EntityExhaustionEvent
                player.causeFoodExhaustion(min, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.REGEN); // CraftBukkit - EntityExhaustionEvent
                this.tickTimer = 0;
            }
        } else if (flag && this.foodLevel >= 18 && player.isHurt()) {
            this.tickTimer++;
            if (this.tickTimer >= this.unsaturatedRegenRate) { // CraftBukkit - add regen rate manipulation
                player.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.SATIATED); // CraftBukkit - added RegainReason
                // this.addExhaustion(6.0F); CraftBukkit - EntityExhaustionEvent
                player.causeFoodExhaustion(player.level().spigotConfig.regenExhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.REGEN); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
                this.tickTimer = 0;
            }
        } else if (this.foodLevel <= 0) {
            this.tickTimer++;
            if (this.tickTimer >= this.starvationRate) { // CraftBukkit - add regen rate manipulation
                if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
                    player.hurtServer(serverLevel, player.damageSources().starve(), 1.0F);
                }

                this.tickTimer = 0;
            }
        } else {
            this.tickTimer = 0;
        }
    }

    public void readAdditionalSaveData(ValueInput input) {
        this.foodLevel = input.getIntOr("foodLevel", 20);
        this.tickTimer = input.getIntOr("foodTickTimer", 0);
        this.saturationLevel = input.getFloatOr("foodSaturationLevel", 5.0F);
        this.exhaustionLevel = input.getFloatOr("foodExhaustionLevel", 0.0F);
    }

    public void addAdditionalSaveData(ValueOutput output) {
        output.putInt("foodLevel", this.foodLevel);
        output.putInt("foodTickTimer", this.tickTimer);
        output.putFloat("foodSaturationLevel", this.saturationLevel);
        output.putFloat("foodExhaustionLevel", this.exhaustionLevel);
    }

    public int getFoodLevel() {
        return this.foodLevel;
    }

    public boolean hasEnoughFood() {
        return this.getFoodLevel() > 6.0F;
    }

    public boolean needsFood() {
        return this.foodLevel < 20;
    }

    public void addExhaustion(float exhaustion) {
        this.exhaustionLevel = Math.min(this.exhaustionLevel + exhaustion, 40.0F);
    }

    public float getSaturationLevel() {
        return this.saturationLevel;
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = foodLevel;
    }

    public void setSaturation(float saturationLevel) {
        this.saturationLevel = saturationLevel;
    }
}
