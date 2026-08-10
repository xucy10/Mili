package net.minecraft.world.entity.boss.enderdragon;

import java.util.Arrays;
import net.minecraft.util.Mth;

public class DragonFlightHistory {
    public static final int LENGTH = 64;
    private static final int MASK = 63;
    private final DragonFlightHistory.Sample[] samples = new DragonFlightHistory.Sample[64];
    private int head = -1;

    public DragonFlightHistory() {
        Arrays.fill(this.samples, new DragonFlightHistory.Sample(0.0, 0.0F));
    }

    public void copyFrom(DragonFlightHistory other) {
        System.arraycopy(other.samples, 0, this.samples, 0, 64);
        this.head = other.head;
    }

    public void record(double y, float yRot) {
        DragonFlightHistory.Sample sample = new DragonFlightHistory.Sample(y, yRot);
        if (this.head < 0) {
            Arrays.fill(this.samples, sample);
        }

        if (++this.head == 64) {
            this.head = 0;
        }

        this.samples[this.head] = sample;
    }

    public DragonFlightHistory.Sample get(int index) {
        return this.samples[this.head - index & 63];
    }

    public DragonFlightHistory.Sample get(int index, float partialTick) {
        DragonFlightHistory.Sample sample = this.get(index);
        DragonFlightHistory.Sample sample1 = this.get(index + 1);
        return new DragonFlightHistory.Sample(Mth.lerp((double)partialTick, sample1.y, sample.y), Mth.rotLerp(partialTick, sample1.yRot, sample.yRot));
    }

    public record Sample(double y, float yRot) {
    }
}
