package io.papermc.paper.threadedregions;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.completable.Completable;
import ca.spottedleaf.concurrentutil.util.TimeUtil;
import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.logging.LogUtils;
import io.papermc.paper.threadedregions.scheduler.FoliaGlobalRegionScheduler;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.gamerules.GameRules;
import org.bukkit.Bukkit;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class RegionizedServer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RegionizedServer INSTANCE = new RegionizedServer();

    public final RegionizedTaskQueue taskQueue = new RegionizedTaskQueue();

    private final CopyOnWriteArrayList<ServerLevel> worlds = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();

    private final MultiThreadedQueue<Runnable> globalTickQueue = new MultiThreadedQueue<>();

    private final GlobalTickTickHandle tickHandle = new GlobalTickTickHandle(this);

    public final PacketProcessor packetProcessor = new PacketProcessor(null);

    public static RegionizedServer getInstance() {
        return INSTANCE;
    }

    public void addConnection(final Connection conn) {
        this.connections.add(conn);
    }

    public boolean removeConnection(final Connection conn) {
        return this.connections.remove(conn);
    }

    public void addWorld(final ServerLevel world) {
        this.worlds.add(world);
    }

    public void init() {
        // call init event _before_ scheduling anything
        new RegionizedServerInitEvent().callEvent();

        // now we can schedule
        this.tickHandle.setInitialStart(System.nanoTime() + TickRegionScheduler.TIME_BETWEEN_TICKS);
        TickRegions.getScheduler().scheduleRegion(this.tickHandle);
        TickRegions.start();
    }

    public void invalidateStatus() {
        this.lastServerStatus = 0L;
    }

    public void addTaskWithoutNotify(final Runnable run) {
        this.globalTickQueue.add(run);
    }

    public void addTask(final Runnable run) {
        this.addTaskWithoutNotify(run);
        this.notifyTasks();
    }

    public void notifyTasks() {
        TickRegions.getScheduler().setHasTasks(this.tickHandle);
    }

    private boolean hasAnyGlobalChunkTasks() {
        for (final ServerLevel world : this.worlds) {
            if (world.taskQueueRegionData.hasGlobalChunkTasks()) {
                return true;
            }
        }
        return false;
    }

    public <T extends PacketListener> void schedulePacket(final T packetListener, final Packet<T> packet) {
        if (this.packetProcessor.scheduleIfPossible(packetListener, packet)) {
            this.notifyTasks();
        }
    }

    public void blockOn(final Runnable run) {
        if (isGlobalTickThread()) {
            run.run();
            return;
        }
        Completable.suppliedAsync(() -> {
            run.run();
            return null;
        }, this::addTask).join();
        return;
    }

    /**
     * Returns the current tick of the region ticking.
     * @throws IllegalStateException If there is no current region.
     */
    public static long getCurrentTick() throws IllegalStateException {
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
            TickRegionScheduler.getCurrentRegion();
        if (region == null) {
            if (TickThread.isShutdownThread()) {
                return 0L;
            }
            throw new IllegalStateException("No currently ticking region");
        }
        return region.getData().getCurrentTick();
    }

    public static boolean isGlobalTickThread() {
        return INSTANCE.tickHandle == TickRegionScheduler.getCurrentTickingTask();
    }

    public static void ensureGlobalTickThread(final String reason) {
        if (!isGlobalTickThread()) {
            throw new IllegalStateException(reason);
        }
    }

    public static TickRegionScheduler.RegionScheduleHandle getGlobalTickData() {
        return INSTANCE.tickHandle;
    }

    private static final class GlobalTickTickHandle extends TickRegionScheduler.RegionScheduleHandle {

        private final RegionizedServer server;

        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final AtomicBoolean ticking = new AtomicBoolean();

        public GlobalTickTickHandle(final RegionizedServer server) {
            super(null, TimeUtil.DEADLINE_NOT_SET);
            this.server = server;
        }

        /**
         * Only valid to call BEFORE scheduled!!!!
         */
        final void setInitialStart(final long start) {
            if (this.scheduled.getAndSet(true)) {
                throw new IllegalStateException("Double scheduling global tick");
            }
            this.updateScheduledStart(start);
        }

        @Override
        protected boolean tryMarkTicking() {
            return !this.ticking.getAndSet(true);
        }

        @Override
        protected boolean markNotTicking() {
            return this.ticking.getAndSet(false);
        }

        @Override
        protected void tickRegion(final long tickCount, final long startTime, final long scheduledEnd) {
            this.drainTasks();
            this.server.globalTick(tickCount);
        }

        private void drainTasks() {
            while (this.runOneTask() || this.runOnePacketTask());
        }

        private boolean runOneTask() {
            final Runnable run = this.server.globalTickQueue.poll();
            if (run == null) {
                return false;
            }

            // TODO try catch?
            run.run();

            return true;
        }

        private boolean runOnePacketTask() {
            return this.server.packetProcessor.executeSinglePacket();
        }

        private boolean runGlobalTask() {
            boolean ret = false;
            for (final ServerLevel world : this.server.worlds) {
                ret |= world.taskQueueRegionData.executeGlobalChunkTask();
            }
            return ret;
        }

        @Override
        protected void runRegionTasks(final BooleanSupplier canContinue) {
            do {
                if (!this.runOneTask() && !this.runOnePacketTask() && !this.runGlobalTask()) {
                    return;
                }
            } while (canContinue.getAsBoolean());
        }

        @Override
        protected boolean hasIntermediateTasks() {
            return !this.server.globalTickQueue.isEmpty() || this.server.packetProcessor.hasPackets() || this.server.hasAnyGlobalChunkTasks();
        }
    }

    private long lastServerStatus;
    private long tickCount;

    /*
    private final java.util.Random random = new java.util.Random(4L);
    private final List<io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void>> walkers =
        new java.util.ArrayList<>();
    static final int PLAYERS = 500;
    static final int RAD_BLOCKS = 1000;
    static final int RAD = RAD_BLOCKS >> 4;
    static final int RAD_BIG_BLOCKS = 100_000;
    static final int RAD_BIG = RAD_BIG_BLOCKS >> 4;
    static final int VD = 4 + 12;
    static final int BIG_PLAYERS = 250;
    static final double WALK_CHANCE = 0.3;
    static final double TP_CHANCE = 0.2;
    static final double TASK_CHANCE = 0.2;

    private ServerLevel getWorld() {
        return this.worlds.get(0);
    }

    private void init2() {
        for (int i = 0; i < PLAYERS; ++i) {
            int rad = i < BIG_PLAYERS ? RAD_BIG : RAD;
            int posX = this.random.nextInt(-rad, rad + 1);
            int posZ = this.random.nextInt(-rad, rad + 1);

            io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void> map = new io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<>(null) {
                @Override
                protected void addCallback(Void parameter, int chunkX, int chunkZ) {
                    ServerLevel world = RegionizedServer.this.getWorld();
                    if (RegionizedServer.this.random.nextDouble() <= TASK_CHANCE) {
                        RegionizedServer.this.taskQueue.queueChunkTask(world, chunkX, chunkZ, () -> {
                            RegionizedServer.this.taskQueue.queueChunkTask(world, chunkX, chunkZ, () -> {});
                        });
                    }
                    world.chunkTaskScheduler.chunkHolderManager.addTicketAtLevel(
                        net.minecraft.server.level.TicketType.PLAYER, chunkX, chunkZ, io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL, new net.minecraft.world.level.ChunkPos(posX, posZ)
                    );
                }

                @Override
                protected void removeCallback(Void parameter, int chunkX, int chunkZ) {
                    ServerLevel world = RegionizedServer.this.getWorld();
                    if (RegionizedServer.this.random.nextDouble() <= TASK_CHANCE) {
                        RegionizedServer.this.taskQueue.queueChunkTask(world, chunkX, chunkZ, () -> {
                            RegionizedServer.this.taskQueue.queueChunkTask(world, chunkX, chunkZ, () -> {});
                        });
                    }
                    world.chunkTaskScheduler.chunkHolderManager.removeTicketAtLevel(
                        net.minecraft.server.level.TicketType.PLAYER, chunkX, chunkZ, io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL, new net.minecraft.world.level.ChunkPos(posX, posZ)
                    );
                }
            };

            map.add(posX, posZ, VD);

            walkers.add(map);
        }
    }

    private void randomWalk() {
        if (this.walkers.isEmpty()) {
            this.init2();
            return;
        }

        for (int i = 0; i < PLAYERS; ++i) {
            if (this.random.nextDouble() > WALK_CHANCE) {
                continue;
            }

            io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void> map = this.walkers.get(i);

            int updateX = this.random.nextInt(-1, 2);
            int updateZ = this.random.nextInt(-1, 2);

            map.update(map.lastChunkX + updateX, map.lastChunkZ + updateZ, VD);
        }

        for (int i = 0; i < PLAYERS; ++i) {
            if (random.nextDouble() >= TP_CHANCE) {
                continue;
            }

            int rad = i < BIG_PLAYERS ? RAD_BIG : RAD;
            int posX = random.nextInt(-rad, rad + 1);
            int posZ = random.nextInt(-rad, rad + 1);

            io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.SingleUserAreaMap<Void> map = walkers.get(i);

            map.update(posX, posZ, VD);
        }
    }
     */

    private void globalTick(final long tickCount) {
        /*
        if (false) {
            io.papermc.paper.threadedregions.ThreadedTicketLevelPropagator.main(null);
        }
        this.randomWalk();
         */
        final long tickStartNanos = System.nanoTime();
        ++this.tickCount;
        // expire invalid click command callbacks
        io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.ADVENTURE_CLICK_MANAGER.handleQueue((int)this.tickCount); // Paper // Folia - region threading - moved to global tick
        io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.DIALOG_CLICK_MANAGER.handleQueue((int)this.tickCount); // Paper // Folia - region threading - moved to global tick

        // scheduler
        ((FoliaGlobalRegionScheduler)Bukkit.getGlobalRegionScheduler()).tick();

        // commands
        ((DedicatedServer)MinecraftServer.getServer()).handleConsoleInputs();

        // needs
        // player ping sample
        // world global tick
        // connection tick

        // tick player ping sample
        this.tickPlayerSample();

        // tick worlds
        for (final ServerLevel world : this.worlds) {
            this.globalTick(world, tickCount);
        }

        fun.bm.mili.protocol.tiscm.TISCMProtocol.broadcastMsptSample(this.tickCount, System.nanoTime() - tickStartNanos);

        // tick connections
        this.tickConnections();

        // player list
        MinecraftServer.getServer().getPlayerList().tick();
    }

    private void tickPlayerSample() {
        final MinecraftServer mcServer = MinecraftServer.getServer();

        final long currtime = System.nanoTime();

        // player ping sample
        // copied from MinecraftServer#tickServer
        // note: we need to reorder setPlayers to be the last operation it does, rather than the first to avoid publishing
        // an uncomplete status
        if (currtime - this.lastServerStatus >= MinecraftServer.STATUS_EXPIRE_TIME_NANOS) {
            this.lastServerStatus = currtime;
            mcServer.rebuildServerStatus();
        }
    }

    public static boolean isNotOwnedByGlobalRegion(final Connection conn) {
        final PacketListener packetListener = conn.getPacketListener();

        if (packetListener instanceof ServerGamePacketListenerImpl gamePacketListener) {
            return !gamePacketListener.waitingForSwitchToConfig;
        }

        if (conn.getPacketListener() instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configurationPacketListener) {
            return configurationPacketListener.switchToMain != null;
        }

        return false;
    }

    private void tickConnections() {
        final List<Connection> connections = new ArrayList<>(this.connections);
        Collections.shuffle(connections); // shuffle to prevent people from "gaming" the server by re-logging
        for (final Connection conn : connections) {
            if (!conn.becomeActive()) {
                continue;
            }

            if (isNotOwnedByGlobalRegion(conn)) {
                // we actually require that the owning regions remove the connection for us, as it is possible
                // that ownership is transferred back to us
                continue;
            }

            if (!conn.isConnected()) {
                this.removeConnection(conn);
                conn.handleDisconnection();
                continue;
            }

            try {
                conn.tick();
            } catch (final Exception exception) {
                if (conn.isMemoryConnection()) {
                    throw new ReportedException(CrashReport.forThrowable(exception, "Ticking memory connection"));
                }

                LOGGER.warn("Failed to handle packet for {}", conn.getLoggableAddress(MinecraftServer.getServer().logIPs()), exception);
                MutableComponent ichatmutablecomponent = Component.literal("Internal server error");

                conn.send(new ClientboundDisconnectPacket(ichatmutablecomponent), PacketSendListener.thenRun(() -> {
                    conn.disconnect(ichatmutablecomponent);
                }));
                conn.setReadOnly();
                continue;
            }
        }
    }

    // A global tick only updates things like weather / worldborder, basically anything in the world that is
    // NOT tied to a specific region, but rather shared amongst all of them.
    private void globalTick(final ServerLevel world, final long tickCount) {
        // needs
        // worldborder tick
        // advancing the weather cycle
        // sleep status thing
        // updating sky brightness
        // time ticking (game time + daylight), plus PrimayLevelDat#getScheduledEvents ticking

        // Typically, we expect there to be a running region to drain a world's global chunk tasks. However,
        // this may not be the case - and thus, only the global tick thread can do anything.
        world.taskQueueRegionData.drainGlobalChunkTasks();

        // worldborder tick
        this.tickWorldBorder(world);

        // weather cycle
        this.advanceWeatherCycle(world);

        // sleep status
        this.checkNightSkip(world);

        // update raids
        this.updateRaids(world);

        // sky brightness
        this.updateSkyBrightness(world);

        // time ticking (TODO API synchronisation?)
        this.tickTime(world, tickCount);

        world.updateTickData();

        world.moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates(); // required to eventually process ticket updates

        this.autoSaveMaps(world);
    }

    private void updateRaids(final ServerLevel world) {
        world.getRaids().globalTick();
    }

    private void checkNightSkip(final ServerLevel world) {
        world.tickSleep();
    }

    private void advanceWeatherCycle(final ServerLevel world) {
        world.advanceWeatherCycle();
    }

    private void updateSkyBrightness(final ServerLevel world) {
        world.updateSkyBrightness();
    }

    private void tickWorldBorder(final ServerLevel world) {
        world.getWorldBorder().tick();
    }

    private void tickTime(final ServerLevel world, final long tickCount) {
        if (world.tickTime) {
            if (world.getGameRules().get(GameRules.ADVANCE_TIME)) {
                world.setDayTime(world.levelData.getDayTime() + tickCount);
            }
            world.serverLevelData.setGameTime(world.serverLevelData.getGameTime() + tickCount);
        }
    }

    private void autoSaveMaps(final ServerLevel world) {
        final int autoSavePeriod = net.minecraft.server.MinecraftServer.getServer().autosavePeriod;
        final long currTime = System.nanoTime();
        if (autoSavePeriod > 0 && (currTime - world.lastMapAutoSave) > (java.util.concurrent.TimeUnit.SECONDS.toNanos((long)autoSavePeriod) / 20L)) {
            world.lastMapAutoSave = currTime;
            world.getDataStorage().saveMaps();
        }
    }

    public static final record WorldLevelData(ServerLevel world, long nonRedstoneGameTime, long dayTime) {

    }
}
