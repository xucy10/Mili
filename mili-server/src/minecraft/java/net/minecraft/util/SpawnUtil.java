package net.minecraft.util;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnUtil {

    public static <T extends Mob> Optional<T> trySpawnMob(
        EntityType<T> entityType,
        EntitySpawnReason spawnReason,
        ServerLevel level,
        BlockPos pos,
        int attempts,
        int range,
        int yOffset,
        SpawnUtil.Strategy strategy,
        boolean checkCollision
    ) {
        // CraftBukkit start
        return trySpawnMob(entityType, spawnReason, level, pos, attempts, range, yOffset, strategy, checkCollision, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT, null); // Paper - pre creature spawn event
    }

    public static <T extends Mob> Optional<T> trySpawnMob(
        EntityType<T> entityType,
        EntitySpawnReason spawnReason,
        ServerLevel level,
        BlockPos pos,
        int attempts,
        int range,
        int yOffset,
        SpawnUtil.Strategy strategy,
        boolean checkCollision,
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason,
        @javax.annotation.Nullable Runnable onAbort // Paper - pre creature spawn event
    ) {
        // CraftBukkit end
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (int i = 0; i < attempts; i++) {
            int i1 = Mth.randomBetweenInclusive(level.random, -range, range);
            int i2 = Mth.randomBetweenInclusive(level.random, -range, range);
            mutableBlockPos.setWithOffset(pos, i1, yOffset, i2);
            if (level.getWorldBorder().isWithinBounds(mutableBlockPos)
                && moveToPossibleSpawnPosition(level, yOffset, mutableBlockPos, strategy)
                && (
                    !checkCollision
                        || level.noCollision(entityType.getSpawnAABB(mutableBlockPos.getX() + 0.5, mutableBlockPos.getY(), mutableBlockPos.getZ() + 0.5))
                )) {
                // Paper start - PreCreatureSpawnEvent
                final com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
                    org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level),
                    org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(entityType),
                    reason
                );
                if (!event.callEvent()) {
                    if (event.shouldAbortSpawn()) {
                        if (onAbort != null) {
                            onAbort.run();
                        }
                        return Optional.empty();
                    }
                    break;
                }
                // Paper end - PreCreatureSpawnEvent
                T mob = (T)entityType.create(level, null, mutableBlockPos, spawnReason, false, false);
                if (mob != null) {
                    if (mob.checkSpawnRules(level, spawnReason) && mob.checkSpawnObstruction(level)) {
                        level.addFreshEntityWithPassengers(mob, reason); // CraftBukkit
                        if (mob.isRemoved()) return Optional.empty(); // CraftBukkit
                        mob.playAmbientSound();
                        return Optional.of(mob);
                    }

                    //mob.discard(null); // CraftBukkit - add Bukkit remove cause // Folia - region threading
                }
            }
        }

        return Optional.empty();
    }

    private static boolean moveToPossibleSpawnPosition(ServerLevel level, int yOffset, BlockPos.MutableBlockPos pos, SpawnUtil.Strategy strategy) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos().set(pos);
        BlockState blockState = level.getBlockState(mutableBlockPos);

        for (int i = yOffset; i >= -yOffset; i--) {
            pos.move(Direction.DOWN);
            mutableBlockPos.setWithOffset(pos, Direction.UP);
            BlockState blockState1 = level.getBlockState(pos);
            if (strategy.canSpawnOn(level, pos, blockState1, mutableBlockPos, blockState)) {
                pos.move(Direction.UP);
                return true;
            }

            blockState = blockState1;
        }

        return false;
    }

    public interface Strategy {
        @Deprecated
        SpawnUtil.Strategy LEGACY_IRON_GOLEM = (level, targetPos, targetState, attemptedPos, attemptedState) -> !targetState.is(Blocks.COBWEB)
            && !targetState.is(Blocks.CACTUS)
            && !targetState.is(Blocks.GLASS_PANE)
            && !(targetState.getBlock() instanceof StainedGlassPaneBlock)
            && !(targetState.getBlock() instanceof StainedGlassBlock)
            && !(targetState.getBlock() instanceof LeavesBlock)
            && !targetState.is(Blocks.CONDUIT)
            && !targetState.is(Blocks.ICE)
            && !targetState.is(Blocks.TNT)
            && !targetState.is(Blocks.GLOWSTONE)
            && !targetState.is(Blocks.BEACON)
            && !targetState.is(Blocks.SEA_LANTERN)
            && !targetState.is(Blocks.FROSTED_ICE)
            && !targetState.is(Blocks.TINTED_GLASS)
            && !targetState.is(Blocks.GLASS)
            && (attemptedState.isAir() || attemptedState.liquid())
            && (targetState.isSolid() || targetState.is(Blocks.POWDER_SNOW));
        SpawnUtil.Strategy ON_TOP_OF_COLLIDER = (level, targetPos, targetState, attemptedPos, attemptedState) -> attemptedState.getCollisionShape(
                    level, attemptedPos
                )
                .isEmpty()
            && Block.isFaceFull(targetState.getCollisionShape(level, targetPos), Direction.UP);
        SpawnUtil.Strategy ON_TOP_OF_COLLIDER_NO_LEAVES = (level, targetPos, targetState, attemptedPos, attemptedState) -> attemptedState.getCollisionShape(
                    level, attemptedPos
                )
                .isEmpty()
            && !targetState.is(BlockTags.LEAVES)
            && Block.isFaceFull(targetState.getCollisionShape(level, targetPos), Direction.UP);

        boolean canSpawnOn(ServerLevel level, BlockPos targetPos, BlockState targetState, BlockPos attemptedPos, BlockState attemptedState);
    }
}
