package com.mojang.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import org.apache.commons.lang3.tuple.Triple;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class Transformation {
    private final Matrix4fc matrix;
    public static final Codec<Transformation> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ExtraCodecs.VECTOR3F.fieldOf("translation").forGetter(transformation -> transformation.translation),
                ExtraCodecs.QUATERNIONF.fieldOf("left_rotation").forGetter(transformation -> transformation.leftRotation),
                ExtraCodecs.VECTOR3F.fieldOf("scale").forGetter(transformation -> transformation.scale),
                ExtraCodecs.QUATERNIONF.fieldOf("right_rotation").forGetter(transformation -> transformation.rightRotation)
            )
            .apply(instance, Transformation::new)
    );
    public static final Codec<Transformation> EXTENDED_CODEC = Codec.withAlternative(
        CODEC, ExtraCodecs.MATRIX4F.xmap(Transformation::new, Transformation::getMatrix)
    );
    private boolean decomposed;
    private @Nullable Vector3fc translation;
    private @Nullable Quaternionfc leftRotation;
    private @Nullable Vector3fc scale;
    private @Nullable Quaternionfc rightRotation;
    private static final Transformation IDENTITY = Util.make(() -> {
        Transformation transformation = new Transformation(new Matrix4f());
        transformation.translation = new Vector3f();
        transformation.leftRotation = new Quaternionf();
        transformation.scale = new Vector3f(1.0F, 1.0F, 1.0F);
        transformation.rightRotation = new Quaternionf();
        transformation.decomposed = true;
        return transformation;
    });

    public Transformation(@Nullable Matrix4fc matrix) {
        if (matrix == null) {
            this.matrix = new Matrix4f();
        } else {
            this.matrix = matrix;
        }
    }

    public Transformation(@Nullable Vector3fc translation, @Nullable Quaternionfc leftRotation, @Nullable Vector3fc scale, @Nullable Quaternionfc rightRotation) {
        this.matrix = compose(translation, leftRotation, scale, rightRotation);
        this.translation = (Vector3fc)(translation != null ? translation : new Vector3f());
        this.leftRotation = (Quaternionfc)(leftRotation != null ? leftRotation : new Quaternionf());
        this.scale = (Vector3fc)(scale != null ? scale : new Vector3f(1.0F, 1.0F, 1.0F));
        this.rightRotation = (Quaternionfc)(rightRotation != null ? rightRotation : new Quaternionf());
        this.decomposed = true;
    }

    public static Transformation identity() {
        return IDENTITY;
    }

    public Transformation compose(Transformation other) {
        Matrix4f matrixCopy = this.getMatrixCopy();
        matrixCopy.mul(other.getMatrix());
        return new Transformation(matrixCopy);
    }

    public @Nullable Transformation inverse() {
        if (this == IDENTITY) {
            return this;
        } else {
            Matrix4f matrix4f = this.getMatrixCopy().invertAffine();
            return matrix4f.isFinite() ? new Transformation(matrix4f) : null;
        }
    }

    private void ensureDecomposed() {
        if (!this.decomposed) {
            float f = 1.0F / this.matrix.m33();
            Triple<Quaternionf, Vector3f, Quaternionf> triple = MatrixUtil.svdDecompose(new Matrix3f(this.matrix).scale(f));
            this.translation = this.matrix.getTranslation(new Vector3f()).mul(f);
            this.leftRotation = new Quaternionf(triple.getLeft());
            this.scale = new Vector3f(triple.getMiddle());
            this.rightRotation = new Quaternionf(triple.getRight());
            this.decomposed = true;
        }
    }

    private static Matrix4f compose(
        @Nullable Vector3fc translation, @Nullable Quaternionfc leftRotation, @Nullable Vector3fc scale, @Nullable Quaternionfc rightRotation
    ) {
        Matrix4f matrix4f = new Matrix4f();
        if (translation != null) {
            matrix4f.translation(translation);
        }

        if (leftRotation != null) {
            matrix4f.rotate(leftRotation);
        }

        if (scale != null) {
            matrix4f.scale(scale);
        }

        if (rightRotation != null) {
            matrix4f.rotate(rightRotation);
        }

        return matrix4f;
    }

    public Matrix4fc getMatrix() {
        return this.matrix;
    }

    public Matrix4f getMatrixCopy() {
        return new Matrix4f(this.matrix);
    }

    public Vector3fc getTranslation() {
        this.ensureDecomposed();
        return this.translation;
    }

    public Quaternionfc getLeftRotation() {
        this.ensureDecomposed();
        return this.leftRotation;
    }

    public Vector3fc getScale() {
        this.ensureDecomposed();
        return this.scale;
    }

    public Quaternionfc getRightRotation() {
        this.ensureDecomposed();
        return this.rightRotation;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            Transformation transformation = (Transformation)other;
            return Objects.equals(this.matrix, transformation.matrix);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.matrix);
    }

    public Transformation slerp(Transformation transformation, float delta) {
        return new Transformation(
            this.getTranslation().lerp(transformation.getTranslation(), delta, new Vector3f()),
            this.getLeftRotation().slerp(transformation.getLeftRotation(), delta, new Quaternionf()),
            this.getScale().lerp(transformation.getScale(), delta, new Vector3f()),
            this.getRightRotation().slerp(transformation.getRightRotation(), delta, new Quaternionf())
        );
    }
}
