package net.minecraft.network;

import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Connection extends SimpleChannelInboundHandler<Packet<?>> {
    private static final float AVERAGE_PACKETS_SMOOTHING = 0.75F;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Marker ROOT_MARKER = MarkerFactory.getMarker("NETWORK");
    public static final Marker PACKET_MARKER = Util.make(MarkerFactory.getMarker("NETWORK_PACKETS"), marker -> marker.add(ROOT_MARKER));
    public static final Marker PACKET_RECEIVED_MARKER = Util.make(MarkerFactory.getMarker("PACKET_RECEIVED"), marker -> marker.add(PACKET_MARKER));
    public static final Marker PACKET_SENT_MARKER = Util.make(MarkerFactory.getMarker("PACKET_SENT"), marker -> marker.add(PACKET_MARKER));
    private static final ProtocolInfo<ServerHandshakePacketListener> INITIAL_PROTOCOL = HandshakeProtocols.SERVERBOUND;
    private final PacketFlow receiving;
    private volatile boolean sendLoginDisconnect = true;
    private final Queue<WrappedConsumer> pendingActions = new ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<>(); // Paper - Optimize network // Folia - region threading - connection fixes
    public Channel channel;
    public SocketAddress address;
    // Spigot start
    public java.util.UUID spoofedUUID;
    public com.mojang.authlib.properties.Property[] spoofedProfile;
    public boolean preparing = true;
    // Spigot end
    private volatile @Nullable PacketListener disconnectListener;
    private volatile @Nullable PacketListener packetListener;
    private @Nullable DisconnectionDetails disconnectionDetails;
    private boolean encrypted;
    private final java.util.concurrent.atomic.AtomicBoolean disconnectionHandled = new java.util.concurrent.atomic.AtomicBoolean(false); // Folia - region threading - may be called concurrently during configuration stage
    private int receivedPackets;
    private int sentPackets;
    private float averageReceivedPackets;
    private float averageSentPackets;
    private int tickCount;
    private boolean handlingFault;
    private volatile @Nullable DisconnectionDetails delayedDisconnect;
    @Nullable BandwidthDebugMonitor bandwidthDebugMonitor;
    public String hostname = ""; // CraftBukkit - add field
    // Paper start - NetworkClient implementation
    public int protocolVersion;
    public java.net.InetSocketAddress virtualHost;
    private static boolean enableExplicitFlush = Boolean.getBoolean("paper.explicit-flush"); // Paper - Disable explicit network manager flushing
    // Paper end
    // Paper start - add utility methods
    public final net.minecraft.server.level.ServerPlayer getPlayer() {
        if (this.packetListener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl impl) {
            return impl.player;
        } else if (this.packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl impl) {
            return null;
        }
        return null;
    }
    // Paper end - add utility methods
    // Paper start - packet limiter
    protected final Object PACKET_LIMIT_LOCK = new Object();
    protected final io.papermc.paper.util.@Nullable IntervalledCounter allPacketCounts = io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.allPackets.isEnabled() ? new io.papermc.paper.util.IntervalledCounter(
        (long)(io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.allPackets.interval() * 1.0e9)
    ) : null;
    protected final java.util.Map<Class<? extends net.minecraft.network.protocol.Packet<?>>, io.papermc.paper.util.IntervalledCounter> packetSpecificLimits = new java.util.HashMap<>();

    private boolean stopReadingPackets;
    private void killForPacketSpam() {
        this.sendPacket(new ClientboundDisconnectPacket(io.papermc.paper.adventure.PaperAdventure.asVanilla(io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.kickMessage)), PacketSendListener.thenRun(() -> {
            this.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.kickMessage));
        }), true);
        this.setReadOnly();
        this.stopReadingPackets = true;
    }
    // Paper end - packet limiter
    @Nullable public SocketAddress haProxyAddress; // Paper - Add API to get player's proxy address
    public java.util.@Nullable Optional<net.minecraft.network.chat.Component> legacySavedLoginEventResultOverride; // Paper - playerloginevent
    public boolean handledLegacyLoginEvent; // Paper - playerloginevent
    public net.minecraft.server.level.@Nullable ServerPlayer savedPlayerForLegacyEvents; // Paper - playerloginevent & PlayerSpawnLocationEvent
    public org.bukkit.event.player.PlayerResourcePackStatusEvent.@Nullable Status resourcePackStatus; // Paper
    // Paper start - Optimize network
    public boolean isPending = true;
    public boolean queueImmunity;
    // Paper end - Optimize network

    public Connection(PacketFlow receiving) {
        this.receiving = receiving;
    }

    // Folia start - region threading
    private volatile boolean becomeActive;

    public boolean becomeActive() {
        return this.becomeActive;
    }

    private static record DisconnectReq(DisconnectionDetails details) {}

    private final ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<DisconnectReq> disconnectReqs =
            new ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<>();

    /**
     * Safely disconnects the connection while possibly on another thread. Note: This call will not block, even if on the
     * same thread that could disconnect.
     */
    public final void disconnectSafely(DisconnectionDetails details) {
        this.disconnectReqs.add(new DisconnectReq(details));
        // We can't halt packet processing here because a plugin could cancel a kick request.
    }

    /**
     * Safely disconnects the connection while possibly on another thread. Note: This call will not block, even if on the
     * same thread that could disconnect.
     */
    public final void disconnectSafely(Component disconnectReason, io.papermc.paper.connection.DisconnectionReason cause) {
        this.disconnectReqs.add(new DisconnectReq(new DisconnectionDetails(disconnectReason, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.ofNullable(cause))));
        // We can't halt packet processing here because a plugin could cancel a kick request.
    }

    public final boolean isPlayerConnected() {
        return this.packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl;
    }
    // Folia end - region threading

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.channel = ctx.channel();
        this.address = this.channel.remoteAddress();
        this.preparing = false; // Spigot
        if (this.delayedDisconnect != null) {
            this.disconnect(this.delayedDisconnect);
        }
        this.becomeActive = true; // Folia - region threading
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        this.disconnect(Component.translatable("disconnect.endOfStream"));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable exception) {
        // Paper start - Handle large packets disconnecting client
        if (exception instanceof io.netty.handler.codec.EncoderException && exception.getCause() instanceof PacketEncoder.PacketTooLargeException packetTooLargeException) {
            final Packet<?> packet = packetTooLargeException.getPacket();
            if (packet.packetTooLarge(this)) {
                ProtocolSwapHandler.handleOutboundTerminalPacket(ctx, packet);
                return;
            } else if (packet.isSkippable()) {
                Connection.LOGGER.debug("Skipping packet due to errors", exception.getCause());
                ProtocolSwapHandler.handleOutboundTerminalPacket(ctx, packet);
                return;
            } else {
                exception = exception.getCause();
            }
        }
        // Paper end - Handle large packets disconnecting client
        if (exception instanceof SkipPacketException) {
            LOGGER.debug("Skipping packet due to errors", exception.getCause());
        } else {
            boolean flag = !this.handlingFault;
            this.handlingFault = true;
            if (this.channel.isOpen()) {
                net.minecraft.server.level.ServerPlayer player = this.getPlayer(); // Paper - Add API for quit reason
                if (exception instanceof TimeoutException) {
                    LOGGER.debug("Timeout", exception);
                    if (player != null) player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.TIMED_OUT; // Paper - Add API for quit reason
                    this.disconnect(Component.translatable("disconnect.timeout"));
                } else {
                    Component component = Component.translatable("disconnect.genericReason", "Internal Exception: " + exception);
                    PacketListener packetListener = this.packetListener;
                    DisconnectionDetails disconnectionDetails;
                    if (packetListener != null) {
                        disconnectionDetails = packetListener.createDisconnectionInfo(component, exception);
                    } else {
                        disconnectionDetails = new DisconnectionDetails(component);
                    }

                    if (player != null) player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.ERRONEOUS_STATE; // Paper - Add API for quit reason
                    if (flag) {
                        LOGGER.debug("Failed to sent packet", exception);
                        boolean doesDisconnectExist = this.packetListener.protocol() != ConnectionProtocol.STATUS && this.packetListener.protocol() != ConnectionProtocol.HANDSHAKING; // Paper
                        if (this.getSending() == PacketFlow.CLIENTBOUND && doesDisconnectExist) { // Paper
                            Packet<?> packet = (Packet<?>)(this.sendLoginDisconnect
                                ? new ClientboundLoginDisconnectPacket(component)
                                : new ClientboundDisconnectPacket(component));
                            this.send(packet, PacketSendListener.thenRun(() -> this.disconnect(disconnectionDetails)));
                        } else {
                            this.disconnect(disconnectionDetails);
                        }

                        this.setReadOnly();
                    } else {
                        LOGGER.debug("Double fault", exception);
                        this.disconnect(disconnectionDetails);
                    }
                }
            }
        }
        if (net.minecraft.server.MinecraftServer.getServer().isDebugging()) io.papermc.paper.util.TraceUtil.printStackTrace(exception); // Spigot // Paper
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet<?> packet) {
        if (this.channel.isOpen()) {
            PacketListener packetListener = this.packetListener;
            if (packetListener == null) {
                throw new IllegalStateException("Received a packet before the packet listener was initialized");
            } else {
                // Paper start - packet limiter
                if (this.stopReadingPackets) {
                    return;
                }
                if (!me.earthme.luminol.config.modules.misc.PaperPacketLimiterConfig.forceDisable && (this.allPacketCounts != null || // Luminol - Add config to force disable the packet limiter of Paper
                    io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.overrides.containsKey(packet.getClass()))) { // Luminol - Add config to force disable the packet limiter of Paper
                    long time = System.nanoTime();
                    synchronized (PACKET_LIMIT_LOCK) {
                        if (this.allPacketCounts != null) {
                            this.allPacketCounts.updateAndAdd(1, time);
                            if (this.allPacketCounts.getRate() >= io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.allPackets.maxPacketRate()) {
                                this.killForPacketSpam();
                                return;
                            }
                        }

                        for (Class<?> check = packet.getClass(); check != Object.class; check = check.getSuperclass()) {
                            io.papermc.paper.configuration.GlobalConfiguration.PacketLimiter.PacketLimit packetSpecificLimit =
                                io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.overrides.get(check);
                            if (packetSpecificLimit == null || !packetSpecificLimit.isEnabled()) {
                                continue;
                            }
                            io.papermc.paper.util.IntervalledCounter counter = this.packetSpecificLimits.computeIfAbsent((Class)check, (clazz) -> {
                                return new io.papermc.paper.util.IntervalledCounter((long)(packetSpecificLimit.interval() * 1.0e9));
                            });
                            counter.updateAndAdd(1, time);
                            if (counter.getRate() >= packetSpecificLimit.maxPacketRate()) {
                                switch (packetSpecificLimit.action()) {
                                    case DROP:
                                        return;
                                    case KICK:
                                        String deobfedPacketName = io.papermc.paper.util.ObfHelper.INSTANCE.deobfClassName(check.getName());

                                        String playerName;
                                        if (this.packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl impl) {
                                            playerName = impl.getOwner().name();
                                        } else {
                                            playerName = this.getLoggableAddress(net.minecraft.server.MinecraftServer.getServer().logIPs());
                                        }

                                        Connection.LOGGER.warn("{} kicked for packet spamming: {}", playerName, deobfedPacketName.substring(deobfedPacketName.lastIndexOf(".") + 1));
                                        this.killForPacketSpam();
                                        return;
                                }
                            }
                        }
                    }
                }
                // Paper end - packet limiter
                if (packetListener.shouldHandleMessage(packet)) {
                    try {
                        genericsFtw(packet, packetListener);
                    } catch (RunningOnDifferentThreadException var5) {
                    } catch (io.papermc.paper.util.ServerStopRejectedExecutionException ignored) { // Paper - do not prematurely disconnect players on stop
                    } catch (RejectedExecutionException var6) {
                        this.disconnect(Component.translatable("multiplayer.disconnect.server_shutdown"));
                    } catch (ClassCastException var7) {
                        LOGGER.error("Received {} that couldn't be processed", packet.getClass(), var7);
                        this.disconnect(Component.translatable("multiplayer.disconnect.invalid_packet"));
                    }

                    this.receivedPackets++;
                }
            }
        }
    }

    private static <T extends PacketListener> void genericsFtw(Packet<T> packet, PacketListener listener) {
        packet.handle((T)listener);
    }

    private void validateListener(ProtocolInfo<?> protocolInfo, PacketListener packetListener) {
        Objects.requireNonNull(packetListener, "packetListener");
        PacketFlow packetFlow = packetListener.flow();
        if (packetFlow != this.receiving) {
            throw new IllegalStateException("Trying to set listener for wrong side: connection is " + this.receiving + ", but listener is " + packetFlow);
        } else {
            ConnectionProtocol connectionProtocol = packetListener.protocol();
            if (protocolInfo.id() != connectionProtocol) {
                throw new IllegalStateException("Listener protocol (" + connectionProtocol + ") does not match requested one " + protocolInfo);
            }
        }
    }

    private static void syncAfterConfigurationChange(ChannelFuture future) {
        try {
            future.syncUninterruptibly();
        } catch (Exception var2) {
            if (var2 instanceof ClosedChannelException) {
                LOGGER.info("Connection closed during protocol change");
            } else {
                throw var2;
            }
        }
    }

    // Luminol start - async protocol switcher
    public <T extends PacketListener> void setupInboundProtocolAsync(
            ProtocolInfo<T> protocol,
            T packetListener,
            @Nullable Runnable callback,
            boolean resumeAutoReading
    ) {
        this.validateListener(protocol, packetListener);
        if (protocol.flow() != this.getReceiving()) {
            throw new IllegalStateException("Invalid inbound protocol: " + protocol.id());
        } else {
            this.packetListener = packetListener;
            this.disconnectListener = null;

            UnconfiguredPipelineHandler.InboundConfigurationTask configMessage = UnconfiguredPipelineHandler.setupInboundProtocol(protocol);
            BundlerInfo bundlerInfo = protocol.bundlerInfo();
            if (bundlerInfo != null) {
                PacketBundlePacker newBundler = new PacketBundlePacker(bundlerInfo);
                configMessage = configMessage.andThen(context -> context.pipeline().addAfter("decoder", "bundler", newBundler));
            }

            // Here we could execute it in event loop async to prevent waiting io task on main
            // stop reading new packets to prevent some packets came into pipeline too early
            this.channel.config().setAutoRead(false);

            // do our configuration task
            final UnconfiguredPipelineHandler.InboundConfigurationTask finalInboundConfigurationTask = configMessage;
            Runnable toExecute = () -> this.channel.writeAndFlush(finalInboundConfigurationTask).addListener(future -> {
                try {
                    if (future.isSuccess()) {
                        if (callback != null) callback.run(); // retire callback if there have one
                        return;
                    }

                    final Throwable ex = future.cause();

                    // here we process our exceptions like that blocking one
                    if (ex instanceof ClosedChannelException) {
                        LOGGER.info("Connection closed during protocol change");
                    } else {
                        this.channel.pipeline().fireExceptionCaught(ex);
                    }
                }finally {
                    // reset auto back and resume reading if needed
                    if (resumeAutoReading) {
                        this.channel.config().setAutoRead(true);
                        this.channel.read();
                    }
                }
            });

            // we need to do this inside the event loop
            if (!this.channel.eventLoop().inEventLoop()) {
                this.channel.eventLoop().execute(toExecute);
                return;
            }

            toExecute.run();
        }
    }

    public void setupOutboundProtocolAsync(
            ProtocolInfo<?> protocol,
            @Nullable Runnable callback,
            boolean resumeAutoReading
    ) {
        if (protocol.flow() != this.getSending()) {
            throw new IllegalStateException("Invalid outbound protocol: " + protocol.id());
        } else {
            UnconfiguredPipelineHandler.OutboundConfigurationTask configMessage = UnconfiguredPipelineHandler.setupOutboundProtocol(protocol);
            BundlerInfo bundlerInfo = protocol.bundlerInfo();
            if (bundlerInfo != null) {
                PacketBundleUnpacker newUnbundler = new PacketBundleUnpacker(bundlerInfo);
                configMessage = configMessage.andThen(
                        context -> context.pipeline().addAfter("encoder", "unbundler", newUnbundler)
                );
            }

            boolean isLoginProtocol = protocol.id() == ConnectionProtocol.LOGIN;

            // Here we could execute it in event loop async to prevent waiting io task on main
            // stop reading new packets to prevent some packets came into pipeline too early
            this.channel.config().setAutoRead(false);

            // do our configuration task
            final UnconfiguredPipelineHandler.OutboundConfigurationTask finalOutboundConfigurationTask = configMessage;
            final Runnable writeTask = () -> this.channel.writeAndFlush(
                    finalOutboundConfigurationTask.andThen(context -> this.sendLoginDisconnect = isLoginProtocol)
            ).addListener(future -> {
                try {
                    if (future.isSuccess()) {
                        if (callback != null) callback.run(); // retire callback if there have one
                        return;
                    }

                    final Throwable ex = future.cause();

                    // here we process our exceptions like that blocking one
                    if (ex instanceof ClosedChannelException) {
                        LOGGER.info("Connection closed during protocol change");
                    } else {
                        this.channel.pipeline().fireExceptionCaught(ex);
                    }
                }finally {
                    // reset auto back and resume reading if needed
                    if (resumeAutoReading) {
                        this.channel.config().setAutoRead(true);
                        this.channel.read(); // read once
                    }
                }
            });

            // we need to do this inside the event loop
            if (!this.channel.eventLoop().inEventLoop()) {
                this.channel.eventLoop().execute(writeTask);
                return;
            }

            writeTask.run();
        }
    }
    // Luminol end

    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetInfo) {
        this.validateListener(protocolInfo, packetInfo);
        if (protocolInfo.flow() != this.getReceiving()) {
            throw new IllegalStateException("Invalid inbound protocol: " + protocolInfo.id());
        } else {
            this.packetListener = packetInfo;
            this.disconnectListener = null;
            UnconfiguredPipelineHandler.InboundConfigurationTask inboundConfigurationTask = UnconfiguredPipelineHandler.setupInboundProtocol(protocolInfo);
            BundlerInfo bundlerInfo = protocolInfo.bundlerInfo();
            if (bundlerInfo != null) {
                PacketBundlePacker packetBundlePacker = new PacketBundlePacker(bundlerInfo);
                inboundConfigurationTask = inboundConfigurationTask.andThen(ctx -> ctx.pipeline().addAfter("decoder", "bundler", packetBundlePacker));
            }

            syncAfterConfigurationChange(this.channel.writeAndFlush(inboundConfigurationTask));
        }
    }

    public void setupOutboundProtocol(ProtocolInfo<?> protocolInfo) {
        if (protocolInfo.flow() != this.getSending()) {
            throw new IllegalStateException("Invalid outbound protocol: " + protocolInfo.id());
        } else {
            UnconfiguredPipelineHandler.OutboundConfigurationTask outboundConfigurationTask = UnconfiguredPipelineHandler.setupOutboundProtocol(protocolInfo);
            BundlerInfo bundlerInfo = protocolInfo.bundlerInfo();
            if (bundlerInfo != null) {
                PacketBundleUnpacker packetBundleUnpacker = new PacketBundleUnpacker(bundlerInfo);
                outboundConfigurationTask = outboundConfigurationTask.andThen(ctx -> ctx.pipeline().addAfter("encoder", "unbundler", packetBundleUnpacker));
            }

            boolean flag = protocolInfo.id() == ConnectionProtocol.LOGIN;
            syncAfterConfigurationChange(this.channel.writeAndFlush(outboundConfigurationTask.andThen(ctx -> this.sendLoginDisconnect = flag)));
        }
    }

    public void setListenerForServerboundHandshake(PacketListener packetListener) {
        if (this.packetListener != null) {
            throw new IllegalStateException("Listener already set");
        } else if (this.receiving == PacketFlow.SERVERBOUND
            && packetListener.flow() == PacketFlow.SERVERBOUND
            && packetListener.protocol() == INITIAL_PROTOCOL.id()) {
            this.packetListener = packetListener;
        } else {
            throw new IllegalStateException("Invalid initial listener");
        }
    }

    public void initiateServerboundStatusConnection(String hostName, int port, ClientStatusPacketListener packetListener) {
        this.initiateServerboundConnection(hostName, port, StatusProtocols.SERVERBOUND, StatusProtocols.CLIENTBOUND, packetListener, ClientIntent.STATUS);
    }

    public void initiateServerboundPlayConnection(String hostName, int port, ClientLoginPacketListener packetListener) {
        this.initiateServerboundConnection(hostName, port, LoginProtocols.SERVERBOUND, LoginProtocols.CLIENTBOUND, packetListener, ClientIntent.LOGIN);
    }

    public <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundPlayConnection(
        String hostName, int port, ProtocolInfo<S> serverboundProtocol, ProtocolInfo<C> clientbountProtocol, C packetListener, boolean isTransfer
    ) {
        this.initiateServerboundConnection(
            hostName, port, serverboundProtocol, clientbountProtocol, packetListener, isTransfer ? ClientIntent.TRANSFER : ClientIntent.LOGIN
        );
    }

    private <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundConnection(
        String hostName, int port, ProtocolInfo<S> serverboundProtocol, ProtocolInfo<C> clientboundProtocol, C packetListener, ClientIntent intention
    ) {
        if (serverboundProtocol.id() != clientboundProtocol.id()) {
            throw new IllegalStateException("Mismatched initial protocols");
        } else {
            this.disconnectListener = packetListener;
            this.runOnceConnected(connection -> {
                this.setupInboundProtocol(clientboundProtocol, packetListener);
                connection.sendPacket(new ClientIntentionPacket(SharedConstants.getCurrentVersion().protocolVersion(), hostName, port, intention), null, true);
                this.setupOutboundProtocol(serverboundProtocol);
            });
        }
    }

    public void send(Packet<?> packet) {
        this.send(packet, null);
    }

    public void send(Packet<?> packet, @Nullable ChannelFutureListener sendListener) {
        this.send(packet, sendListener, true);
    }

    public void send(Packet<?> packet, @Nullable ChannelFutureListener sendListener, boolean flush) {
        // Paper start - Optimize network: Handle oversized packets better
        final boolean connected = this.isConnected();
        if (!connected && !this.preparing) {
            return;
        }

        packet.onPacketDispatch(this.getPlayer());
        if (false && connected && (InnerUtil.canSendImmediate(this, packet) // Folia - region threading - connection fixes
            || (io.papermc.paper.util.MCUtil.isMainThread() && packet.isReady() && this.pendingActions.isEmpty()
            && (packet.getExtraPackets() == null || packet.getExtraPackets().isEmpty())))) {
            this.sendPacket(packet, sendListener, flush);
        } else {
            // Write the packets to the queue, then flush - antixray hooks there already
            final java.util.List<Packet<?>> extraPackets = InnerUtil.buildExtraPackets(packet);
            final boolean hasExtraPackets = extraPackets != null && !extraPackets.isEmpty();
            if (!hasExtraPackets) {
                this.pendingActions.add(new PacketSendAction(packet, sendListener, flush));
            } else {
                final java.util.List<PacketSendAction> actions = new java.util.ArrayList<>(1 + extraPackets.size());
                actions.add(new PacketSendAction(packet, null, false)); // Delay the future listener until the end of the extra packets

                for (int i = 0, len = extraPackets.size(); i < len;) {
                    final Packet<?> extraPacket = extraPackets.get(i);
                    final boolean end = ++i == len;
                    actions.add(new PacketSendAction(extraPacket, end ? sendListener : null, end)); // Append listener to the end
                }

                this.pendingActions.addAll(actions);
            }

            this.flushQueue();
            // Paper end - Optimize network
        }
    }

    public void runOnceConnected(Consumer<Connection> action) {
        if (false && this.isConnected()) { // Folia - region threading - connection fixes
            this.flushQueue();
            action.accept(this);
        } else {
            this.pendingActions.add(new WrappedConsumer(action)); // Paper - Optimize network
            this.flushQueue(); // Folia - region threading - connection fixes
        }
    }

    private void sendPacket(Packet<?> packet, @Nullable ChannelFutureListener sendListener, boolean flush) {
        this.sentPackets++;
        if (this.channel.eventLoop().inEventLoop()) {
            this.doSendPacket(packet, sendListener, flush);
        } else {
            this.channel.eventLoop().execute(() -> this.doSendPacket(packet, sendListener, flush));
        }
    }

    private void doSendPacket(Packet<?> packet, @Nullable ChannelFutureListener sendListener, boolean flush) {
        // Paper start - Optimize network
        final net.minecraft.server.level.ServerPlayer player = this.getPlayer();
        if (!this.isConnected()) {
            packet.onPacketDispatchFinish(player, null);
            return;
        }
        try {
        final ChannelFuture channelFuture;
        // Paper end - Optimize network
        if (sendListener != null) {
            channelFuture = flush ? this.channel.writeAndFlush(packet) : this.channel.write(packet); // Paper - Optimize network
            channelFuture.addListener(sendListener);
        } else if (flush) {
            channelFuture = this.channel.writeAndFlush(packet, this.channel.voidPromise()); // Paper - Optimize network
        } else {
            channelFuture = this.channel.write(packet, this.channel.voidPromise()); // Paper - Optimize network
        }

        // Paper start - Optimize network
        if (packet.hasFinishListener()) {
            channelFuture.addListener((ChannelFutureListener) future -> packet.onPacketDispatchFinish(player, future));
        }
        } catch (final Exception e) {
            LOGGER.error("NetworkException: {}", player, e);
            this.disconnect(Component.translatable("disconnect.genericReason", "Internal Exception: " + e.getMessage()));
            packet.onPacketDispatchFinish(player, null);
        }
        // Paper end - Optimize network
    }

    public void flushChannel() {
        if (false && this.isConnected()) { // Folia - region threading - connection fixes
            this.flush();
        } else {
            this.pendingActions.add(new WrappedConsumer(Connection::flush)); // Paper - Optimize network
            this.flushQueue(); // Folia - region threading - connection fixes
        }
    }

    private void flush() {
        if (this.channel.eventLoop().inEventLoop()) {
            this.channel.flush();
        } else {
            this.channel.eventLoop().execute(() -> this.channel.flush());
        }
    }

    // Paper start - Optimize network: Rewrite this to be safer if ran off main thread
    private boolean flushQueue() {
        return this.processQueue(); // Folia - region threading - connection fixes
    }

    // Folia start - region threading - connection fixes
    // allow only one thread to be flushing the queue at once to ensure packets are written in the order they are sent
    // into the queue
    private final java.util.concurrent.atomic.AtomicBoolean flushingQueue = new java.util.concurrent.atomic.AtomicBoolean();

    private static boolean canWrite(WrappedConsumer queued) {
        return queued != null && (!(queued instanceof PacketSendAction packet) || packet.packet.isReady());
    }

    private boolean canWritePackets() {
        return canWrite(this.pendingActions.peek());
    }
    // Folia end - region threading - connection fixes

    private boolean processQueue() {
        // Folia start - region threading - connection fixes
        if (!this.isConnected()) {
            return true;
        }

        while (this.canWritePackets()) {
            final boolean set = this.flushingQueue.getAndSet(true);
            try {
                if (set) {
                    // we didn't acquire the lock, break
                    return false;
                }

                ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<WrappedConsumer> queue =
                    (ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<WrappedConsumer>)this.pendingActions;
                WrappedConsumer holder;
                for (;;) {
                    // synchronise so that queue clears appear atomic
                    synchronized (queue) {
                        holder = queue.pollIf(Connection::canWrite);
                    }
                    if (holder == null) {
                        break;
                    }

                    holder.accept(this);
                }

            } finally {
                if (!set) {
                    this.flushingQueue.set(false);
                }
            }
        }

        return true;
        // Folia end - region threading - connection fixes
    }
    // Paper end - Optimize network

    private static final int MAX_PER_TICK = io.papermc.paper.configuration.GlobalConfiguration.get().misc.maxJoinsPerTick; // Paper - Buffer joins to world
    private static int joinAttemptsThisTick; // Paper - Buffer joins to world
    private static int currTick; // Paper - Buffer joins to world
    public void tick() {
        this.flushQueue();
        // Folia - this is broken
        // Folia start - region threading
        // handle disconnect requests, but only after flushQueue()
        DisconnectReq disconnectReq;
        while ((disconnectReq = this.disconnectReqs.poll()) != null) {
            PacketListener packetlistener = this.packetListener;

            if (packetlistener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginPacketListener) {
                loginPacketListener.disconnect(disconnectReq.details.reason());
                // this doesn't fail, so abort any further attempts
                return;
            } else if (packetlistener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl commonPacketListener) {
                commonPacketListener.disconnect(disconnectReq.details);
                // may be cancelled by a plugin, if not cancelled then any further calls do nothing
                continue;
            } else {
                this.disconnect(disconnectReq.details);
                this.setReadOnly();
                return;
            }
        }
        if (!this.isConnected()) {
            // disconnected from above
            this.handleDisconnection();
            return;
        }
        // Folia end - region threading
        if (this.packetListener instanceof TickablePacketListener tickablePacketListener) {
            // Paper start - Buffer joins to world
            if (true) { // Folia - region threading
            // Paper start - detailed watchdog information
            net.minecraft.network.PacketProcessor.packetProcessing.push(this.packetListener);
            try {
            tickablePacketListener.tick();
            } finally {
                net.minecraft.network.PacketProcessor.packetProcessing.pop();
            } // Paper end - detailed watchdog information
            } // Paper end - Buffer joins to world
        }

        if (!this.isConnected()) {// Folia - region threading - it's fine to call if it is already handled, as it no longer logs
            this.handleDisconnection();
        }

        if (this.channel != null) {
            if (enableExplicitFlush) this.channel.eventLoop().execute(() -> this.channel.flush()); // Paper - Disable explicit network manager flushing; we don't need to explicit flush here, but allow opt in incase issues are found to a better version
        }

        if (this.tickCount++ % 20 == 0) {
            this.tickSecond();
        }

        if (this.bandwidthDebugMonitor != null) {
            this.bandwidthDebugMonitor.tick();
        }
    }

    protected void tickSecond() {
        this.averageSentPackets = Mth.lerp(0.75F, (float)this.sentPackets, this.averageSentPackets);
        this.averageReceivedPackets = Mth.lerp(0.75F, (float)this.receivedPackets, this.averageReceivedPackets);
        this.sentPackets = 0;
        this.receivedPackets = 0;
    }

    public SocketAddress getRemoteAddress() {
        return this.address;
    }

    public String getLoggableAddress(boolean logIps) {
        if (this.address == null) {
            return "local";
        } else {
            return logIps ? this.address.toString() : "IP hidden";
        }
    }

    public void disconnect(Component reason) {
        this.disconnect(new DisconnectionDetails(reason));
    }

    public void disconnect(DisconnectionDetails disconnectionDetails) {
        this.preparing = false; // Spigot
        this.clearPacketQueue(); // Paper - Optimize network
        if (this.channel == null) {
            this.delayedDisconnect = disconnectionDetails;
        }

        if (this.isConnected()) {
            this.channel.close(); // We can't wait as this may be called from an event loop.
            this.disconnectionDetails = disconnectionDetails;
        }
        this.becomeActive = true; // Folia - region threading
    }

    public boolean isMemoryConnection() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public PacketFlow getReceiving() {
        return this.receiving;
    }

    public PacketFlow getSending() {
        return this.receiving.getOpposite();
    }

    public static Connection connectToServer(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, @Nullable LocalSampleLogger sampleLogger) {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        if (sampleLogger != null) {
            connection.setBandwidthLogger(sampleLogger);
        }

        ChannelFuture channelFuture = connect(address, eventLoopGroupHolder, connection);
        channelFuture.syncUninterruptibly();
        return connection;
    }

    public static ChannelFuture connect(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, final Connection connection) {
        return new Bootstrap().group(eventLoopGroupHolder.eventLoopGroup()).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException var3) {
                }

                ChannelPipeline channelPipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
                Connection.configureSerialization(channelPipeline, PacketFlow.CLIENTBOUND, false, connection.bandwidthDebugMonitor);
                connection.configurePacketHandler(channelPipeline);
            }
        }).channel(eventLoopGroupHolder.channelCls()).connect(address.getAddress(), address.getPort());
    }

    private static String outboundHandlerName(boolean clientbound) {
        return clientbound ? "encoder" : "outbound_config";
    }

    private static String inboundHandlerName(boolean serverbound) {
        return serverbound ? "decoder" : "inbound_config";
    }

    public void configurePacketHandler(ChannelPipeline pipeline) {
        pipeline.addLast("hackfix", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext channelHandlerContext, Object object, ChannelPromise channelPromise) throws Exception {
                super.write(channelHandlerContext, object, channelPromise);
            }
        }).addLast("packet_handler", this);
    }

    public static void configureSerialization(
        ChannelPipeline pipeline, PacketFlow flow, boolean memoryOnly, @Nullable BandwidthDebugMonitor bandwidthDebugMonitor
    ) {
        PacketFlow opposite = flow.getOpposite();
        boolean flag = flow == PacketFlow.SERVERBOUND;
        boolean flag1 = opposite == PacketFlow.SERVERBOUND;
        pipeline.addLast("splitter", createFrameDecoder(bandwidthDebugMonitor, memoryOnly))
            .addLast(new FlowControlHandler())
            .addLast(inboundHandlerName(flag), (ChannelHandler)(flag ? new PacketDecoder<>(INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Inbound()))
            .addLast("prepender", createFrameEncoder(memoryOnly))
            .addLast(outboundHandlerName(flag1), (ChannelHandler)(flag1 ? new PacketEncoder<>(INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Outbound()));
    }

    private static ChannelOutboundHandler createFrameEncoder(boolean memoryOnly) {
        return (ChannelOutboundHandler)(memoryOnly ? new LocalFrameEncoder() : new Varint21LengthFieldPrepender());
    }

    private static ChannelInboundHandler createFrameDecoder(@Nullable BandwidthDebugMonitor bandwidthDebugMonitor, boolean memoryOnly) {
        if (!memoryOnly) {
            return new Varint21FrameDecoder(bandwidthDebugMonitor);
        } else {
            return (ChannelInboundHandler)(bandwidthDebugMonitor != null ? new MonitoredLocalFrameDecoder(bandwidthDebugMonitor) : new LocalFrameDecoder());
        }
    }

    public static void configureInMemoryPipeline(ChannelPipeline pipeline, PacketFlow flow) {
        configureSerialization(pipeline, flow, true, null);
    }

    public static Connection connectToLocalServer(SocketAddress address) {
        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        new Bootstrap().group(EventLoopGroupHolder.local().eventLoopGroup()).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                ChannelPipeline channelPipeline = channel.pipeline();
                Connection.configureInMemoryPipeline(channelPipeline, PacketFlow.CLIENTBOUND);
                connection.configurePacketHandler(channelPipeline);
            }
        }).channel(EventLoopGroupHolder.local().channelCls()).connect(address).syncUninterruptibly();
        return connection;
    }

    // Paper start - Use Velocity cipher
    public void setEncryptionKey(javax.crypto.SecretKey key) throws net.minecraft.util.CryptException {
        if (!this.encrypted) {
            try {
                com.velocitypowered.natives.encryption.VelocityCipher decryptionCipher = com.velocitypowered.natives.util.Natives.cipher.get().forDecryption(key);
                com.velocitypowered.natives.encryption.VelocityCipher encryptionCipher = com.velocitypowered.natives.util.Natives.cipher.get().forEncryption(key);

                this.encrypted = true;
                this.channel.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(decryptionCipher));
                this.channel.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(encryptionCipher));
            } catch (java.security.GeneralSecurityException e) {
                throw new net.minecraft.util.CryptException(e);
            }
        }
    }
    // Paper end - Use Velocity cipher

    public boolean isEncrypted() {
        return this.encrypted;
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return this.channel == null;
    }

    public @Nullable PacketListener getPacketListener() {
        return this.packetListener;
    }

    public @Nullable DisconnectionDetails getDisconnectionDetails() {
        return this.disconnectionDetails;
    }

    public void setReadOnly() {
        if (this.channel != null) {
            this.channel.config().setAutoRead(false);
        }
    }

    // Paper start - add proper async disconnect
    public void enableAutoRead() {
        if (this.channel != null) {
            this.channel.config().setAutoRead(true);
        }
    }
    // Paper end - add proper async disconnect
    public void setupCompression(int threshold, boolean validateDecompressed) {
        if (threshold >= 0) {
            com.velocitypowered.natives.compression.VelocityCompressor compressor = com.velocitypowered.natives.util.Natives.compress.get().create(io.papermc.paper.configuration.GlobalConfiguration.get().misc.compressionLevel.or(-1)); // Paper - Use Velocity cipher
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder compressionDecoder) {
                compressionDecoder.setThreshold(compressor, threshold, validateDecompressed); // Paper - Use Velocity cipher
            } else {
                this.channel.pipeline().addAfter("splitter", "decompress", new CompressionDecoder(compressor, threshold, validateDecompressed)); // Paper - Use Velocity cipher
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder compressionEncoder) {
                compressionEncoder.setThreshold(threshold);
            } else {
                this.channel.pipeline().addAfter("prepender", "compress", new CompressionEncoder(compressor, threshold)); // Paper - Use Velocity cipher
            }
            this.channel.pipeline().fireUserEventTriggered(io.papermc.paper.network.ConnectionEvent.COMPRESSION_THRESHOLD_SET); // Paper - Add Channel initialization listeners
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
            this.channel.pipeline().fireUserEventTriggered(io.papermc.paper.network.ConnectionEvent.COMPRESSION_DISABLED); // Paper - Add Channel initialization listeners
        }
    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (!this.disconnectionHandled.compareAndSet(false, true)) { // Folia - region threading - may be called concurrently during configuration stage
                // LOGGER.warn("handleDisconnection() called twice"); // Paper - Don't log useless message
            } else {
                //this.disconnectionHandled = true; // Folia - region threading - may be called concurrently during configuration stage - set above
                PacketListener packetListener = this.getPacketListener();
                PacketListener packetListener1 = packetListener != null ? packetListener : this.disconnectListener;
                if (packetListener1 != null) {
                    DisconnectionDetails disconnectionDetails = Objects.requireNonNullElseGet(
                        this.getDisconnectionDetails(), () -> new DisconnectionDetails(Component.translatable("multiplayer.disconnect.generic"))
                    );
                    packetListener1.onDisconnect(disconnectionDetails);
                }
                this.clearPacketQueue(); // Paper - Optimize network
                // Paper start - Add PlayerConnectionCloseEvent
                if (packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl commonPacketListener) {
                    /* Player was logged in, either game listener or configuration listener */
                    final com.mojang.authlib.GameProfile profile = commonPacketListener.getOwner();
                    new com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent(profile.id(),
                        profile.name(), ((InetSocketAddress) this.address).getAddress(), false).callEvent();
                } else if (packetListener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginListener) {
                    /* Player is login stage */
                    switch (loginListener.state) {
                        case VERIFYING:
                        case WAITING_FOR_DUPE_DISCONNECT:
                        case PROTOCOL_SWITCHING:
                        case ACCEPTED:
                            final com.mojang.authlib.GameProfile profile = loginListener.authenticatedProfile; /* Should be non-null at this stage */
                            new com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent(profile.id(), profile.name(),
                                ((InetSocketAddress) this.address).getAddress(), false).callEvent();
                    }
                }
                // Paper end - Add PlayerConnectionCloseEvent
                // Folia start - region threading
                if (packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl commonPacketListener) {
                    net.minecraft.server.MinecraftServer.getServer().getPlayerList().removeConnection(
                            commonPacketListener.getOwner().name(),
                            commonPacketListener.getOwner().id(), this
                    );
                } else if (packetListener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginPacketListener) {
                    if (loginPacketListener.state.ordinal() >= net.minecraft.server.network.ServerLoginPacketListenerImpl.State.VERIFYING.ordinal()) {
                        net.minecraft.server.MinecraftServer.getServer().getPlayerList().removeConnection(
                                loginPacketListener.authenticatedProfile.name(),
                                loginPacketListener.authenticatedProfile.id(), this
                        );
                    }
                }
                // Folia end - region threading
            }
        }
    }

    public float getAverageReceivedPackets() {
        return this.averageReceivedPackets;
    }

    public float getAverageSentPackets() {
        return this.averageSentPackets;
    }

    public void setBandwidthLogger(LocalSampleLogger bandwidthLogger) {
        this.bandwidthDebugMonitor = new BandwidthDebugMonitor(bandwidthLogger);
    }

    // Paper start - Optimize network
    public void clearPacketQueue() {
        final net.minecraft.server.level.ServerPlayer player = getPlayer();
        // Folia start - region threading - connection fixes
        java.util.List<Connection.PacketSendAction> queuedPackets = new java.util.ArrayList<>();
        // synchronise so that flushQueue does not poll values while the queue is being cleared
        synchronized (this.pendingActions) {
            Connection.WrappedConsumer consumer;
            while ((consumer = this.pendingActions.poll()) != null) {
                if (consumer instanceof Connection.PacketSendAction packetHolder) {
                    queuedPackets.add(packetHolder);
                }
            }
        }

        for (Connection.PacketSendAction queuedPacket : queuedPackets) {
            Packet<?> packet = queuedPacket.packet;
            if (packet.hasFinishListener()) {
                packet.onPacketDispatchFinish(player, null);
            }
        }
        // Folia end - region threading - connection fixes
    }

    private static final class InnerUtil { // Attempt to hide these methods from ProtocolLib, so it doesn't accidently pick them up.

        private static java.util.@Nullable List<Packet<?>> buildExtraPackets(final Packet<?> packet) {
            final java.util.List<Packet<?>> extra = packet.getExtraPackets();
            if (extra == null || extra.isEmpty()) {
                return null;
            }

            final java.util.List<Packet<?>> ret = new java.util.ArrayList<>(1 + extra.size());
            buildExtraPackets0(extra, ret);
            return ret;
        }

        private static void buildExtraPackets0(final java.util.List<Packet<?>> extraPackets, final java.util.List<Packet<?>> into) {
            for (final Packet<?> extra : extraPackets) {
                into.add(extra);
                final java.util.List<Packet<?>> extraExtra = extra.getExtraPackets();
                if (extraExtra != null && !extraExtra.isEmpty()) {
                    buildExtraPackets0(extraExtra, into);
                }
            }
        }

        private static boolean canSendImmediate(final Connection networkManager, final net.minecraft.network.protocol.Packet<?> packet) {
            return networkManager.isPending || networkManager.packetListener.protocol() != ConnectionProtocol.PLAY ||
                packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundClearTitlesPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSoundEntityPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundStopSoundPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundBossEventPacket ||
                packet instanceof net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
        }
    }

    private static class WrappedConsumer implements Consumer<Connection> {
        private final Consumer<Connection> delegate;
        private final java.util.concurrent.atomic.AtomicBoolean consumed = new java.util.concurrent.atomic.AtomicBoolean(false);

        private WrappedConsumer(final Consumer<Connection> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accept(final Connection connection) {
            this.delegate.accept(connection);
        }

        public boolean tryMarkConsumed() {
            return consumed.compareAndSet(false, true);
        }

        public boolean isConsumed() {
            return consumed.get();
        }
    }

    private static final class PacketSendAction extends WrappedConsumer {
        private final Packet<?> packet;

        private PacketSendAction(final Packet<?> packet, @Nullable final ChannelFutureListener channelFutureListener, final boolean flush) {
            super(connection -> connection.sendPacket(packet, channelFutureListener, flush));
            this.packet = packet;
        }
    }
    // Paper end - Optimize network
}
