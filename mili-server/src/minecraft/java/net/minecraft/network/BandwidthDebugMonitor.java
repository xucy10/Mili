package net.minecraft.network;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.debugchart.LocalSampleLogger;

public class BandwidthDebugMonitor {
    private final AtomicInteger bytesReceived = new AtomicInteger();
    private final LocalSampleLogger bandwidthLogger;

    public BandwidthDebugMonitor(LocalSampleLogger bandwidthLogger) {
        this.bandwidthLogger = bandwidthLogger;
    }

    public void onReceive(int amount) {
        this.bytesReceived.getAndAdd(amount);
    }

    public void tick() {
        this.bandwidthLogger.logSample(this.bytesReceived.getAndSet(0));
    }
}
