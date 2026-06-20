package net.minecraft.world.effect;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.gamerules.GameRules;

class WeavingMobEffect extends MobEffect {
    private final ToIntFunction<RandomSource> maxCobwebs;

    protected WeavingMobEffect(MobEffectCategory category, int color, ToIntFunction<RandomSource> maxCobwebs) {
        super(category, color, ParticleTypes.ITEM_COBWEB);
        this.maxCobwebs = maxCobwebs;
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED && (entity instanceof Player || level.getGameRules().get(GameRules.MOB_GRIEFING))) {
            this.spawnCobwebsRandomlyAround(level, entity.getRandom(), entity.blockPosition(), entity); // Paper - Fire EntityChangeBlockEvent in more places
        }
    }

    private void spawnCobwebsRandomlyAround(ServerLevel level, RandomSource random, BlockPos pos, LivingEntity entity) { // Paper - Fire EntityChangeBlockEvent in more places
        Set<BlockPos> set = Sets.newHashSet();
        int i = this.maxCobwebs.applyAsInt(random);

        for (BlockPos blockPos : BlockPos.randomInCube(random, 15, pos, 1)) {
            BlockPos blockPos1 = blockPos.below();
            if (!set.contains(blockPos)
                && level.getBlockState(blockPos).canBeReplaced()
                && level.getBlockState(blockPos1).isFaceSturdy(level, blockPos1, Direction.UP)) {
                set.add(blockPos.immutable());
                if (set.size() >= i) {
                    break;
                }
            }
        }

        for (BlockPos blockPosx : set) {
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, blockPosx, Blocks.COBWEB.defaultBlockState())) continue; // Paper - Fire EntityChangeBlockEvent in more places
            level.setBlock(blockPosx, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
            level.levelEvent(LevelEvent.ANIMATION_SPAWN_COBWEB, blockPosx, 0);
        }
    }
}
