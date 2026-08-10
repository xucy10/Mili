package net.minecraft.world.entity;

import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public record PositionMoveRotation(Vec3 position, Vec3 deltaMovement, float yRot, float xRot) {
    public static final StreamCodec<FriendlyByteBuf, PositionMoveRotation> STREAM_CODEC = StreamCodec.composite(
        Vec3.STREAM_CODEC,
        PositionMoveRotation::position,
        Vec3.STREAM_CODEC,
        PositionMoveRotation::deltaMovement,
        ByteBufCodecs.FLOAT,
        PositionMoveRotation::yRot,
        ByteBufCodecs.FLOAT,
        PositionMoveRotation::xRot,
        PositionMoveRotation::new
    );

    public static PositionMoveRotation of(Entity entity) {
        return entity.isInterpolating()
            ? new PositionMoveRotation(
                entity.getInterpolation().position(), entity.getKnownMovement(), entity.getInterpolation().yRot(), entity.getInterpolation().xRot()
            )
            : new PositionMoveRotation(entity.position(), entity.getKnownMovement(), entity.getYRot(), entity.getXRot());
    }

    public PositionMoveRotation withRotation(float yRot, float xRot) {
        return new PositionMoveRotation(this.position(), this.deltaMovement(), yRot, xRot);
    }

    public static PositionMoveRotation of(TeleportTransition teleportTransition) {
        return new PositionMoveRotation(teleportTransition.position(), teleportTransition.deltaMovement(), teleportTransition.yRot(), teleportTransition.xRot());
    }

    public static PositionMoveRotation calculateAbsolute(PositionMoveRotation current, PositionMoveRotation after, Set<Relative> relatives) {
        double d = relatives.contains(Relative.X) ? current.position.x : 0.0;
        double d1 = relatives.contains(Relative.Y) ? current.position.y : 0.0;
        double d2 = relatives.contains(Relative.Z) ? current.position.z : 0.0;
        float f = relatives.contains(Relative.Y_ROT) ? current.yRot : 0.0F;
        float f1 = relatives.contains(Relative.X_ROT) ? current.xRot : 0.0F;
        Vec3 vec3 = new Vec3(d + after.position.x, d1 + after.position.y, d2 + after.position.z);
        float f2 = f + after.yRot;
        float f3 = Mth.clamp(f1 + after.xRot, -90.0F, 90.0F);
        Vec3 vec31 = current.deltaMovement;
        if (relatives.contains(Relative.ROTATE_DELTA)) {
            float f4 = current.yRot - f2;
            float f5 = current.xRot - f3;
            vec31 = vec31.xRot((float)Math.toRadians(f5));
            vec31 = vec31.yRot((float)Math.toRadians(f4));
        }

        Vec3 vec32 = new Vec3(
            calculateDelta(vec31.x, after.deltaMovement.x, relatives, Relative.DELTA_X),
            calculateDelta(vec31.y, after.deltaMovement.y, relatives, Relative.DELTA_Y),
            calculateDelta(vec31.z, after.deltaMovement.z, relatives, Relative.DELTA_Z)
        );
        return new PositionMoveRotation(vec3, vec32, f2, f3);
    }

    private static double calculateDelta(double position, double deltaMovement, Set<Relative> relatives, Relative deltaRelative) {
        return relatives.contains(deltaRelative) ? position + deltaMovement : deltaMovement;
    }
}
