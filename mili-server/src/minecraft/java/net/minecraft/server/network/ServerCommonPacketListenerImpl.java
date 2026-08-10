package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class ServerCommonPacketListenerImpl implements ServerCommonPacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LATENCY_CHECK_INTERVAL = 15000;
    private static final int CLOSED_LISTENER_TIMEOUT = 15000;
    private static final Component TIMEOUT_DISCONNECTION_MESSAGE = Component.translatable("disconnect.timeout");
    static final Component DISCONNECT_UNEXPECTED_QUERY = Component.translatable("multiplayer.disconnect.unexpected_query_response");
    protected final MinecraftServer server;
    public final Connection connection; // Paper
    private final boolean transferred;
    //private long keepAliveTime; // Paper - improve keepalives
    //private boolean keepAlivePending; // Paper - improve keepalives
    //private long keepAliveChallenge; // Paper - improve keepalives
    private long closedListenerTime;
    private boolean closed = false;
    private volatile int latency; // Paper - improve keepalives - make volatile
    private final io.papermc.paper.util.KeepAlive keepAlive; // Paper - improve keepalives
    private volatile long lastKeepAliveResponse; // Mili - async keepalive
    private volatile boolean suspendFlushingOnServerThread = false;
    // CraftBukkit start
    public final org.bukkit.craftbukkit.CraftServer cserver;
    public boolean processedDisconnect;
    // CraftBukkit end
    public final java.util.Map<java.util.UUID, net.kyori.adventure.resource.ResourcePackCallback> packCallbacks = new java.util.concurrent.ConcurrentHashMap<>(); // Paper - adventure resource pack callbacks
    private static final long KEEPALIVE_LIMIT = Long.getLong("paper.playerconnection.keepalive", 30) * 1000; // Paper - provide property to set keepalive limit
    protected static final net.minecraft.resources.Identifier MINECRAFT_BRAND = net.minecraft.resources.Identifier.withDefaultNamespace("brand"); // Paper - Brand support
    // Paper start - retain certain values
    public @Nullable String playerBrand;
    public final java.util.Set<String> pluginMessagerChannels;
    // Paper end - retain certain values

    public ServerCommonPacketListenerImpl(MinecraftServer server, Connection connection, CommonListenerCookie cookie) {
        this.server = server;
        this.connection = connection;
        //this.keepAliveTime = Util.getMillis(); // Paper - improve keepalives
        this.latency = cookie.latency();
        this.transferred = cookie.transferred();
        // Paper start
        this.playerBrand = cookie.brandName();
        this.cserver = server.server;
        this.pluginMessagerChannels = cookie.channels();
        this.keepAlive = cookie.keepAlive();
        // Paper end
        this.lastKeepAliveResponse = this.keepAlive.lastKeepAliveTx; // Mili - async keepalive
        fun.bm.mili.utils.AsyncKeepaliveManager.register(this); // Mili - async keepalive
    }

    // Paper start - configuration phase API
    public abstract io.papermc.paper.connection.PlayerCommonConnection getApiConnection();

    public abstract net.kyori.adventure.audience.Audience getAudience();
    // Paper end - configuration phase API

    private void close() {
        if (!this.closed) {
            this.closedListenerTime = Util.getMillis();
            this.closed = true;
        }
    }

    // Folia start - region threading
    public boolean handledDisconnect = false;
    // Folia end - region threading

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        // Folia start - region threading
        if (this.handledDisconnect) {
            // avoid retiring scheduler twice
            return;
        }
        this.handledDisconnect = true;
        // Folia end - region threading
        fun.bm.mili.utils.AsyncKeepaliveManager.unregister(this); // Mili - async keepalive
        if (this.isSingleplayerOwner()) {
            LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }
    }

    @Override
    public void onPacketError(Packet packet, Exception exception) throws ReportedException {
        ServerCommonPacketListener.super.onPacketError(packet, exception);
        this.server.reportPacketHandlingException(exception, packet.type());
    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket packet) {
        // Mili start - async keepalive
        if (fun.bm.mili.config.modules.function.OldFeatureConfig.asyncKeepalive) {
            this.handleAsyncKeepAlive(packet);
            return;
        }
        // Mili end - async keepalive
        // Paper start - improve keepalives
        long now = System.nanoTime();
        io.papermc.paper.util.KeepAlive.PendingKeepAlive pending = this.keepAlive.pendingKeepAlives.peek();
        if (pending != null && pending.challengeId() == packet.getId()) {
            this.keepAlive.pendingKeepAlives.remove(pending);

            io.papermc.paper.util.KeepAlive.KeepAliveResponse response = new io.papermc.paper.util.KeepAlive.KeepAliveResponse(pending.txTimeNS(), now);

            this.keepAlive.pingCalculator1m.update(response);
            this.keepAlive.pingCalculator5s.update(response);

            this.latency = this.keepAlive.pingCalculator5s.getAvgLatencyMS();
            return;
        }

        for (java.util.Iterator<io.papermc.paper.util.KeepAlive.PendingKeepAlive> itr = this.keepAlive.pendingKeepAlives.iterator(); itr.hasNext();) {
            io.papermc.paper.util.KeepAlive.PendingKeepAlive ka = itr.next();
            if (ka.challengeId() == packet.getId()) {
                itr.remove();

                if (!this.processedDisconnect) {
                    LOGGER.info("Disconnecting {} for sending keepalive response ({}) out-of-order!", this.playerProfile().name(), packet.getId());
                    this.disconnectAsync(TIMEOUT_DISCONNECTION_MESSAGE, io.papermc.paper.connection.DisconnectionReason.TIMEOUT);
                    return;
                }
                break;
            }
        }

        if (!this.processedDisconnect) {
            LOGGER.info("Disconnecting {} for sending keepalive response ({}) without matching challenge!", this.playerProfile().name(), packet.getId());
            this.disconnectAsync(TIMEOUT_DISCONNECTION_MESSAGE, io.papermc.paper.connection.DisconnectionReason.TIMEOUT);
            return;
        }
        // Paper end - improve keepalives
    }

    // Mili start - async keepalive
    private void handleAsyncKeepAlive(ServerboundKeepAlivePacket packet) {
        long now = System.nanoTime();
        long timeoutNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(fun.bm.mili.config.modules.function.OldFeatureConfig.asyncKeepaliveTimeoutSeconds * 1000L);
        for (io.papermc.paper.util.KeepAlive.PendingKeepAlive pending : this.keepAlive.pendingKeepAlives) {
            if (pending.challengeId() == packet.getId()) {
                if ((now - pending.txTimeNS()) > timeoutNanos) {
                    return;
                }

                io.papermc.paper.util.KeepAlive.KeepAliveResponse response = new io.papermc.paper.util.KeepAlive.KeepAliveResponse(pending.txTimeNS(), now);
                this.keepAlive.pingCalculator1m.update(response);
                this.keepAlive.pingCalculator5s.update(response);
                this.latency = this.keepAlive.pingCalculator5s.getAvgLatencyMS();
                this.lastKeepAliveResponse = now;
                this.keepAlive.pendingKeepAlives.remove(pending);
                return;
            }
        }
        this.keepAlive.pendingKeepAlives.pollIf((pending) -> (now - pending.txTimeNS()) > timeoutNanos);
    }
    // Mili end - async keepalive

    @Override
    public void handlePong(ServerboundPongPacket packet) {
    }

    // Paper start
    public static final net.minecraft.resources.Identifier CUSTOM_REGISTER = net.minecraft.resources.Identifier.withDefaultNamespace("register");
    private static final net.minecraft.resources.Identifier CUSTOM_UNREGISTER = net.minecraft.resources.Identifier.withDefaultNamespace("unregister");
    // Paper end

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
        // Leaves start - protocol
        if (packet.payload() instanceof org.leavesmc.leaves.protocol.core.LeavesCustomPayload leavesPayload) {
            org.leavesmc.leaves.protocol.core.LeavesProtocolManager.handlePayload(org.leavesmc.leaves.protocol.core.ProtocolUtils.createSelector(this), leavesPayload);
            return;
        }
        if (packet.payload() instanceof net.minecraft.network.protocol.common.custom.DiscardedPayload(net.minecraft.resources.Identifier id, byte[] data)) {
            if (org.leavesmc.leaves.protocol.core.LeavesProtocolManager.handleBytebuf(org.leavesmc.leaves.protocol.core.ProtocolUtils.createSelector(this), id, io.netty.buffer.Unpooled.wrappedBuffer(data))) {
                return;
            }
        }
        // Leaves end - protocol
        // Paper start
        if (!(packet.payload() instanceof final net.minecraft.network.protocol.common.custom.DiscardedPayload discardedPayload)) {
            return;
        }

        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet, this, this.server.packetProcessor());

        final net.minecraft.resources.Identifier identifier = packet.payload().type().id();
        final byte[] data = discardedPayload.data();
        try {
            final boolean registerChannel = CUSTOM_REGISTER.equals(identifier);
            if (registerChannel || CUSTOM_UNREGISTER.equals(identifier)) {
                // Strings separated by zeros instead of length prefixes
                int startIndex = 0;
                for (int i = 0; i < data.length; i++) {
                    final byte b = data[i];
                    if (b != 0) {
                        continue;
                    }

                    readChannelIdentifier(data, startIndex, i, registerChannel);
                    startIndex = i + 1;
                }

                // Read the last one
                readChannelIdentifier(data, startIndex, data.length, registerChannel);
                return;
            }

            if (identifier.equals(MINECRAFT_BRAND)) {
                this.playerBrand = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data)).readUtf(256);
            }

            this.cserver.getMessenger().dispatchIncomingMessage(paperConnection(), identifier.toString(), data);
        } catch (final Exception e) {
            ServerGamePacketListenerImpl.LOGGER.error("Couldn't handle custom payload on channel {}", identifier, e);
            this.disconnect(net.minecraft.network.chat.Component.literal("Invalid custom payload payload!"), io.papermc.paper.connection.DisconnectionReason.INVALID_PAYLOAD); // Paper - kick event cause
        }
    }

    private void readChannelIdentifier(final byte[] data, final int from, final int to, final boolean register) {
        io.papermc.paper.connection.PluginMessageBridgeImpl bridge = switch (this) {
            case ServerGamePacketListenerImpl gamePacketListener -> gamePacketListener.player.getBukkitEntity();
            case ServerConfigurationPacketListenerImpl commonPacketListener -> commonPacketListener.paperConnection;
            default -> null;
        };
        if (bridge == null) {
            return;
        }


        final int length = to - from;
        if (length == 0) {
            return;
        }

        final String channel = new String(data, from, length, java.nio.charset.StandardCharsets.US_ASCII);
        if (register) {
            bridge.addChannel(channel);
            org.leavesmc.leaves.protocol.core.LeavesProtocolManager.handleMinecraftRegister(channel, org.leavesmc.leaves.protocol.core.ProtocolUtils.createSelector(this)); // Leaves - protocol
        } else {
            bridge.removeChannel(channel);
        }
    // Paper end
    }

    @Override
    public void handleCustomClickAction(ServerboundCustomClickActionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.server.packetProcessor());
        this.server.handleCustomClickAction(packet.id(), packet.payload());
        // Paper start - Implement click callbacks with custom click action
        final io.papermc.paper.event.player.PaperPlayerCustomClickEvent event = new io.papermc.paper.event.player.PaperPlayerCustomClickEvent(io.papermc.paper.adventure.PaperAdventure.asAdventure(packet.id()), this.getApiConnection(), packet.payload().orElse(null));
        event.callEvent();
        io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.DIALOG_CLICK_MANAGER.tryRunCallback(this.getAudience(), packet.id(), packet.payload());
        io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.ADVENTURE_CLICK_MANAGER.tryRunCallback(this.getAudience(), packet.id(),  packet.payload());
        // Paper end - Implement click callbacks with custom click action
    }

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.server.packetProcessor());
        if (packet.action() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
            LOGGER.info("Disconnecting {} due to resource pack {} rejection", this.playerProfile().name(), packet.id());
            this.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"), io.papermc.paper.connection.DisconnectionReason.RESOURCE_PACK_REJECTION); // Paper - kick event cause
        }
        // Paper start - adventure pack callbacks
        // call the callbacks before the previously-existing event so the event has final say
        final net.kyori.adventure.resource.ResourcePackCallback callback;
        if (packet.action().isTerminal()) {
            callback = this.packCallbacks.remove(packet.id());
        } else {
            callback = this.packCallbacks.get(packet.id());
        }
        if (callback != null) {
            net.kyori.adventure.audience.Audience audience = switch (this) {
                case ServerGamePacketListenerImpl serverGamePacketListener -> serverGamePacketListener.getCraftPlayer();
                case ServerConfigurationPacketListenerImpl configurationPacketListener -> configurationPacketListener.paperConnection.getAudience();
                default -> throw new IllegalStateException("Unexpected value: " + this);
            };

            callback.packEventReceived(packet.id(), net.kyori.adventure.resource.ResourcePackStatus.valueOf(packet.action().name()), audience);
        }
        // Paper end
    }

    @Override
    public void handleCookieResponse(ServerboundCookieResponsePacket packet) {
        if (this.paperConnection().handleCookieResponse(packet)) return; // Paper
        this.disconnect(DISCONNECT_UNEXPECTED_QUERY, io.papermc.paper.connection.DisconnectionReason.INVALID_COOKIE); // Paper - kick event cause
    }

    protected void keepConnectionAlive() {
        Profiler.get().push("keepAlive");
        long millis = Util.getMillis();
        // Mili start - async keepalive
        if (fun.bm.mili.config.modules.function.OldFeatureConfig.asyncKeepalive) {
            this.checkIfClosed(millis);
            Profiler.get().pop();
            return;
        }
        // Mili end - async keepalive
        // Paper start - improve keepalives
        if (this.checkIfClosed(millis) && !this.processedDisconnect) {
            long currTime = System.nanoTime();

            if ((currTime - this.keepAlive.lastKeepAliveTx) >= java.util.concurrent.TimeUnit.SECONDS.toNanos(1L)) {
                this.keepAlive.lastKeepAliveTx = currTime;

                io.papermc.paper.util.KeepAlive.PendingKeepAlive pka = new io.papermc.paper.util.KeepAlive.PendingKeepAlive(currTime, millis);
                this.keepAlive.pendingKeepAlives.add(pka);
                this.send(new ClientboundKeepAlivePacket(pka.challengeId()));
            }

            io.papermc.paper.util.KeepAlive.PendingKeepAlive oldest = this.keepAlive.pendingKeepAlives.peek();
            if (oldest != null && (currTime - oldest.txTimeNS()) > java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(KEEPALIVE_LIMIT)) {
                LOGGER.info("{} was kicked due to keepalive timeout!", this.playerProfile().name());
                this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE, io.papermc.paper.connection.DisconnectionReason.TIMEOUT); // Paper - kick event cause
                // Paper end - improve keepalives
            }
        }

        Profiler.get().pop();
    }

    // Mili start - async keepalive
    public void keepConnectionAliveAsync(long currentTimeNs, long currentTimeMs) {
        if (this.closed || this.processedDisconnect || !this.connection.isConnected()) {
            return;
        }

        long timeoutNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(fun.bm.mili.config.modules.function.OldFeatureConfig.asyncKeepaliveTimeoutSeconds * 1000L);
        if ((currentTimeNs - this.keepAlive.lastKeepAliveTx) >= java.util.concurrent.TimeUnit.SECONDS.toNanos(1L)) {
            this.keepAlive.lastKeepAliveTx = currentTimeNs;
            io.papermc.paper.util.KeepAlive.PendingKeepAlive pending = new io.papermc.paper.util.KeepAlive.PendingKeepAlive(currentTimeNs, currentTimeMs);
            this.keepAlive.pendingKeepAlives.add(pending);
            this.send(new ClientboundKeepAlivePacket(pending.challengeId()));
        }
        while (this.keepAlive.pendingKeepAlives.pollIf((pending) -> (currentTimeNs - pending.txTimeNS()) > timeoutNanos) != null) {
        }

        if ((currentTimeNs - this.lastKeepAliveResponse) > timeoutNanos) {
            LOGGER.info("{} was kicked due to keepalive timeout!", this.playerProfile().name());
            this.disconnectAsync(TIMEOUT_DISCONNECTION_MESSAGE, io.papermc.paper.connection.DisconnectionReason.TIMEOUT);
        }
    }
    // Mili end - async keepalive

    private boolean checkIfClosed(long time) {
        if (this.closed) {
            if (time - this.closedListenerTime >= 15000L) {
                this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE, io.papermc.paper.connection.DisconnectionReason.TIMEOUT); // Paper - kick event cause
            }

            return false;
        } else {
            return true;
        }
    }

    public void suspendFlushing() {
        this.suspendFlushingOnServerThread = true;
    }

    public void resumeFlushing() {
        this.suspendFlushingOnServerThread = false;
        this.connection.flushChannel();
    }

    public void send(Packet<?> packet) {
        this.send(packet, null);
    }

    public void send(Packet<?> packet, @Nullable ChannelFutureListener sendListener) {
        // CraftBukkit start
        if (packet == null || this.processedDisconnect) { // Spigot
            return;
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket defaultSpawnPositionPacket && this instanceof ServerGamePacketListenerImpl serverGamePacketListener) {
            serverGamePacketListener.player.compassTarget = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(defaultSpawnPositionPacket.respawnData().pos(), serverGamePacketListener.getPlayer().level());
        }
        // CraftBukkit end
        if (packet.isTerminal()) {
            this.close();
        }

        boolean flag = !this.suspendFlushingOnServerThread || !this.server.isSameThread();

        try {
            this.connection.send(packet, sendListener, flag);
        } catch (Throwable var7) {
            CrashReport crashReport = CrashReport.forThrowable(var7, "Sending packet");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Packet being sent");
            crashReportCategory.setDetail("Packet class", () -> packet.getClass().getCanonicalName());
            throw new ReportedException(crashReport);
        }
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - kick event causes
    public void disconnect(Component reason) {
        // Paper start - kick event causes
        this.disconnect(reason, io.papermc.paper.connection.DisconnectionReason.UNKNOWN);
    }

    public void disconnect(Component reason, io.papermc.paper.connection.DisconnectionReason cause) {
        this.disconnect(new DisconnectionDetails(reason, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(cause)));
        // Paper end - kick event causes
    }

    public void disconnect(DisconnectionDetails disconnectionDetails) {
        // CraftBukkit start - fire PlayerKickEvent
        if (this.processedDisconnect) {
            return;
        }
        // Folia start - region threading
        boolean onMain;
        if (this instanceof ServerGamePacketListenerImpl serverGamePacketListener) {
            onMain = ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(serverGamePacketListener.player);
        } else if (this instanceof ServerConfigurationPacketListenerImpl configurationPacketListener) {
            net.minecraft.server.level.ServerPlayer player = configurationPacketListener.switchToMain;
            onMain = player == null ? io.papermc.paper.threadedregions.RegionizedServer.isGlobalTickThread() : ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player);
        } else {
            onMain = io.papermc.paper.threadedregions.RegionizedServer.isGlobalTickThread();
        }
        if (!onMain) {
            this.connection.disconnectSafely(disconnectionDetails); // it HAS to be delayed/async to avoid deadlock if we try to wait for another region
            // Folia end - region threading
            return;
        }

        Component reason;
        Component leaveMessage;
        if (this instanceof ServerGamePacketListenerImpl serverGamePacketListener) {
            net.kyori.adventure.text.Component rawLeaveMessage = net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? serverGamePacketListener.player.getBukkitEntity().displayName() : net.kyori.adventure.text.Component.text(serverGamePacketListener.player.getScoreboardName())); // Paper - Adventure

            net.minecraft.server.level.ServerPlayer player = serverGamePacketListener.player;
            org.bukkit.event.player.PlayerKickEvent.Cause cause = disconnectionDetails.disconnectionReason().orElseThrow().game().orElse(org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN);
            org.bukkit.event.player.PlayerKickEvent event = new org.bukkit.event.player.PlayerKickEvent(
                    player.getBukkitEntity(),
                    io.papermc.paper.adventure.PaperAdventure.asAdventure(disconnectionDetails.reason()),
                    rawLeaveMessage, cause

            );

            if (this.cserver.getServer().isRunning()) {
                this.cserver.getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                // Do not kick the player
                return;
            }

            reason = io.papermc.paper.adventure.PaperAdventure.asVanilla(event.reason());
            leaveMessage =  io.papermc.paper.adventure.PaperAdventure.asVanilla(event.leaveMessage());
            serverGamePacketListener.player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.KICKED; // Paper - Add API for quit reason
            // Log kick to console *after* event was processed.
            switch (cause) {
                case FLYING_PLAYER -> LOGGER.warn("{} was kicked for floating too long!", player.getPlainTextName());
                case FLYING_VEHICLE -> LOGGER.warn("{} was kicked for floating a vehicle too long!", player.getPlainTextName());
            }
        } else {
            // TODO: Add event for config event
            reason = disconnectionDetails.reason();
            leaveMessage = null;
        }

        // Send the possibly modified leave message
        this.disconnect0(new DisconnectionDetails(reason, disconnectionDetails.report(), disconnectionDetails.bugReportLink(), java.util.Optional.ofNullable(leaveMessage), disconnectionDetails.disconnectionReason()));
    }

    private void disconnect0(DisconnectionDetails disconnectionDetails) {
        this.connection
                .send(
                        new ClientboundDisconnectPacket(disconnectionDetails.reason()),
                        PacketSendListener.thenRun(() -> this.connection.disconnect(disconnectionDetails))
                );
        this.onDisconnect(disconnectionDetails);
        this.connection.setReadOnly();
        // CraftBukkit - Don't wait
        //this.server.scheduleOnMain(this.connection::handleDisconnection); // Paper // Folia - region threading
    }

    // Paper start - add proper async disconnect
    public final void disconnectAsync(Component component, io.papermc.paper.connection.DisconnectionReason reason) {
        this.disconnectAsync(new DisconnectionDetails(component, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(reason)));
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public final void disconnectAsync(Component component) {
        this.disconnectAsync(component, io.papermc.paper.connection.DisconnectionReason.UNKNOWN);
    }

    public abstract void disconnectAsync(DisconnectionDetails disconnectionInfo);

    public boolean isTransferred() {
        return this.transferred;
    }

    public abstract io.papermc.paper.connection.PaperCommonConnection<?> paperConnection();
    // Paper end - add proper async disconnect

    protected boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(new NameAndId(this.playerProfile()));
    }

    protected abstract GameProfile playerProfile();

    @VisibleForDebug
    public GameProfile getOwner() {
        return this.playerProfile();
    }

    public int latency() {
        return this.latency;
    }

    protected CommonListenerCookie createCookie(ClientInformation clientInformation) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, clientInformation, this.transferred, this.playerBrand, this.pluginMessagerChannels, this.keepAlive); // Paper
    }
}
