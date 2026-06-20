package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

public enum Mirror implements StringRepresentable {
    NONE("none", OctahedralGroup.IDENTITY),
    LEFT_RIGHT("left_right", OctahedralGroup.INVERT_Z),
    FRONT_BACK("front_back", OctahedralGroup.INVERT_X);

    public static final Codec<Mirror> CODEC = StringRepresentable.fromEnum(Mirror::values);
    @Deprecated
    public static final Codec<Mirror> LEGACY_CODEC = ExtraCodecs.legacyEnum(Mirror::valueOf);
    private final String id;
    private final Component symbol;
    private final OctahedralGroup rotation;

    private Mirror(final String id, final OctahedralGroup rotation) {
        this.id = id;
        this.symbol = Component.translatable("mirror." + id);
        this.rotation = rotation;
    }

    public int mirror(int rotation, int rotationCount) {
        int i = rotationCount / 2;
        int i1 = rotation > i ? rotation - rotationCount : rotation;
        switch (this) {
            case LEFT_RIGHT:
                return (i - i1 + rotationCount) % rotationCount;
            case FRONT_BACK:
                return (rotationCount - i1) % rotationCount;
            default:
                return rotation;
        }
    }

    public Rotation getRotation(Direction facing) {
        Direction.Axis axis = facing.getAxis();
        return (this != LEFT_RIGHT || axis != Direction.Axis.Z) && (this != FRONT_BACK || axis != Direction.Axis.X) ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }

    public Direction mirror(Direction facing) {
        if (this == FRONT_BACK && facing.getAxis() == Direction.Axis.X) {
            return facing.getOpposite();
        } else {
            return this == LEFT_RIGHT && facing.getAxis() == Direction.Axis.Z ? facing.getOpposite() : facing;
        }
    }

    public OctahedralGroup rotation() {
        return this.rotation;
    }

    public Component symbol() {
        return this.symbol;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
