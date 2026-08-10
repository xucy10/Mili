package com.mojang.math;

import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Vector3f;
import org.joml.Vector3i;

public enum SymmetricGroup3 {
    P123(0, 1, 2),
    P213(1, 0, 2),
    P132(0, 2, 1),
    P312(2, 0, 1),
    P231(1, 2, 0),
    P321(2, 1, 0);

    private final int p0;
    private final int p1;
    private final int p2;
    private final Matrix3fc transformation;
    private static final SymmetricGroup3[][] CAYLEY_TABLE = Util.make(
        () -> {
            SymmetricGroup3[] symmetricGroup3s = values();
            SymmetricGroup3[][] symmetricGroup3s1 = new SymmetricGroup3[symmetricGroup3s.length][symmetricGroup3s.length];

            for (SymmetricGroup3 symmetricGroup3 : symmetricGroup3s) {
                for (SymmetricGroup3 symmetricGroup31 : symmetricGroup3s) {
                    int i = symmetricGroup3.permute(symmetricGroup31.p0);
                    int i1 = symmetricGroup3.permute(symmetricGroup31.p1);
                    int i2 = symmetricGroup3.permute(symmetricGroup31.p2);
                    SymmetricGroup3 symmetricGroup32 = Arrays.stream(symmetricGroup3s)
                        .filter(group -> group.p0 == i && group.p1 == i1 && group.p2 == i2)
                        .findFirst()
                        .get();
                    symmetricGroup3s1[symmetricGroup3.ordinal()][symmetricGroup31.ordinal()] = symmetricGroup32;
                }
            }

            return symmetricGroup3s1;
        }
    );
    private static final SymmetricGroup3[] INVERSE_TABLE = Util.make(
        () -> {
            SymmetricGroup3[] symmetricGroup3s = values();
            return Arrays.stream(symmetricGroup3s)
                .map(group1 -> Arrays.stream(values()).filter(group2 -> group1.compose(group2) == P123).findAny().get())
                .toArray(SymmetricGroup3[]::new);
        }
    );

    private SymmetricGroup3(final int p0, final int p1, final int p2) {
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
        this.transformation = new Matrix3f().zero().set(this.permute(0), 0, 1.0F).set(this.permute(1), 1, 1.0F).set(this.permute(2), 2, 1.0F);
    }

    public SymmetricGroup3 compose(SymmetricGroup3 other) {
        return CAYLEY_TABLE[this.ordinal()][other.ordinal()];
    }

    public SymmetricGroup3 inverse() {
        return INVERSE_TABLE[this.ordinal()];
    }

    public int permute(int pn) {
        return switch (pn) {
            case 0 -> this.p0;
            case 1 -> this.p1;
            case 2 -> this.p2;
            default -> throw new IllegalArgumentException("Must be 0, 1 or 2, but got " + pn);
        };
    }

    public Direction.Axis permuteAxis(Direction.Axis axis) {
        return Direction.Axis.VALUES[this.permute(axis.ordinal())];
    }

    public Vector3f permuteVector(Vector3f vector) {
        float f = vector.get(this.p0);
        float f1 = vector.get(this.p1);
        float f2 = vector.get(this.p2);
        return vector.set(f, f1, f2);
    }

    public Vector3i permuteVector(Vector3i vector) {
        int i = vector.get(this.p0);
        int i1 = vector.get(this.p1);
        int i2 = vector.get(this.p2);
        return vector.set(i, i1, i2);
    }

    public Matrix3fc transformation() {
        return this.transformation;
    }
}
