package net.minecraft.world.level.block.entity.trialspawner;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public interface PlayerDetector {
    PlayerDetector NO_CREATIVE_PLAYERS = (level, entitySelector, pos, maxDistance, requireLineOfSight) -> entitySelector.getPlayers(
            level, player -> player.blockPosition().closerThan(pos, maxDistance) && !player.isCreative() && !player.isSpectator() && player.affectsSpawning // Paper - Affects Spawning API
        )
        .stream()
        .filter(player -> !requireLineOfSight || inLineOfSight(level, pos.getCenter(), player.getEyePosition()))
        .map(Entity::getUUID)
        .toList();
    PlayerDetector INCLUDING_CREATIVE_PLAYERS = (level, entitySelector, pos, maxDistance, requireLineOfSight) -> entitySelector.getPlayers(
            level, player -> player.blockPosition().closerThan(pos, maxDistance) && !player.isSpectator()
        )
        .stream()
        .filter(player -> !requireLineOfSight || inLineOfSight(level, pos.getCenter(), player.getEyePosition()))
        .map(Entity::getUUID)
        .toList();
    PlayerDetector SHEEP = (level, entitySelector, pos, maxDistance, requireLineOfSight) -> {
        AABB aabb = new AABB(pos).inflate(maxDistance);
        return entitySelector.getEntities(level, EntityType.SHEEP, aabb, LivingEntity::isAlive)
            .stream()
            .filter(sheep -> !requireLineOfSight || inLineOfSight(level, pos.getCenter(), sheep.getEyePosition()))
            .map(Entity::getUUID)
            .toList();
    };

    List<UUID> detect(ServerLevel level, PlayerDetector.EntitySelector entitySelector, BlockPos pos, double maxDistance, boolean requireLineOfSight);

    private static boolean inLineOfSight(Level level, Vec3 pos, Vec3 targetPos) {
        BlockHitResult blockHitResult = level.clip(new ClipContext(targetPos, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return blockHitResult.getBlockPos().equals(BlockPos.containing(pos)) || blockHitResult.getType() == HitResult.Type.MISS;
    }

    public interface EntitySelector {
        PlayerDetector.EntitySelector SELECT_FROM_LEVEL = new PlayerDetector.EntitySelector() {
            @Override
            public List<ServerPlayer> getPlayers(ServerLevel level, Predicate<? super Player> predicate) {
                return level.getPlayers(predicate);
            }

            @Override
            public <T extends Entity> List<T> getEntities(
                ServerLevel level, EntityTypeTest<Entity, T> typeTest, AABB boundingBox, Predicate<? super T> predicate
            ) {
                return level.getEntities(typeTest, boundingBox, predicate);
            }
        };

        List<? extends Player> getPlayers(ServerLevel level, Predicate<? super Player> predicate);

        <T extends Entity> List<T> getEntities(ServerLevel level, EntityTypeTest<Entity, T> typeTest, AABB boundingBox, Predicate<? super T> predicate);

        static PlayerDetector.EntitySelector onlySelectPlayer(Player player) {
            return onlySelectPlayers(List.of(player));
        }

        static PlayerDetector.EntitySelector onlySelectPlayers(final List<Player> players) {
            return new PlayerDetector.EntitySelector() {
                @Override
                public List<Player> getPlayers(ServerLevel level, Predicate<? super Player> predicate) {
                    return players.stream().filter(predicate).toList();
                }

                @Override
                public <T extends Entity> List<T> getEntities(
                    ServerLevel level, EntityTypeTest<Entity, T> typeTest, AABB boundingBox, Predicate<? super T> predicate
                ) {
                    return players.stream().map(typeTest::tryCast).filter(Objects::nonNull).filter(predicate).toList();
                }
            };
        }
    }
}
