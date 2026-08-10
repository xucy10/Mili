package net.minecraft.world.level.portal;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

public record TeleportTransition(
    ServerLevel newLevel,
    Vec3 position,
    Vec3 deltaMovement,
    float yRot,
    float xRot,
    boolean missingRespawnBlock,
    boolean asPassenger,
    Set<Relative> relatives,
    TeleportTransition.PostTeleportTransition postTeleportTransition
    , org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause // CraftBukkit
) {
    public static final TeleportTransition.PostTeleportTransition DO_NOTHING = entity -> {};
    public static final TeleportTransition.PostTeleportTransition PLAY_PORTAL_SOUND = TeleportTransition::playPortalSound;
    public static final TeleportTransition.PostTeleportTransition PLACE_PORTAL_TICKET = TeleportTransition::placePortalTicket;

    // CraftBukkit start
    public TeleportTransition(ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot, boolean missingRespawnBlock, boolean asPassenger, Set<Relative> relatives, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        this(newLevel, position, deltaMovement, yRot, xRot, missingRespawnBlock, asPassenger, relatives, postTeleportTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }
    // CraftBukkit end

    public TeleportTransition(
        ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot, TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        this(newLevel, position, deltaMovement, yRot, xRot, Set.of(), postTeleportTransition);
    }

    public TeleportTransition(
        ServerLevel newLevel,
        Vec3 position,
        Vec3 deltaMovement,
        float yRot,
        float xRot,
        Set<Relative> relatives,
        TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        // CraftBukkit start
        this(newLevel, position, deltaMovement, yRot, xRot, relatives, postTeleportTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }
    public TeleportTransition(
        ServerLevel newLevel,
        Vec3 position,
        Vec3 deltaMovement,
        float yRot,
        float xRot,
        Set<Relative> relatives,
        TeleportTransition.PostTeleportTransition postTeleportTransition,
        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause
    ) {
        this(newLevel, position, deltaMovement, yRot, xRot, false, false, relatives, postTeleportTransition, cause);
        // CraftBukkit end
    }

    private static void playPortalSound(Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundLevelEventPacket(LevelEvent.SOUND_PORTAL_TRAVEL, BlockPos.ZERO, 0, false));
        }
    }

    private static void placePortalTicket(Entity entity) {
        entity.placePortalTicket(BlockPos.containing(entity.position()));
    }

    public static TeleportTransition createDefault(ServerPlayer player, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        ServerLevel serverLevel = player.level().getServer().findRespawnDimension();
        LevelData.RespawnData respawnData = serverLevel.getRespawnData();
        return new TeleportTransition(
            serverLevel,
            findAdjustedSharedSpawnPos(serverLevel, player),
            Vec3.ZERO,
            respawnData.yaw(),
            respawnData.pitch(),
            false,
            false,
            Set.of(),
            postTeleportTransition
        );
    }

    public static TeleportTransition missingRespawnBlock(ServerPlayer player, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        ServerLevel serverLevel = player.level().getServer().findRespawnDimension();
        LevelData.RespawnData respawnData = serverLevel.getRespawnData();
        return new TeleportTransition(
            serverLevel,
            findAdjustedSharedSpawnPos(serverLevel, player),
            Vec3.ZERO,
            respawnData.yaw(),
            respawnData.pitch(),
            true,
            false,
            Set.of(),
            postTeleportTransition
        );
    }

    private static Vec3 findAdjustedSharedSpawnPos(ServerLevel level, Entity entity) {
        return entity.adjustSpawnLocation(level, level.getRespawnData().pos()).getBottomCenter();
    }

    public TeleportTransition withRotation(float yRot, float xRot) {
        return new TeleportTransition(
            this.newLevel(),
            this.position(),
            this.deltaMovement(),
            yRot,
            xRot,
            this.missingRespawnBlock(),
            this.asPassenger(),
            this.relatives(),
            this.postTeleportTransition(),
            this.cause() // Paper - keep cause
        );
    }

    public TeleportTransition withPosition(Vec3 position) {
        return new TeleportTransition(
            this.newLevel(),
            position,
            this.deltaMovement(),
            this.yRot(),
            this.xRot(),
            this.missingRespawnBlock(),
            this.asPassenger(),
            this.relatives(),
            this.postTeleportTransition(),
            this.cause() // Paper - keep cause
        );
    }

    public TeleportTransition transitionAsPassenger() {
        return new TeleportTransition(
            this.newLevel(),
            this.position(),
            this.deltaMovement(),
            this.yRot(),
            this.xRot(),
            this.missingRespawnBlock(),
            true,
            this.relatives(),
            this.postTeleportTransition(),
            this.cause() // Paper - keep cause
        );
    }

    @FunctionalInterface
    public interface PostTeleportTransition {
        void onTransition(Entity entity);

        default TeleportTransition.PostTeleportTransition then(TeleportTransition.PostTeleportTransition postTeleportTransition) {
            return entity -> {
                this.onTransition(entity);
                postTeleportTransition.onTransition(entity);
            };
        }
    }
}
