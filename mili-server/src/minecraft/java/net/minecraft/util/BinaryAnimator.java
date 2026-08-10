package net.minecraft.util;

public class BinaryAnimator {
    private final int animationLength;
    private final EasingType easing;
    private int ticks;
    private int ticksOld;

    public BinaryAnimator(int animationLength, EasingType easing) {
        this.animationLength = animationLength;
        this.easing = easing;
    }

    public BinaryAnimator(int animationLength) {
        this(animationLength, EasingType.LINEAR);
    }

    public void tick(boolean active) {
        this.ticksOld = this.ticks;
        if (active) {
            if (this.ticks < this.animationLength) {
                this.ticks++;
            }
        } else if (this.ticks > 0) {
            this.ticks--;
        }
    }

    public float getFactor(float partialTick) {
        float f = Mth.lerp(partialTick, (float)this.ticksOld, (float)this.ticks) / this.animationLength;
        return this.easing.apply(f);
    }
}
