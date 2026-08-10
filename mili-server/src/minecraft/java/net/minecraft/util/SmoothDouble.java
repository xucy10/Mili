package net.minecraft.util;

public class SmoothDouble {
    private double targetValue;
    private double remainingValue;
    private double lastAmount;

    public double getNewDeltaValue(double input, double multiplier) {
        this.targetValue += input;
        double d = this.targetValue - this.remainingValue;
        double d1 = Mth.lerp(0.5, this.lastAmount, d);
        double d2 = Math.signum(d);
        if (d2 * d > d2 * this.lastAmount) {
            d = d1;
        }

        this.lastAmount = d1;
        this.remainingValue += d * multiplier;
        return d * multiplier;
    }

    public void reset() {
        this.targetValue = 0.0;
        this.remainingValue = 0.0;
        this.lastAmount = 0.0;
    }
}
