package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import org.jspecify.annotations.Nullable;

public class CelebrateVillagersSurvivedRaid extends Behavior<Villager> {
    private @Nullable Raid currentRaid;

    public CelebrateVillagersSurvivedRaid(int minDuration, int maxDuration) {
        super(ImmutableMap.of(), minDuration, maxDuration);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        BlockPos blockPos = owner.blockPosition();
        this.currentRaid = level.getRaidAt(blockPos);
        return this.currentRaid != null && this.currentRaid.isVictory() && MoveToSkySeeingSpot.hasNoBlocksAbove(level, owner, blockPos);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.currentRaid != null && !this.currentRaid.isStopped();
    }

    @Override
    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        this.currentRaid = null;
        entity.getBrain().updateActivityFromSchedule(level.environmentAttributes(), level.getGameTime(), entity.position());
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        RandomSource random = owner.getRandom();
        if (random.nextInt(100) == 0) {
            owner.playCelebrateSound();
        }

        if (random.nextInt(200) == 0 && MoveToSkySeeingSpot.hasNoBlocksAbove(level, owner, owner.blockPosition())) {
            DyeColor dyeColor = Util.getRandom(DyeColor.values(), random);
            int randomInt = random.nextInt(3);
            ItemStack firework = this.getFirework(dyeColor, randomInt);
            Projectile.spawnProjectile(new FireworkRocketEntity(owner.level(), owner, owner.getX(), owner.getEyeY(), owner.getZ(), firework), level, firework);
        }
    }

    private ItemStack getFirework(DyeColor color, int flightTime) {
        ItemStack itemStack = new ItemStack(Items.FIREWORK_ROCKET);
        itemStack.set(
            DataComponents.FIREWORKS,
            new Fireworks(
                (byte)flightTime,
                List.of(new FireworkExplosion(FireworkExplosion.Shape.BURST, IntList.of(color.getFireworkColor()), IntList.of(), false, false))
            )
        );
        return itemStack;
    }
}
