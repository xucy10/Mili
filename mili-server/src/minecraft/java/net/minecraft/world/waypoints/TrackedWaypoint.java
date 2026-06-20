package net.minecraft.world.waypoints;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;

public abstract class TrackedWaypoint implements Waypoint {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final StreamCodec<ByteBuf, TrackedWaypoint> STREAM_CODEC = StreamCodec.ofMember(TrackedWaypoint::write, TrackedWaypoint::read);
    protected final Either<UUID, String> identifier;
    private final Waypoint.Icon icon;
    private final TrackedWaypoint.Type type;

    TrackedWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, TrackedWaypoint.Type type) {
        this.identifier = identifier;
        this.icon = icon;
        this.type = type;
    }

    public Either<UUID, String> id() {
        return this.identifier;
    }

    public abstract void update(TrackedWaypoint waypoint);

    public void write(ByteBuf buffer) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(buffer);
        friendlyByteBuf.writeEither(this.identifier, UUIDUtil.STREAM_CODEC, FriendlyByteBuf::writeUtf);
        Waypoint.Icon.STREAM_CODEC.encode(friendlyByteBuf, this.icon);
        friendlyByteBuf.writeEnum(this.type);
        this.writeContents(buffer);
    }

    public abstract void writeContents(ByteBuf buffer);

    private static TrackedWaypoint read(ByteBuf buffer) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(buffer);
        Either<UUID, String> either = friendlyByteBuf.readEither(UUIDUtil.STREAM_CODEC, FriendlyByteBuf::readUtf);
        Waypoint.Icon icon = Waypoint.Icon.STREAM_CODEC.decode(friendlyByteBuf);
        TrackedWaypoint.Type type = friendlyByteBuf.readEnum(TrackedWaypoint.Type.class);
        return type.constructor.apply(either, icon, friendlyByteBuf);
    }

    public static TrackedWaypoint setPosition(UUID uuid, Waypoint.Icon icon, Vec3i position) {
        return new TrackedWaypoint.Vec3iWaypoint(uuid, icon, position);
    }

    public static TrackedWaypoint setChunk(UUID uuid, Waypoint.Icon icon, ChunkPos chunkPos) {
        return new TrackedWaypoint.ChunkWaypoint(uuid, icon, chunkPos);
    }

    public static TrackedWaypoint setAzimuth(UUID uuid, Waypoint.Icon icon, float angle) {
        return new TrackedWaypoint.AzimuthWaypoint(uuid, icon, angle);
    }

    public static TrackedWaypoint empty(UUID uuid) {
        return new TrackedWaypoint.EmptyWaypoint(uuid);
    }

    public abstract double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickGetter);

    public abstract TrackedWaypoint.PitchDirection pitchDirectionToCamera(
        Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickGetter
    );

    public abstract double distanceSquared(Entity entity);

    public Waypoint.Icon icon() {
        return this.icon;
    }

    static class AzimuthWaypoint extends TrackedWaypoint {
        private float angle;

        public AzimuthWaypoint(UUID uuid, Waypoint.Icon icon, float angle) {
            super(Either.left(uuid), icon, TrackedWaypoint.Type.AZIMUTH);
            this.angle = angle;
        }

        public AzimuthWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, FriendlyByteBuf buffer) {
            super(identifier, icon, TrackedWaypoint.Type.AZIMUTH);
            this.angle = buffer.readFloat();
        }

        @Override
        public void update(TrackedWaypoint waypoint) {
            if (waypoint instanceof TrackedWaypoint.AzimuthWaypoint azimuthWaypoint) {
                this.angle = azimuthWaypoint.angle;
            } else {
                TrackedWaypoint.LOGGER.warn("Unsupported Waypoint update operation: {}", waypoint.getClass());
            }
        }

        @Override
        public void writeContents(ByteBuf buffer) {
            buffer.writeFloat(this.angle);
        }

        @Override
        public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickGetter) {
            return Mth.degreesDifference(camera.yaw(), this.angle * (180.0F / (float)Math.PI));
        }

        @Override
        public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickGetter) {
            double d = projector.projectHorizonToScreen();
            if (d < -1.0) {
                return TrackedWaypoint.PitchDirection.DOWN;
            } else {
                return d > 1.0 ? TrackedWaypoint.PitchDirection.UP : TrackedWaypoint.PitchDirection.NONE;
            }
        }

        @Override
        public double distanceSquared(Entity entity) {
            return Double.POSITIVE_INFINITY;
        }
    }

    public interface Camera {
        float yaw();

        Vec3 position();
    }

    static class ChunkWaypoint extends TrackedWaypoint {
        private ChunkPos chunkPos;

        public ChunkWaypoint(UUID uuid, Waypoint.Icon icon, ChunkPos chunkPos) {
            super(Either.left(uuid), icon, TrackedWaypoint.Type.CHUNK);
            this.chunkPos = chunkPos;
        }

        public ChunkWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, FriendlyByteBuf buffer) {
            super(identifier, icon, TrackedWaypoint.Type.CHUNK);
            this.chunkPos = new ChunkPos(buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void update(TrackedWaypoint waypoint) {
            if (waypoint instanceof TrackedWaypoint.ChunkWaypoint chunkWaypoint) {
                this.chunkPos = chunkWaypoint.chunkPos;
            } else {
                TrackedWaypoint.LOGGER.warn("Unsupported Waypoint update operation: {}", waypoint.getClass());
            }
        }

        @Override
        public void writeContents(ByteBuf buffer) {
            VarInt.write(buffer, this.chunkPos.x);
            VarInt.write(buffer, this.chunkPos.z);
        }

        private Vec3 position(double y) {
            return Vec3.atCenterOf(this.chunkPos.getMiddleBlockPosition((int)y));
        }

        @Override
        public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickGetter) {
            Vec3 vec3 = camera.position();
            Vec3 vec31 = vec3.subtract(this.position(vec3.y())).rotateClockwise90();
            float f = (float)Mth.atan2(vec31.z(), vec31.x()) * (180.0F / (float)Math.PI);
            return Mth.degreesDifference(camera.yaw(), f);
        }

        @Override
        public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickGetter) {
            double d = projector.projectHorizonToScreen();
            if (d < -1.0) {
                return TrackedWaypoint.PitchDirection.DOWN;
            } else {
                return d > 1.0 ? TrackedWaypoint.PitchDirection.UP : TrackedWaypoint.PitchDirection.NONE;
            }
        }

        @Override
        public double distanceSquared(Entity entity) {
            return entity.distanceToSqr(Vec3.atCenterOf(this.chunkPos.getMiddleBlockPosition(entity.getBlockY())));
        }
    }

    static class EmptyWaypoint extends TrackedWaypoint {
        private EmptyWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, FriendlyByteBuf buffer) {
            super(identifier, icon, TrackedWaypoint.Type.EMPTY);
        }

        EmptyWaypoint(UUID uuid) {
            super(Either.left(uuid), Waypoint.Icon.NULL, TrackedWaypoint.Type.EMPTY);
        }

        @Override
        public void update(TrackedWaypoint waypoint) {
        }

        @Override
        public void writeContents(ByteBuf buffer) {
        }

        @Override
        public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickGetter) {
            return Double.NaN;
        }

        @Override
        public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickGetter) {
            return TrackedWaypoint.PitchDirection.NONE;
        }

        @Override
        public double distanceSquared(Entity entity) {
            return Double.POSITIVE_INFINITY;
        }
    }

    public static enum PitchDirection {
        NONE,
        UP,
        DOWN;
    }

    public interface Projector {
        Vec3 projectPointToScreen(Vec3 point);

        double projectHorizonToScreen();
    }

    static enum Type {
        EMPTY(TrackedWaypoint.EmptyWaypoint::new),
        VEC3I(TrackedWaypoint.Vec3iWaypoint::new),
        CHUNK(TrackedWaypoint.ChunkWaypoint::new),
        AZIMUTH(TrackedWaypoint.AzimuthWaypoint::new);

        final TriFunction<Either<UUID, String>, Waypoint.Icon, FriendlyByteBuf, TrackedWaypoint> constructor;

        private Type(final TriFunction<Either<UUID, String>, Waypoint.Icon, FriendlyByteBuf, TrackedWaypoint> constructor) {
            this.constructor = constructor;
        }
    }

    static class Vec3iWaypoint extends TrackedWaypoint {
        private Vec3i vector;

        public Vec3iWaypoint(UUID uuid, Waypoint.Icon icon, Vec3i vector) {
            super(Either.left(uuid), icon, TrackedWaypoint.Type.VEC3I);
            this.vector = vector;
        }

        public Vec3iWaypoint(Either<UUID, String> identifier, Waypoint.Icon icon, FriendlyByteBuf buffer) {
            super(identifier, icon, TrackedWaypoint.Type.VEC3I);
            this.vector = new Vec3i(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void update(TrackedWaypoint waypoint) {
            if (waypoint instanceof TrackedWaypoint.Vec3iWaypoint vec3iWaypoint) {
                this.vector = vec3iWaypoint.vector;
            } else {
                TrackedWaypoint.LOGGER.warn("Unsupported Waypoint update operation: {}", waypoint.getClass());
            }
        }

        @Override
        public void writeContents(ByteBuf buffer) {
            VarInt.write(buffer, this.vector.getX());
            VarInt.write(buffer, this.vector.getY());
            VarInt.write(buffer, this.vector.getZ());
        }

        private Vec3 position(Level level, PartialTickSupplier partialTickGetter) {
            return this.identifier
                .left()
                .map(level::getEntity)
                .map(entity -> entity.blockPosition().distManhattan(this.vector) > 3 ? null : entity.getEyePosition(partialTickGetter.apply(entity)))
                .orElseGet(() -> Vec3.atCenterOf(this.vector));
        }

        @Override
        public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickGetter) {
            Vec3 vec3 = camera.position().subtract(this.position(level, partialTickGetter)).rotateClockwise90();
            float f = (float)Mth.atan2(vec3.z(), vec3.x()) * (180.0F / (float)Math.PI);
            return Mth.degreesDifference(camera.yaw(), f);
        }

        @Override
        public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickGetter) {
            Vec3 vec3 = projector.projectPointToScreen(this.position(level, partialTickGetter));
            boolean flag = vec3.z > 1.0;
            double d = flag ? -vec3.y : vec3.y;
            if (d < -1.0) {
                return TrackedWaypoint.PitchDirection.DOWN;
            } else if (d > 1.0) {
                return TrackedWaypoint.PitchDirection.UP;
            } else {
                if (flag) {
                    if (vec3.y > 0.0) {
                        return TrackedWaypoint.PitchDirection.UP;
                    }

                    if (vec3.y < 0.0) {
                        return TrackedWaypoint.PitchDirection.DOWN;
                    }
                }

                return TrackedWaypoint.PitchDirection.NONE;
            }
        }

        @Override
        public double distanceSquared(Entity entity) {
            return entity.distanceToSqr(Vec3.atCenterOf(this.vector));
        }
    }
}
