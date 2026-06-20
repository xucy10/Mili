package net.minecraft.network.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.PacketListener;
import org.jspecify.annotations.Nullable;

public interface BundlerInfo {
    int BUNDLE_SIZE_LIMIT = 4096;

    static <T extends PacketListener, P extends BundlePacket<? super T>> BundlerInfo createForPacket(
        final PacketType<P> type, final Function<Iterable<Packet<? super T>>, P> bundler, final BundleDelimiterPacket<? super T> delimiterPacket
    ) {
        return new BundlerInfo() {
            @Override
            public void unbundlePacket(Packet<?> packet, Consumer<Packet<?>> consumer) {
                if (packet.type() == type) {
                    P bundlePacket = (P)packet;
                    consumer.accept(delimiterPacket);
                    bundlePacket.subPackets().forEach(consumer);
                    consumer.accept(delimiterPacket);
                } else {
                    consumer.accept(packet);
                }
            }

            @Override
            public BundlerInfo.@Nullable Bundler startPacketBundling(Packet<?> packet) {
                return packet == delimiterPacket ? new BundlerInfo.Bundler() {
                    private final List<Packet<? super T>> bundlePackets = new ArrayList<>();

                    @Override
                    public @Nullable Packet<?> addPacket(Packet<?> subPacket) {
                        if (subPacket == delimiterPacket) {
                            return bundler.apply(this.bundlePackets);
                        } else if (this.bundlePackets.size() >= 4096) {
                            throw new IllegalStateException("Too many packets in a bundle");
                        } else {
                            this.bundlePackets.add((Packet<? super T>)subPacket);
                            return null;
                        }
                    }
                } : null;
            }
        };
    }

    void unbundlePacket(Packet<?> packet, Consumer<Packet<?>> consumer);

    BundlerInfo.@Nullable Bundler startPacketBundling(Packet<?> packet);

    public interface Bundler {
        @Nullable Packet<?> addPacket(Packet<?> subPacket);
    }
}
