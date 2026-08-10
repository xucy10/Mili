package com.mojang.math;

import org.joml.Math;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

public record GivensParameters(float sinHalf, float cosHalf) {
    public static GivensParameters fromUnnormalized(float sinHalf, float cosHalf) {
        float f = Math.invsqrt(sinHalf * sinHalf + cosHalf * cosHalf);
        return new GivensParameters(f * sinHalf, f * cosHalf);
    }

    public static GivensParameters fromPositiveAngle(float angle) {
        float f = Math.sin(angle / 2.0F);
        float f1 = Math.cosFromSin(f, angle / 2.0F);
        return new GivensParameters(f, f1);
    }

    public GivensParameters inverse() {
        return new GivensParameters(-this.sinHalf, this.cosHalf);
    }

    public Quaternionf aroundX(Quaternionf quaternion) {
        return quaternion.set(this.sinHalf, 0.0F, 0.0F, this.cosHalf);
    }

    public Quaternionf aroundY(Quaternionf quaternion) {
        return quaternion.set(0.0F, this.sinHalf, 0.0F, this.cosHalf);
    }

    public Quaternionf aroundZ(Quaternionf quaternion) {
        return quaternion.set(0.0F, 0.0F, this.sinHalf, this.cosHalf);
    }

    public float cos() {
        return this.cosHalf * this.cosHalf - this.sinHalf * this.sinHalf;
    }

    public float sin() {
        return 2.0F * this.sinHalf * this.cosHalf;
    }

    public Matrix3f aroundX(Matrix3f matrix) {
        matrix.m01 = 0.0F;
        matrix.m02 = 0.0F;
        matrix.m10 = 0.0F;
        matrix.m20 = 0.0F;
        float f = this.cos();
        float f1 = this.sin();
        matrix.m11 = f;
        matrix.m22 = f;
        matrix.m12 = f1;
        matrix.m21 = -f1;
        matrix.m00 = 1.0F;
        return matrix;
    }

    public Matrix3f aroundY(Matrix3f matrix) {
        matrix.m01 = 0.0F;
        matrix.m10 = 0.0F;
        matrix.m12 = 0.0F;
        matrix.m21 = 0.0F;
        float f = this.cos();
        float f1 = this.sin();
        matrix.m00 = f;
        matrix.m22 = f;
        matrix.m02 = -f1;
        matrix.m20 = f1;
        matrix.m11 = 1.0F;
        return matrix;
    }

    public Matrix3f aroundZ(Matrix3f matrix) {
        matrix.m02 = 0.0F;
        matrix.m12 = 0.0F;
        matrix.m20 = 0.0F;
        matrix.m21 = 0.0F;
        float f = this.cos();
        float f1 = this.sin();
        matrix.m00 = f;
        matrix.m11 = f;
        matrix.m01 = f1;
        matrix.m10 = -f1;
        matrix.m22 = 1.0F;
        return matrix;
    }
}
