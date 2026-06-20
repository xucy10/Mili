package net.minecraft.util;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ARGB {
    private static final int LINEAR_CHANNEL_DEPTH = 1024;
    private static final short[] SRGB_TO_LINEAR = Util.make(new short[256], shorts -> {
        for (int i = 0; i < shorts.length; i++) {
            float f = i / 255.0F;
            shorts[i] = (short)Math.round(computeSrgbToLinear(f) * 1023.0F);
        }
    });
    private static final byte[] LINEAR_TO_SRGB = Util.make(new byte[1024], bytes -> {
        for (int i = 0; i < bytes.length; i++) {
            float f = i / 1023.0F;
            bytes[i] = (byte)Math.round(computeLinearToSrgb(f) * 255.0F);
        }
    });

    private static float computeSrgbToLinear(float value) {
        return value >= 0.04045F ? (float)Math.pow((value + 0.055) / 1.055, 2.4) : value / 12.92F;
    }

    private static float computeLinearToSrgb(float value) {
        return value >= 0.0031308F ? (float)(1.055 * Math.pow(value, 0.4166666666666667) - 0.055) : 12.92F * value;
    }

    public static float srgbToLinearChannel(int srgb) {
        return SRGB_TO_LINEAR[srgb] / 1023.0F;
    }

    public static int linearToSrgbChannel(float linear) {
        return LINEAR_TO_SRGB[Mth.floor(linear * 1023.0F)] & 0xFF;
    }

    public static int meanLinear(int srgb1, int srgb2, int srgb3, int srgb4) {
        return color(
            (alpha(srgb1) + alpha(srgb2) + alpha(srgb3) + alpha(srgb4)) / 4,
            linearChannelMean(red(srgb1), red(srgb2), red(srgb3), red(srgb4)),
            linearChannelMean(green(srgb1), green(srgb2), green(srgb3), green(srgb4)),
            linearChannelMean(blue(srgb1), blue(srgb2), blue(srgb3), blue(srgb4))
        );
    }

    private static int linearChannelMean(int c1, int c2, int c3, int c4) {
        int i = (SRGB_TO_LINEAR[c1] + SRGB_TO_LINEAR[c2] + SRGB_TO_LINEAR[c3] + SRGB_TO_LINEAR[c4]) / 4;
        return LINEAR_TO_SRGB[i] & 0xFF;
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int color(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    public static int color(int red, int green, int blue) {
        return color(255, red, green, blue);
    }

    public static int color(Vec3 color) {
        return color(as8BitChannel((float)color.x()), as8BitChannel((float)color.y()), as8BitChannel((float)color.z()));
    }

    public static int multiply(int color1, int color2) {
        if (color1 == -1) {
            return color2;
        } else {
            return color2 == -1
                ? color1
                : color(
                    alpha(color1) * alpha(color2) / 255,
                    red(color1) * red(color2) / 255,
                    green(color1) * green(color2) / 255,
                    blue(color1) * blue(color2) / 255
                );
        }
    }

    public static int addRgb(int color1, int color2) {
        return color(
            alpha(color1), Math.min(red(color1) + red(color2), 255), Math.min(green(color1) + green(color2), 255), Math.min(blue(color1) + blue(color2), 255)
        );
    }

    public static int subtractRgb(int color1, int color2) {
        return color(
            alpha(color1), Math.max(red(color1) - red(color2), 0), Math.max(green(color1) - green(color2), 0), Math.max(blue(color1) - blue(color2), 0)
        );
    }

    public static int multiplyAlpha(int color, float alphaMultiplier) {
        if (color == 0 || alphaMultiplier <= 0.0F) {
            return 0;
        } else {
            return alphaMultiplier >= 1.0F ? color : color(alphaFloat(color) * alphaMultiplier, color);
        }
    }

    public static int scaleRGB(int color, float scale) {
        return scaleRGB(color, scale, scale, scale);
    }

    public static int scaleRGB(int color, float redScale, float greenScale, float blueScale) {
        return color(
            alpha(color),
            Math.clamp((long)((int)(red(color) * redScale)), 0, 255),
            Math.clamp((long)((int)(green(color) * greenScale)), 0, 255),
            Math.clamp((long)((int)(blue(color) * blueScale)), 0, 255)
        );
    }

    public static int scaleRGB(int color, int scale) {
        return color(
            alpha(color),
            Math.clamp((long)red(color) * scale / 255L, 0, 255),
            Math.clamp((long)green(color) * scale / 255L, 0, 255),
            Math.clamp((long)blue(color) * scale / 255L, 0, 255)
        );
    }

    public static int greyscale(int color) {
        int i = (int)(red(color) * 0.3F + green(color) * 0.59F + blue(color) * 0.11F);
        return color(alpha(color), i, i, i);
    }

    public static int alphaBlend(int destination, int source) {
        int i = alpha(destination);
        int i1 = alpha(source);
        if (i1 == 255) {
            return source;
        } else if (i1 == 0) {
            return destination;
        } else {
            int i2 = i1 + i * (255 - i1) / 255;
            return color(
                i2,
                alphaBlendChannel(i2, i1, red(destination), red(source)),
                alphaBlendChannel(i2, i1, green(destination), green(source)),
                alphaBlendChannel(i2, i1, blue(destination), blue(source))
            );
        }
    }

    private static int alphaBlendChannel(int resultAlpha, int sourceAlpha, int destination, int source) {
        return (source * sourceAlpha + destination * (resultAlpha - sourceAlpha)) / resultAlpha;
    }

    public static int srgbLerp(float delta, int color1, int color2) {
        int i = Mth.lerpInt(delta, alpha(color1), alpha(color2));
        int i1 = Mth.lerpInt(delta, red(color1), red(color2));
        int i2 = Mth.lerpInt(delta, green(color1), green(color2));
        int i3 = Mth.lerpInt(delta, blue(color1), blue(color2));
        return color(i, i1, i2, i3);
    }

    public static int linearLerp(float delta, int color1, int color2) {
        return color(
            Mth.lerpInt(delta, alpha(color1), alpha(color2)),
            LINEAR_TO_SRGB[Mth.lerpInt(delta, SRGB_TO_LINEAR[red(color1)], SRGB_TO_LINEAR[red(color2)])] & 0xFF,
            LINEAR_TO_SRGB[Mth.lerpInt(delta, SRGB_TO_LINEAR[green(color1)], SRGB_TO_LINEAR[green(color2)])] & 0xFF,
            LINEAR_TO_SRGB[Mth.lerpInt(delta, SRGB_TO_LINEAR[blue(color1)], SRGB_TO_LINEAR[blue(color2)])] & 0xFF
        );
    }

    public static int opaque(int color) {
        return color | 0xFF000000;
    }

    public static int transparent(int color) {
        return color & 16777215;
    }

    public static int color(int alpha, int color) {
        return alpha << 24 | color & 16777215;
    }

    public static int color(float alpha, int color) {
        return as8BitChannel(alpha) << 24 | color & 16777215;
    }

    public static int white(float alpha) {
        return as8BitChannel(alpha) << 24 | 16777215;
    }

    public static int white(int alpha) {
        return alpha << 24 | 16777215;
    }

    public static int black(float alpha) {
        return as8BitChannel(alpha) << 24;
    }

    public static int black(int alpha) {
        return alpha << 24;
    }

    public static int colorFromFloat(float alpha, float red, float green, float blue) {
        return color(as8BitChannel(alpha), as8BitChannel(red), as8BitChannel(green), as8BitChannel(blue));
    }

    public static Vector3f vector3fFromRGB24(int color) {
        return new Vector3f(redFloat(color), greenFloat(color), blueFloat(color));
    }

    public static Vector4f vector4fFromARGB32(int color) {
        return new Vector4f(redFloat(color), greenFloat(color), blueFloat(color), alphaFloat(color));
    }

    public static int average(int color1, int color2) {
        return color(
            (alpha(color1) + alpha(color2)) / 2, (red(color1) + red(color2)) / 2, (green(color1) + green(color2)) / 2, (blue(color1) + blue(color2)) / 2
        );
    }

    public static int as8BitChannel(float value) {
        return Mth.floor(value * 255.0F);
    }

    public static float alphaFloat(int color) {
        return from8BitChannel(alpha(color));
    }

    public static float redFloat(int color) {
        return from8BitChannel(red(color));
    }

    public static float greenFloat(int color) {
        return from8BitChannel(green(color));
    }

    public static float blueFloat(int color) {
        return from8BitChannel(blue(color));
    }

    private static float from8BitChannel(int value) {
        return value / 255.0F;
    }

    public static int toABGR(int color) {
        return color & -16711936 | (color & 0xFF0000) >> 16 | (color & 0xFF) << 16;
    }

    public static int fromABGR(int color) {
        return toABGR(color);
    }

    public static int setBrightness(int color, float brightness) {
        int i = red(color);
        int i1 = green(color);
        int i2 = blue(color);
        int i3 = alpha(color);
        int max = Math.max(Math.max(i, i1), i2);
        int min = Math.min(Math.min(i, i1), i2);
        float f = max - min;
        float f1;
        if (max != 0) {
            f1 = f / max;
        } else {
            f1 = 0.0F;
        }

        float f2;
        if (f1 == 0.0F) {
            f2 = 0.0F;
        } else {
            float f3 = (max - i) / f;
            float f4 = (max - i1) / f;
            float f5 = (max - i2) / f;
            if (i == max) {
                f2 = f5 - f4;
            } else if (i1 == max) {
                f2 = 2.0F + f3 - f5;
            } else {
                f2 = 4.0F + f4 - f3;
            }

            f2 /= 6.0F;
            if (f2 < 0.0F) {
                f2++;
            }
        }

        if (f1 == 0.0F) {
            i = i1 = i2 = Math.round(brightness * 255.0F);
            return color(i3, i, i1, i2);
        } else {
            float f3x = (f2 - (float)Math.floor(f2)) * 6.0F;
            float f4x = f3x - (float)Math.floor(f3x);
            float f5x = brightness * (1.0F - f1);
            float f6 = brightness * (1.0F - f1 * f4x);
            float f7 = brightness * (1.0F - f1 * (1.0F - f4x));
            switch ((int)f3x) {
                case 0:
                    i = Math.round(brightness * 255.0F);
                    i1 = Math.round(f7 * 255.0F);
                    i2 = Math.round(f5x * 255.0F);
                    break;
                case 1:
                    i = Math.round(f6 * 255.0F);
                    i1 = Math.round(brightness * 255.0F);
                    i2 = Math.round(f5x * 255.0F);
                    break;
                case 2:
                    i = Math.round(f5x * 255.0F);
                    i1 = Math.round(brightness * 255.0F);
                    i2 = Math.round(f7 * 255.0F);
                    break;
                case 3:
                    i = Math.round(f5x * 255.0F);
                    i1 = Math.round(f6 * 255.0F);
                    i2 = Math.round(brightness * 255.0F);
                    break;
                case 4:
                    i = Math.round(f7 * 255.0F);
                    i1 = Math.round(f5x * 255.0F);
                    i2 = Math.round(brightness * 255.0F);
                    break;
                case 5:
                    i = Math.round(brightness * 255.0F);
                    i1 = Math.round(f5x * 255.0F);
                    i2 = Math.round(f6 * 255.0F);
            }

            return color(i3, i, i1, i2);
        }
    }
}
