package net.minecraft.util.debugchart;

import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.util.debug.ServerDebugSubscribers;

public class RemoteSampleLogger extends AbstractSampleLogger {
    private final ServerDebugSubscribers subscribers;
    private final RemoteDebugSampleType sampleType;

    public RemoteSampleLogger(int size, ServerDebugSubscribers subscribers, RemoteDebugSampleType sampleType) {
        this(size, subscribers, sampleType, new long[size]);
    }

    public RemoteSampleLogger(int size, ServerDebugSubscribers subscribers, RemoteDebugSampleType sampleType, long[] defaults) {
        super(size, defaults);
        this.subscribers = subscribers;
        this.sampleType = sampleType;
    }

    @Override
    protected void useSample() {
        if (this.subscribers.hasAnySubscriberFor(this.sampleType.subscription())) {
            this.subscribers.broadcastToAll(this.sampleType.subscription(), new ClientboundDebugSamplePacket((long[])this.sample.clone(), this.sampleType));
        }
    }
}
