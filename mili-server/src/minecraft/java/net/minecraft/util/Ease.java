package net.minecraft.util;

public class Ease {
    public static float inBack(float progress) {
        float f = 1.70158F;
        float f1 = 2.70158F;
        return Mth.square(progress) * (2.70158F * progress - 1.70158F);
    }

    public static float inBounce(float progress) {
        return 1.0F - outBounce(1.0F - progress);
    }

    public static float inCubic(float progress) {
        return Mth.cube(progress);
    }

    public static float inElastic(float progress) {
        if (progress == 0.0F) {
            return 0.0F;
        } else if (progress == 1.0F) {
            return 1.0F;
        } else {
            float f = (float) (Math.PI * 2.0 / 3.0);
            return (float)(-Math.pow(2.0, 10.0 * progress - 10.0) * Math.sin((progress * 10.0 - 10.75) * (float) (Math.PI * 2.0 / 3.0)));
        }
    }

    public static float inExpo(float progress) {
        return progress == 0.0F ? 0.0F : (float)Math.pow(2.0, 10.0 * progress - 10.0);
    }

    public static float inQuart(float progress) {
        return Mth.square(Mth.square(progress));
    }

    public static float inQuint(float progress) {
        return Mth.square(Mth.square(progress)) * progress;
    }

    public static float inSine(float progress) {
        return 1.0F - Mth.cos(progress * (float) (Math.PI / 2));
    }

    public static float inOutBounce(float progress) {
        return progress < 0.5F ? (1.0F - outBounce(1.0F - 2.0F * progress)) / 2.0F : (1.0F + outBounce(2.0F * progress - 1.0F)) / 2.0F;
    }

    public static float inOutCirc(float progress) {
        return progress < 0.5F
            ? (float)((1.0 - Math.sqrt(1.0 - Math.pow(2.0 * progress, 2.0))) / 2.0)
            : (float)((Math.sqrt(1.0 - Math.pow(-2.0 * progress + 2.0, 2.0)) + 1.0) / 2.0);
    }

    public static float inOutCubic(float progress) {
        return progress < 0.5F ? 4.0F * Mth.cube(progress) : (float)(1.0 - Math.pow(-2.0 * progress + 2.0, 3.0) / 2.0);
    }

    public static float inOutQuad(float progress) {
        return progress < 0.5F ? 2.0F * Mth.square(progress) : (float)(1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0);
    }

    public static float inOutQuart(float progress) {
        return progress < 0.5F ? 8.0F * Mth.square(Mth.square(progress)) : (float)(1.0 - Math.pow(-2.0 * progress + 2.0, 4.0) / 2.0);
    }

    public static float inOutQuint(float progress) {
        return progress < 0.5 ? 16.0F * progress * progress * progress * progress * progress : (float)(1.0 - Math.pow(-2.0 * progress + 2.0, 5.0) / 2.0);
    }

    public static float outBounce(float progress) {
        float f = 7.5625F;
        float f1 = 2.75F;
        if (progress < 0.36363637F) {
            return 7.5625F * Mth.square(progress);
        } else if (progress < 0.72727275F) {
            return 7.5625F * Mth.square(progress - 0.54545456F) + 0.75F;
        } else {
            return progress < 0.9090909090909091
                ? 7.5625F * Mth.square(progress - 0.8181818F) + 0.9375F
                : 7.5625F * Mth.square(progress - 0.95454544F) + 0.984375F;
        }
    }

    public static float outElastic(float progress) {
        float f = (float) (Math.PI * 2.0 / 3.0);
        if (progress == 0.0F) {
            return 0.0F;
        } else {
            return progress == 1.0F
                ? 1.0F
                : (float)(Math.pow(2.0, -10.0 * progress) * Math.sin((progress * 10.0 - 0.75) * (float) (Math.PI * 2.0 / 3.0)) + 1.0);
        }
    }

    public static float outExpo(float progress) {
        return progress == 1.0F ? 1.0F : 1.0F - (float)Math.pow(2.0, -10.0 * progress);
    }

    public static float outQuad(float progress) {
        return 1.0F - Mth.square(1.0F - progress);
    }

    public static float outQuint(float progress) {
        return 1.0F - (float)Math.pow(1.0 - progress, 5.0);
    }

    public static float outSine(float progress) {
        return Mth.sin(progress * (float) (Math.PI / 2));
    }

    public static float inOutSine(float progress) {
        return -(Mth.cos((float) Math.PI * progress) - 1.0F) / 2.0F;
    }

    public static float outBack(float progress) {
        float f = 1.70158F;
        float f1 = 2.70158F;
        return 1.0F + 2.70158F * Mth.cube(progress - 1.0F) + 1.70158F * Mth.square(progress - 1.0F);
    }

    public static float outQuart(float progress) {
        return 1.0F - Mth.square(Mth.square(1.0F - progress));
    }

    public static float outCubic(float progress) {
        return 1.0F - Mth.cube(1.0F - progress);
    }

    public static float inOutExpo(float progress) {
        if (progress < 0.5F) {
            return progress == 0.0F ? 0.0F : (float)(Math.pow(2.0, 20.0 * progress - 10.0) / 2.0);
        } else {
            return progress == 1.0F ? 1.0F : (float)((2.0 - Math.pow(2.0, -20.0 * progress + 10.0)) / 2.0);
        }
    }

    public static float inQuad(float progress) {
        return progress * progress;
    }

    public static float outCirc(float progress) {
        return (float)Math.sqrt(1.0F - Mth.square(progress - 1.0F));
    }

    public static float inOutElastic(float progress) {
        float f = (float) Math.PI * 4.0F / 9.0F;
        if (progress == 0.0F) {
            return 0.0F;
        } else if (progress == 1.0F) {
            return 1.0F;
        } else {
            double sin = Math.sin((20.0 * progress - 11.125) * (float) Math.PI * 4.0F / 9.0F);
            return progress < 0.5F
                ? (float)(-(Math.pow(2.0, 20.0 * progress - 10.0) * sin) / 2.0)
                : (float)(Math.pow(2.0, -20.0 * progress + 10.0) * sin / 2.0 + 1.0);
        }
    }

    public static float inCirc(float progress) {
        return (float)(-Math.sqrt(1.0F - progress * progress)) + 1.0F;
    }

    public static float inOutBack(float progress) {
        float f = 1.70158F;
        float f1 = 2.5949094F;
        if (progress < 0.5F) {
            return 4.0F * progress * progress * (7.189819F * progress - 2.5949094F) / 2.0F;
        } else {
            float f2 = 2.0F * progress - 2.0F;
            return (f2 * f2 * (3.5949094F * f2 + 2.5949094F) + 2.0F) / 2.0F;
        }
    }
}
