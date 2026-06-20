package net.minecraft.world.entity.ai.village.poi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.debug.DebugPoiInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PoiSection implements ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiSection { // Paper - rewrite chunk system
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Short2ObjectMap<PoiRecord> records = new Short2ObjectOpenHashMap<>();
    private final Map<Holder<PoiType>, Set<PoiRecord>> byType = Maps.newHashMap(); public final Map<Holder<PoiType>, Set<PoiRecord>> getData() { return this.byType; } // Paper - public accessor
    private final Runnable setDirty;
    private boolean isValid;

    // Paper start - rewrite chunk system
    private final Optional<PoiSection> noAllocOptional = Optional.of((PoiSection)(Object)this);

    @Override
    public final boolean moonrise$isEmpty() {
        return this.isValid && this.records.isEmpty() && this.byType.isEmpty();
    }

    @Override
    public final Optional<PoiSection> moonrise$asOptional() {
        return this.noAllocOptional;
    }
    // Paper end - rewrite chunk system

    public PoiSection(Runnable setDirty) {
        this(setDirty, true, ImmutableList.of());
    }

    PoiSection(Runnable setDirty, boolean isValid, List<PoiRecord> records) {
        this.setDirty = setDirty;
        this.isValid = isValid;
        records.forEach(this::add);
    }

    public PoiSection.Packed pack() {
        return new PoiSection.Packed(this.isValid, this.records.values().stream().map(PoiRecord::pack).toList());
    }

    public Stream<PoiRecord> getRecords(Predicate<Holder<PoiType>> typePredicate, PoiManager.Occupancy status) {
        return this.byType
            .entrySet()
            .stream()
            .filter(entry -> typePredicate.test(entry.getKey()))
            .flatMap(entry -> entry.getValue().stream())
            .filter(status.getTest());
    }

    public @Nullable PoiRecord add(BlockPos pos, Holder<PoiType> poiType) {
        PoiRecord poiRecord = new PoiRecord(pos, poiType, this.setDirty);
        if (this.add(poiRecord)) {
            LOGGER.debug("Added POI of type {} @ {}", poiType.getRegisteredName(), pos);
            this.setDirty.run();
            return poiRecord;
        } else {
            return null;
        }
    }

    private boolean add(PoiRecord _record) {
        BlockPos pos = _record.getPos();
        Holder<PoiType> poiType = _record.getPoiType();
        short s = SectionPos.sectionRelativePos(pos);
        PoiRecord poiRecord = this.records.get(s);
        if (poiRecord != null) {
            if (poiType.equals(poiRecord.getPoiType())) {
                return false;
            }

            Util.logAndPauseIfInIde("POI data mismatch: already registered at " + pos);
        }

        this.records.put(s, _record);
        this.byType.computeIfAbsent(poiType, holder -> Sets.newHashSet()).add(_record);
        return true;
    }

    public void remove(BlockPos pos) {
        PoiRecord poiRecord = this.records.remove(SectionPos.sectionRelativePos(pos));
        if (poiRecord == null) {
            LOGGER.error("POI data mismatch: never registered at {}", pos);
        } else {
            this.byType.get(poiRecord.getPoiType()).remove(poiRecord);
            LOGGER.debug("Removed POI of type {} @ {}", LogUtils.defer(poiRecord::getPoiType), LogUtils.defer(poiRecord::getPos));
            this.setDirty.run();
        }
    }

    @Deprecated
    @VisibleForDebug
    public int getFreeTickets(BlockPos pos) {
        return this.getPoiRecord(pos).map(PoiRecord::getFreeTickets).orElse(0);
    }

    public boolean release(BlockPos pos) {
        PoiRecord poiRecord = this.records.get(SectionPos.sectionRelativePos(pos));
        if (poiRecord == null) {
            throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("POI never registered at " + pos));
        } else {
            boolean flag = poiRecord.releaseTicket();
            this.setDirty.run();
            return flag;
        }
    }

    public boolean exists(BlockPos pos, Predicate<Holder<PoiType>> typePredicate) {
        return this.getType(pos).filter(typePredicate).isPresent();
    }

    public Optional<Holder<PoiType>> getType(BlockPos pos) {
        return this.getPoiRecord(pos).map(PoiRecord::getPoiType);
    }

    private Optional<PoiRecord> getPoiRecord(BlockPos pos) {
        return Optional.ofNullable(this.records.get(SectionPos.sectionRelativePos(pos)));
    }

    public Optional<DebugPoiInfo> getDebugPoiInfo(BlockPos pos) {
        return this.getPoiRecord(pos).map(DebugPoiInfo::new);
    }

    public void refresh(Consumer<BiConsumer<BlockPos, Holder<PoiType>>> posToTypeConsumer) {
        if (!this.isValid) {
            Short2ObjectMap<PoiRecord> map = new Short2ObjectOpenHashMap<>(this.records);
            this.clear();
            posToTypeConsumer.accept((blockPos, holder) -> {
                short s = SectionPos.sectionRelativePos(blockPos);
                PoiRecord poiRecord = map.computeIfAbsent(s, s1 -> new PoiRecord(blockPos, holder, this.setDirty));
                this.add(poiRecord);
            });
            this.isValid = true;
            this.setDirty.run();
        }
    }

    private void clear() {
        this.records.clear();
        this.byType.clear();
    }

    boolean isValid() {
        return this.isValid;
    }

    public record Packed(boolean isValid, List<PoiRecord.Packed> records) {
        public static final Codec<PoiSection.Packed> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.BOOL.lenientOptionalFieldOf("Valid", false).forGetter(PoiSection.Packed::isValid),
                    PoiRecord.Packed.CODEC.listOf().fieldOf("Records").forGetter(PoiSection.Packed::records)
                )
                .apply(instance, PoiSection.Packed::new)
        );

        public PoiSection unpack(Runnable setDirty) {
            return new PoiSection(setDirty, this.isValid, this.records.stream().map(packed -> packed.unpack(setDirty)).toList());
        }
    }
}
