package net.minecraft.world.level.chunk;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.util.ZeroBitStorage;
import org.jspecify.annotations.Nullable;

public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T> {
    private static final int MIN_PALETTE_BITS = 0;
    public volatile PalettedContainer.Data<T> data; // Paper - optimise collisions - public
    private final Strategy<T> strategy;
    private final T @org.jetbrains.annotations.Nullable [] presetValues; // Paper - Anti-Xray - Add preset values
    //private final ThreadingDetector threadingDetector = new ThreadingDetector("PalettedContainer"); // Paper - unused

    public void acquire() {
        // this.threadingDetector.checkAndLock(); // Paper - disable this - use proper synchronization
    }

    public void release() {
        // this.threadingDetector.checkAndUnlock(); // Paper - disable this - use proper synchronization
    }

    // Paper start - Anti-Xray - Add preset values
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public static <T> Codec<PalettedContainer<T>> codecRW(Codec<T> valueCodec, Strategy<T> strategy, T defaultValue) {
        return PalettedContainer.codecRW(valueCodec, strategy, defaultValue, null);
    }
    public static <T> Codec<PalettedContainer<T>> codecRW(Codec<T> valueCodec, Strategy strategy, T defaultValue, T @org.jetbrains.annotations.Nullable [] presetValues) {
        PalettedContainerRO.Unpacker<T, PalettedContainer<T>> unpacker = (strategy1, packedData) -> unpack(strategy1, packedData, defaultValue, presetValues);
        // Paper end - Anti-Xray
        return codec(valueCodec, strategy, defaultValue, unpacker);
     }

    public static <T> Codec<PalettedContainerRO<T>> codecRO(Codec<T> valueCodec, Strategy<T> strategy, T defaultValue) {
        PalettedContainerRO.Unpacker<T, PalettedContainerRO<T>> unpacker = (strategy1, packedData) -> unpack(strategy1, packedData, defaultValue, null) // Paper - Anti-Xray - Add preset values
            .map(container -> (PalettedContainerRO<T>)container);
        return codec(valueCodec, strategy, defaultValue, unpacker);
    }

    private static <T, C extends PalettedContainerRO<T>> Codec<C> codec(
        Codec<T> valueCodec, Strategy<T> strategy, T defaultValue, PalettedContainerRO.Unpacker<T, C> unpacker
    ) {
        return RecordCodecBuilder.<PalettedContainerRO.PackedData>create(
                instance -> instance.group(
                        valueCodec.mapResult(ExtraCodecs.orElsePartial(defaultValue))
                            .listOf()
                            .fieldOf("palette")
                            .forGetter(PalettedContainerRO.PackedData::paletteEntries),
                        Codec.LONG_STREAM.lenientOptionalFieldOf("data").forGetter(PalettedContainerRO.PackedData::storage)
                    )
                    .apply(instance, PalettedContainerRO.PackedData::new)
            )
            .comapFlatMap(packedData -> unpacker.read(strategy, (PalettedContainerRO.PackedData<T>)packedData), container -> container.pack(strategy));
    }

    // Paper start - optimise palette reads
    private void updateData(final PalettedContainer.Data<T> data) {
        if (data != null) {
            ((ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T>)(Object)data).moonrise$setPalette(
                ((ca.spottedleaf.moonrise.patches.fast_palette.FastPalette<T>)data.palette).moonrise$getRawPalette((ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T>)(Object)data)
            );
        }
    }

    private T readPaletteSlow(final PalettedContainer.Data<T> data, final int paletteIdx) {
        return data.palette.valueFor(paletteIdx);
    }

    private T readPalette(final PalettedContainer.Data<T> data, final int paletteIdx) {
        final T[] palette = ((ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T>)(Object)data).moonrise$getPalette();
        if (palette == null) {
            return this.readPaletteSlow(data, paletteIdx);
        }

        final T ret = palette[paletteIdx];
        if (ret == null) {
            throw new IllegalArgumentException("Palette index out of bounds");
        }
        return ret;
    }
    // Paper end - optimise palette reads

    // Paper start - Anti-Xray - Add preset values
    private PalettedContainer(Strategy<T> strategy, Configuration configuration, BitStorage storage, Palette<T> palette, List<T> values, T defaultValue, T @org.jetbrains.annotations.Nullable [] presetValues) {
        this.presetValues = presetValues;
        this.strategy = strategy;
        this.data = new PalettedContainer.Data<>(configuration, storage, palette);
        if (presetValues != null
            && (configuration instanceof net.minecraft.world.level.chunk.Configuration.Simple simpleFactory && simpleFactory.factory() == Strategy.SINGLE_VALUE_PALETTE_FACTORY
            ? this.data.palette.valueFor(0) != defaultValue
            : !(configuration instanceof net.minecraft.world.level.chunk.Configuration.Global))) {
            // In 1.18 Mojang unfortunately removed code that already handled possible resize operations on read from disk for us
            // We readd this here but in a smarter way than it was before
            int maxSize = 1 << configuration.bitsInMemory();

            for (T presetValue : presetValues) {
                if (this.data.palette.getSize() >= maxSize) {
                    java.util.Set<T> allValues = new java.util.HashSet<>(values);
                    allValues.addAll(Arrays.asList(presetValues));
                    int newBits = net.minecraft.util.Mth.ceillog2(allValues.size());

                    if (newBits > configuration.bitsInMemory()) {
                        this.onResize(newBits, null);
                    }

                    break;
                }

                this.data.palette.idFor(presetValue, this);
            }
        }
        // Paper end
        this.updateData(this.data); // Paper - optimise palette reads
    }

    private PalettedContainer(PalettedContainer<T> other, T @org.jetbrains.annotations.Nullable [] presetValues) { // Paper - Anti-Xray - Add preset values
        this.presetValues = presetValues; // Paper - Anti-Xray - Add preset values
        this.strategy = other.strategy;
        this.data = other.data.copy();
        this.updateData(this.data); // Paper - optimise palette reads
    }

    // Paper start - Anti-Xray - Add preset values
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public PalettedContainer(T defaultValue, Strategy<T> strategy) {
        this(defaultValue, strategy, null);
    }
    public PalettedContainer(T defaultValue, Strategy<T> strategy, T @org.jetbrains.annotations.Nullable [] presetValues) {
        this.presetValues = presetValues;
        // Paper end - Anti-Xray
        this.strategy = strategy;
        this.data = this.createOrReuseData(null, 0);
        this.data.palette.idFor(defaultValue, this);
        this.updateData(this.data); // Paper - optimise palette reads
    }

    private PalettedContainer.Data<T> createOrReuseData(PalettedContainer.@Nullable Data<T> data, int bits) {
        Configuration configurationForBitCount = this.strategy.getConfigurationForBitCount(bits);
        if (data != null && configurationForBitCount.equals(data.configuration())) {
            return data;
        } else {
            BitStorage bitStorage = (BitStorage)(configurationForBitCount.bitsInMemory() == 0
                ? new ZeroBitStorage(this.strategy.entryCount())
                : new SimpleBitStorage(configurationForBitCount.bitsInMemory(), this.strategy.entryCount()));
            Palette<T> palette = configurationForBitCount.createPalette(this.strategy, List.of());
            return new PalettedContainer.Data<>(configurationForBitCount, bitStorage, palette);
        }
    }

    @Override
    public synchronized int onResize(int bits, T addedValue) { // Paper - synchronize
        PalettedContainer.Data<T> data = this.data;
        // Paper start - Anti-Xray - Add preset values
        if (this.presetValues != null && addedValue != null && data.configuration() instanceof Configuration.Simple simpleFactory && simpleFactory.factory() == Strategy.SINGLE_VALUE_PALETTE_FACTORY) {
            int duplicates = 0;
            List<T> presetValues = Arrays.asList(this.presetValues);
            duplicates += presetValues.contains(addedValue) ? 1 : 0;
            duplicates += presetValues.contains(data.palette.valueFor(0)) ? 1 : 0;
            final int size = 1 << this.strategy.getConfigurationForBitCount(bits).bitsInMemory();
            bits = net.minecraft.util.Mth.ceillog2(size + presetValues.size() - duplicates);
        }
        // Paper end - Anti-Xray
        PalettedContainer.Data<T> data1 = this.createOrReuseData(data, bits);
        data1.copyFrom(data.palette, data.storage);
        this.data = data1;
        this.updateData(this.data); // Paper - optimise palette reads
        // Paper start - Anti-Xray
        this.addPresetValues();
        return addedValue == null ? -1 : data1.palette.idFor(addedValue, PaletteResize.noResizeExpected());
    }
    private void addPresetValues() {
        if (this.presetValues != null && !(this.data.configuration() instanceof Configuration.Global)) {
            for (T presetValue : this.presetValues) {
                this.data.palette.idFor(presetValue, this);
            }
        }
    }
    // Paper end - Anti-Xray

    public synchronized T getAndSet(int x, int y, int z, T state) { // Paper - synchronize
        this.acquire();

        Object var5;
        try {
            var5 = this.getAndSet(this.strategy.getIndex(x, y, z), state);
        } finally {
            this.release();
        }

        return (T)var5;
    }

    public T getAndSetUnchecked(int x, int y, int z, T state) {
        return this.getAndSet(this.strategy.getIndex(x, y, z), state);
    }

    private T getAndSet(int index, T state) {
        // Paper start - optimise palette reads
        final int paletteIdx = this.data.palette.idFor(state, this);
        final PalettedContainer.Data<T> data = this.data;
        final int prev = data.storage.getAndSet(index, paletteIdx);
        return this.readPalette(data, prev);
        // Paper end - optimise palette reads
    }

    public synchronized void set(int x, int y, int z, T state) { // Paper - synchronize
        this.acquire();

        try {
            this.set(this.strategy.getIndex(x, y, z), state);
        } finally {
            this.release();
        }
    }

    private void set(int index, T state) {
        int i = this.data.palette.idFor(state, this);
        this.data.storage.set(index, i);
    }

    @Override
    public T get(int x, int y, int z) {
        return this.get(this.strategy.getIndex(x, y, z));
    }

    public T get(int index) { // Paper - public
        // Paper start - optimise palette reads
        final PalettedContainer.Data<T> data = this.data;
        return this.readPalette(data, data.storage.get(index));
        // Paper end - optimise palette reads
    }

    @Override
    public void getAll(Consumer<T> consumer) {
        Palette<T> palette = this.data.palette();
        IntSet set = new IntArraySet();
        this.data.storage.getAll(set::add);
        set.forEach(id -> consumer.accept(palette.valueFor(id)));
    }

    public synchronized void read(FriendlyByteBuf buffer) { // Paper - synchronize
        this.acquire();

        try {
            int _byte = buffer.readByte();
            PalettedContainer.Data<T> data = this.createOrReuseData(this.data, _byte);
            data.palette.read(buffer, this.strategy.globalMap());
            buffer.readFixedSizeLongArray(data.storage.getRaw());
            this.data = data;
            this.addPresetValues(); // Paper - Anti-Xray - Add preset values (inefficient, but this isn't used by the server)
            this.updateData(this.data); // Paper - optimise palette reads
        } finally {
            this.release();
        }
    }

    // Paper start - Anti-Xray; Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public void write(FriendlyByteBuf buffer) {
        this.write(buffer, null, 0);
    }
    @Override
    public synchronized void write(FriendlyByteBuf buffer, io.papermc.paper.antixray.@Nullable ChunkPacketInfo<T> chunkPacketInfo, int chunkSectionIndex) { // Paper - synchronize
        this.acquire();

        try {
            this.data.write(buffer, this.strategy.globalMap(), chunkPacketInfo, chunkSectionIndex);
            if (chunkPacketInfo != null) {
                chunkPacketInfo.setPresetValues(chunkSectionIndex, this.presetValues);
            }
            // Paper end - Anti-Xray
        } finally {
            this.release();
        }
    }

    @VisibleForTesting
    public static <T> DataResult<PalettedContainer<T>> unpack(Strategy<T> strategy, PalettedContainerRO.PackedData<T> packedData, T defaultValue, T @org.jetbrains.annotations.Nullable [] presetValues) { // Paper - Anti-Xray
        List<T> list = packedData.paletteEntries();
        int entryCount = strategy.entryCount();
        Configuration configurationForPaletteSize = strategy.getConfigurationForPaletteSize(list.size());
        int i = configurationForPaletteSize.bitsInStorage();
        if (packedData.bitsPerEntry() != -1 && i != packedData.bitsPerEntry()) {
            return DataResult.error(() -> "Invalid bit count, calculated " + i + ", but container declared " + packedData.bitsPerEntry());
        } else {
            BitStorage bitStorage;
            Palette<T> palette;
            if (configurationForPaletteSize.bitsInMemory() == 0) {
                palette = configurationForPaletteSize.createPalette(strategy, list);
                bitStorage = new ZeroBitStorage(entryCount);
            } else {
                Optional<LongStream> optional = packedData.storage();
                if (optional.isEmpty()) {
                    return DataResult.error(() -> "Missing values for non-zero storage");
                }

                long[] longs = optional.get().toArray();

                try {
                    if (!configurationForPaletteSize.alwaysRepack() && configurationForPaletteSize.bitsInMemory() == i) {
                        palette = configurationForPaletteSize.createPalette(strategy, list);
                        bitStorage = new SimpleBitStorage(configurationForPaletteSize.bitsInMemory(), entryCount, longs);
                    } else {
                        Palette<T> palette1 = new HashMapPalette<>(i, list);
                        SimpleBitStorage simpleBitStorage = new SimpleBitStorage(i, entryCount, longs);
                        Palette<T> palette2 = configurationForPaletteSize.createPalette(strategy, list);
                        int[] ints = reencodeContents(simpleBitStorage, palette1, palette2);
                        palette = palette2;
                        bitStorage = new SimpleBitStorage(configurationForPaletteSize.bitsInMemory(), entryCount, ints);
                    }
                } catch (SimpleBitStorage.InitializationException var14) {
                    return DataResult.error(() -> "Failed to read PalettedContainer: " + var14.getMessage());
                }
            }

            return DataResult.success(new PalettedContainer<>(strategy, configurationForPaletteSize, bitStorage, palette, list, defaultValue, presetValues)); // Paper - Anti-Xray - Add preset values
        }
    }

    @Override
    public synchronized PalettedContainerRO.PackedData<T> pack(Strategy<T> strategy) { // Paper - synchronize
        this.acquire();

        PalettedContainerRO.PackedData var14;
        try {
            BitStorage bitStorage = this.data.storage;
            Palette<T> palette = this.data.palette;
            HashMapPalette<T> hashMapPalette = new HashMapPalette<>(bitStorage.getBits());
            int entryCount = strategy.entryCount();
            int[] ints = reencodeContents(bitStorage, palette, hashMapPalette);
            Configuration configurationForPaletteSize = strategy.getConfigurationForPaletteSize(hashMapPalette.getSize());
            int i = configurationForPaletteSize.bitsInStorage();
            Optional<LongStream> optional;
            if (i != 0) {
                SimpleBitStorage simpleBitStorage = new SimpleBitStorage(i, entryCount, ints);
                optional = Optional.of(Arrays.stream(simpleBitStorage.getRaw()));
            } else {
                optional = Optional.empty();
            }

            var14 = new PalettedContainerRO.PackedData<>(hashMapPalette.getEntries(), optional, i);
        } finally {
            this.release();
        }

        return var14;
    }

    private static <T> int[] reencodeContents(BitStorage bitStorage, Palette<T> oldPalette, Palette<T> newPalette) {
        int[] ints = new int[bitStorage.getSize()];
        bitStorage.unpack(ints);
        PaletteResize<T> paletteResize = PaletteResize.noResizeExpected();
        int i = -1;
        int i1 = -1;

        for (int i2 = 0; i2 < ints.length; i2++) {
            int i3 = ints[i2];
            if (i3 != i) {
                i = i3;
                i1 = newPalette.idFor(oldPalette.valueFor(i3), paletteResize);
            }

            ints[i2] = i1;
        }

        return ints;
    }

    @Override
    public int getSerializedSize() {
        return this.data.getSerializedSize(this.strategy.globalMap());
    }

    @Override
    public int bitsPerEntry() {
        return this.data.storage().getBits();
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        return this.data.palette.maybeHas(predicate);
    }

    @Override
    public PalettedContainer<T> copy() {
        return new PalettedContainer<>(this, this.presetValues); // Paper - Anti-Xray - Add preset values
    }

    @Override
    public PalettedContainer<T> recreate() {
        return new PalettedContainer<>(this.data.palette.valueFor(0), this.strategy, this.presetValues); // Paper - Anti-Xray - Add preset values
    }

    @Override
    public void count(PalettedContainer.CountConsumer<T> countConsumer) {
        if (this.data.palette.getSize() == 1) {
            countConsumer.accept(this.data.palette.valueFor(0), this.data.storage.getSize());
        } else {
            Int2IntOpenHashMap map = new Int2IntOpenHashMap();
            this.data.storage.getAll(id -> map.addTo(id, 1));
            map.int2IntEntrySet().forEach(idEntry -> countConsumer.accept(this.data.palette.valueFor(idEntry.getIntKey()), idEntry.getIntValue()));
        }
    }

    @FunctionalInterface
    public interface CountConsumer<T> {
        void accept(T state, int count);
    }

    // Paper start - optimise palette reads
    public static final class Data<T> implements ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T> {

        private final Configuration configuration;
        private final BitStorage storage;
        private final Palette<T> palette;

        private T[] moonrise$palette;

        public Data(final Configuration configuration, final BitStorage storage, final Palette<T> palette) {
            this.configuration = configuration;
            this.storage = storage;
            this.palette = palette;
        }

        public Configuration configuration() {
            return this.configuration;
        }

        public BitStorage storage() {
            return this.storage;
        }

        public Palette<T> palette() {
            return this.palette;
        }

        @Override
        public final T[] moonrise$getPalette() {
            return this.moonrise$palette;
        }

        @Override
        public final void moonrise$setPalette(final T[] palette) {
            this.moonrise$palette = palette;
        }
        // Paper end - optimise palette reads

        public void copyFrom(Palette<T> palette, BitStorage bitStorage) {
            PaletteResize<T> paletteResize = PaletteResize.noResizeExpected();

            for (int i = 0; i < bitStorage.getSize(); i++) {
                T object = palette.valueFor(bitStorage.get(i));
                this.storage.set(i, this.palette.idFor(object, paletteResize));
            }
        }

        public int getSerializedSize(IdMap<T> map) {
            return 1 + this.palette.getSerializedSize(map) + this.storage.getRaw().length * 8;
        }

        // Paper start - Anti-Xray - Add chunk packet info
        public void write(FriendlyByteBuf buffer, IdMap<T> map, io.papermc.paper.antixray.@Nullable ChunkPacketInfo<T> chunkPacketInfo, int chunkSectionIndex) {
            buffer.writeByte(this.storage.getBits());
            this.palette.write(buffer, map);
            if (chunkPacketInfo != null) {
                chunkPacketInfo.setBits(chunkSectionIndex, this.configuration.bitsInMemory());
                chunkPacketInfo.setPalette(chunkSectionIndex, this.palette);
                chunkPacketInfo.setIndex(chunkSectionIndex, buffer.writerIndex());
            }
            // Paper end - Anti-Xray - Add chunk packet info
            buffer.writeFixedSizeLongArray(this.storage.getRaw());
        }

        public PalettedContainer.Data<T> copy() {
            return new PalettedContainer.Data<>(this.configuration, this.storage.copy(), this.palette.copy());
        }
    }
}
