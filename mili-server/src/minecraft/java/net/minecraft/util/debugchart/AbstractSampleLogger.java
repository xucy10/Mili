package net.minecraft.util.debugchart;

public abstract class AbstractSampleLogger implements SampleLogger {
    protected final long[] defaults;
    protected final long[] sample;

    protected AbstractSampleLogger(int size, long[] defaults) {
        if (defaults.length != size) {
            throw new IllegalArgumentException("defaults have incorrect length of " + defaults.length);
        } else {
            this.sample = new long[size];
            this.defaults = defaults;
        }
    }

    @Override
    public void logFullSample(long[] sample) {
        System.arraycopy(sample, 0, this.sample, 0, sample.length);
        this.useSample();
        this.resetSample();
    }

    @Override
    public void logSample(long value) {
        this.sample[0] = value;
        this.useSample();
        this.resetSample();
    }

    @Override
    public void logPartialSample(long value, int index) {
        if (index >= 1 && index < this.sample.length) {
            this.sample[index] = value;
        } else {
            throw new IndexOutOfBoundsException(index + " out of bounds for dimensions " + this.sample.length);
        }
    }

    protected abstract void useSample();

    protected void resetSample() {
        System.arraycopy(this.defaults, 0, this.sample, 0, this.defaults.length);
    }
}
