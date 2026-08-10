package net.minecraft.util.debugchart;

public interface SampleLogger {
    void logFullSample(long[] sample);

    void logSample(long value);

    void logPartialSample(long value, int index);
}
