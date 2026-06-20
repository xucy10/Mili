package net.minecraft.world.level;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TicketStorage extends SavedData implements ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketStorage { // Paper - rewrite chunk system
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Pair<ChunkPos, Ticket>> TICKET_ENTRY = Codec.mapPair(ChunkPos.CODEC.fieldOf("chunk_pos"), Ticket.CODEC).codec();
    public static final Codec<TicketStorage> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(TICKET_ENTRY.listOf().optionalFieldOf("tickets", List.of()).forGetter(TicketStorage::packTickets))
            .apply(instance, TicketStorage::fromPacked)
    );
    public static final SavedDataType<TicketStorage> TYPE = new SavedDataType<>("chunks", TicketStorage::new, CODEC, DataFixTypes.SAVED_DATA_FORCED_CHUNKS);
    // Paper - rewrite chunk system
    private final Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets;
    // Paper - rewrite chunk system
    private TicketStorage.@Nullable ChunkUpdated loadingChunkUpdatedListener;
    private TicketStorage.@Nullable ChunkUpdated simulationChunkUpdatedListener;

    // Paper start - rewrite chunk system
    private ChunkMap chunkMap;

    @Override
    public final ChunkMap moonrise$getChunkMap() {
        return this.chunkMap;
    }

    @Override
    public final void moonrise$setChunkMap(final ChunkMap chunkMap) {
        this.chunkMap = chunkMap;
    }
    // Paper end - rewrite chunk system

    private TicketStorage(Long2ObjectOpenHashMap<List<Ticket>> tickets, Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets) {
        // Paper - rewrite chunk system
        this.deactivatedTickets = deactivatedTickets;
        // Paper - rewrite chunk system
    }

    public TicketStorage() {
        this(new Long2ObjectOpenHashMap<>(4), new Long2ObjectOpenHashMap<>());
    }

    private static TicketStorage fromPacked(List<Pair<ChunkPos, Ticket>> packed) {
        Long2ObjectOpenHashMap<List<Ticket>> map = new Long2ObjectOpenHashMap<>();

        for (Pair<ChunkPos, Ticket> pair : packed) {
            ChunkPos chunkPos = pair.getFirst();
            List<Ticket> list = map.computeIfAbsent(chunkPos.toLong(), l -> new ObjectArrayList<>(4));
            list.add(pair.getSecond());
        }

        return new TicketStorage(new Long2ObjectOpenHashMap<>(4), map);
    }

    private List<Pair<ChunkPos, Ticket>> packTickets() {
        List<Pair<ChunkPos, Ticket>> list = new ArrayList<>();
        this.forEachTicket((chunkPos, ticket) -> {
            if (ticket.getType().persist()) {
                list.add(new Pair<>(chunkPos, ticket));
            }
        });
        return list;
    }

    // Paper start - rewrite chunk system
    private void redirectRegularTickets(final BiConsumer<ChunkPos, Ticket> consumer, final Long2ObjectOpenHashMap<List<Ticket>> ticketsParam) {
        if (ticketsParam != null) {
            throw new IllegalStateException("Bad injection point");
        }

        final Long2ObjectOpenHashMap<java.util.Collection<net.minecraft.server.level.Ticket>> tickets = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.chunkMap.level)
            .moonrise$getChunkTaskScheduler().chunkHolderManager.getTicketsCopy();

        for (final Iterator<it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<java.util.Collection<net.minecraft.server.level.Ticket>>> iterator = tickets.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            final it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<java.util.Collection<net.minecraft.server.level.Ticket>> entry = iterator.next();

            final long pos = entry.getLongKey();
            final java.util.Collection<net.minecraft.server.level.Ticket> chunkTickets = entry.getValue();

            final ChunkPos chunkPos = new ChunkPos(pos);

            for (final Ticket ticket : chunkTickets) {
                consumer.accept(chunkPos, ticket);
            }
        }
    }
    // Paper end - rewrite chunk system

    private void forEachTicket(BiConsumer<ChunkPos, Ticket> action) {
        this.redirectRegularTickets(action, null); // Paper - rewrite chunk system
        forEachTicket(action, this.deactivatedTickets);
    }

    private static void forEachTicket(BiConsumer<ChunkPos, Ticket> action, Long2ObjectOpenHashMap<List<Ticket>> tickets) {
        for (Entry<List<Ticket>> entry : Long2ObjectMaps.fastIterable(tickets)) {
            ChunkPos chunkPos = new ChunkPos(entry.getLongKey());

            for (Ticket ticket : entry.getValue()) {
                action.accept(chunkPos, ticket);
            }
        }
    }

    public void activateAllDeactivatedTickets() {
        for (Entry<List<Ticket>> entry : Long2ObjectMaps.fastIterable(this.deactivatedTickets)) {
            for (Ticket ticket : entry.getValue()) {
                this.addTicket(entry.getLongKey(), ticket);
            }
        }

        this.deactivatedTickets.clear();
    }

    public void setLoadingChunkUpdatedListener(TicketStorage.@Nullable ChunkUpdated loadingChunkUpdatedListener) {
        // Paper - rewrite chunk system
    }

    public void setSimulationChunkUpdatedListener(TicketStorage.@Nullable ChunkUpdated simulationChunkUpdatedListener) {
        // Paper - rewrite chunk system
    }

    public boolean hasTickets() {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager.hasTickets(); // Paper - rewrite chunk system
    }

    public boolean shouldKeepDimensionActive() {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.concurrentutil.map.ConcurrentLong2LongChainedHashTable ticketCounters = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager
            .getTicketCounters(ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType.COUNTER_TYPE_KEEP_DIMENSION_ACTIVE);
        return ticketCounters != null && !ticketCounters.isEmpty();
        // Paper end - rewrite chunk system
    }

    public List<Ticket> getTickets(long chunkPos) {
        // Paper start - rewrite chunk system
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager
            .getTicketsAt(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(chunkPos), ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(chunkPos));
        // Paper end - rewrite chunk system
    }

    private List<Ticket> getOrCreateTickets(long chunkPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void addTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int radius) {
        Ticket ticket = new Ticket(ticketType, ChunkLevel.byStatus(FullChunkStatus.FULL) - radius);
        this.addTicket(chunkPos.toLong(), ticket);
    }

    public void addTicket(Ticket ticket, ChunkPos chunkPos) {
        this.addTicket(chunkPos.toLong(), ticket);
    }

    public boolean addTicket(long chunkPos, Ticket ticket) {
        // Paper start - rewrite chunk system
        final boolean ret = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager
            .addTicketAtLevel(ticket.getType(), chunkPos, ticket.getTicketLevel(), ((ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicket<?>)ticket).moonrise$getIdentifier());

        this.setDirty();

        return ret;
        // Paper end - rewrite chunk system
    }

    private static boolean isTicketSameTypeAndLevel(Ticket first, Ticket second) {
        return second.getType() == first.getType() && second.getTicketLevel() == first.getTicketLevel() && java.util.Objects.equals(second.getIdentifier(), first.getIdentifier()); // Paper - add identifier
    }

    public int getTicketLevelAt(long chunkPos, boolean requireSimulation) {
        return getTicketLevelAt(this.getTickets(chunkPos), requireSimulation);
    }

    private static int getTicketLevelAt(List<Ticket> tickets, boolean requireSimulation) {
        Ticket lowestTicket = getLowestTicket(tickets, requireSimulation);
        return lowestTicket == null ? ChunkLevel.MAX_LEVEL + 1 : lowestTicket.getTicketLevel();
    }

    private static @Nullable Ticket getLowestTicket(@Nullable List<Ticket> tickets, boolean requireSimulation) {
        if (tickets == null) {
            return null;
        } else {
            Ticket ticket = null;

            for (Ticket ticket1 : tickets) {
                if (ticket == null || ticket1.getTicketLevel() < ticket.getTicketLevel()) {
                    if (requireSimulation && ticket1.getType().doesSimulate()) {
                        ticket = ticket1;
                    } else if (!requireSimulation && ticket1.getType().doesLoad()) {
                        ticket = ticket1;
                    }
                }
            }

            return ticket;
        }
    }

    public void removeTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int radius) {
        Ticket ticket = new Ticket(ticketType, ChunkLevel.byStatus(FullChunkStatus.FULL) - radius);
        this.removeTicket(chunkPos.toLong(), ticket);
    }

    public void removeTicket(Ticket ticket, ChunkPos chunkPos) {
        this.removeTicket(chunkPos.toLong(), ticket);
    }

    public boolean removeTicket(long chunkPos, Ticket ticket) {
        // Paper start - rewrite chunk system
        final boolean ret = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager
            .removeTicketAtLevel(ticket.getType(), chunkPos, ticket.getTicketLevel(), ((ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicket<?>)ticket).moonrise$getIdentifier());

        if (ret) {
            this.setDirty();
        }

        return ret;
        // Paper end - rewrite chunk system
    }

    private void updateForcedChunks() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public String getTicketDebugString(long chunkPos, boolean requireSimulation) {
        List<Ticket> tickets = this.getTickets(chunkPos);
        Ticket lowestTicket = getLowestTicket(tickets, requireSimulation);
        return lowestTicket == null ? "no_ticket" : lowestTicket.toString();
    }

    public void purgeStaleTickets(ChunkMap map) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager.tick(); // Paper - rewrite chunk system
        this.setDirty();
    }

    private boolean canTicketExpire(ChunkMap map, Ticket ticket, long chunkPos) {
        if (!ticket.getType().hasTimeout()) {
            return false;
        } else if (ticket.getType().canExpireIfUnloaded()) {
            return true;
        } else {
            ChunkHolder updatingChunkIfPresent = map.getUpdatingChunkIfPresent(chunkPos);
            return updatingChunkIfPresent == null || updatingChunkIfPresent.isReadyForSaving();
        }
    }

    public void deactivateTicketsOnClosing() {
        // Paper - rewrite chunk system
    }

    public void removeTicketIf(TicketStorage.TicketPredicate predicate, @Nullable Long2ObjectOpenHashMap<List<Ticket>> tickets) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void replaceTicketLevelOfType(int level, TicketType ticketType) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean updateChunkForced(ChunkPos chunkPos, boolean add) {
        Ticket ticket = new Ticket(TicketType.FORCED, ChunkMap.FORCED_TICKET_LEVEL);
        return add ? this.addTicket(chunkPos.toLong(), ticket) : this.removeTicket(chunkPos.toLong(), ticket);
    }

    public LongSet getForceLoadedChunks() {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.concurrentutil.map.ConcurrentLong2LongChainedHashTable forced = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getTicketCounters(ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType.COUNTER_TYPE_FORCED);

        if (forced == null) {
            return new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
        }

       // note: important to presize correctly using size/loadfactor to avoid awful write performance
       //       think: iteration over our map has the same hash strategy, and if ret is not sized
       //       correctly then every (ret.table.length) may collide. During resize, open hashed tables
       //       (like LongLinkedOpenHashSet) must reinsert - leading to O(n^2) to copy IF we do not initially
       //       size correctly
       final it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet ret = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet(forced.size(), forced.getLoadFactor());
       for (final java.util.PrimitiveIterator.OfLong iterator = forced.keyIterator(); iterator.hasNext();) {
           ret.add(iterator.nextLong());
       }
       return ret;
        // Paper end - rewrite chunk system
    }

    private LongSet getAllChunksWithTicketThat(Predicate<Ticket> predicate) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @FunctionalInterface
    public interface ChunkUpdated {
        void update(long chunkPos, int ticketLevel, boolean isDecreasing);
    }

    public interface TicketPredicate {
        boolean test(Ticket ticket, long chunkPos);
    }

    // Paper start
    public boolean addPluginRegionTicket(final ChunkPos pos, final org.bukkit.plugin.Plugin value) {
        // Keep inline with force loading
        return addTicket(pos.toLong(), new Ticket(TicketType.PLUGIN_TICKET, ChunkMap.FORCED_TICKET_LEVEL, value));
    }

    public boolean removePluginRegionTicket(final ChunkPos pos, final org.bukkit.plugin.Plugin value) {
        // Keep inline with force loading
        return removeTicket(pos.toLong(), new Ticket(TicketType.PLUGIN_TICKET, ChunkMap.FORCED_TICKET_LEVEL, value));
    }

    public void removeAllPluginRegionTickets(TicketType ticketType, int ticketLevel, org.bukkit.plugin.Plugin ticketIdentifier) {
        this.chunkMap.level.moonrise$getChunkTaskScheduler().chunkHolderManager.removeAllTicketsFor(ticketType, ticketLevel, ticketIdentifier); // Paper - rewrite chunk system
    }
    // Paper end
}
