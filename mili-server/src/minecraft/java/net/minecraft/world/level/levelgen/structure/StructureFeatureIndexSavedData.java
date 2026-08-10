package net.minecraft.world.level.levelgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class StructureFeatureIndexSavedData extends SavedData {
    private final LongSet all;
    private final LongSet remaining;
    private static final Codec<LongSet> LONG_SET = Codec.LONG_STREAM.xmap(LongOpenHashSet::toSet, LongCollection::longStream);
    public static final Codec<StructureFeatureIndexSavedData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(LONG_SET.fieldOf("All").forGetter(data -> data.all), LONG_SET.fieldOf("Remaining").forGetter(data -> data.remaining))
            .apply(instance, StructureFeatureIndexSavedData::new)
    );

    public static SavedDataType<StructureFeatureIndexSavedData> type(String id) {
        return new SavedDataType<>(id, StructureFeatureIndexSavedData::new, CODEC, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES);
    }

    private StructureFeatureIndexSavedData(LongSet all, LongSet remaining) {
        this.all = all;
        this.remaining = remaining;
    }

    public StructureFeatureIndexSavedData() {
        this(new LongOpenHashSet(), new LongOpenHashSet());
    }

    public void addIndex(long index) {
        this.all.add(index);
        this.remaining.add(index);
        this.setDirty();
    }

    public boolean hasStartIndex(long index) {
        return this.all.contains(index);
    }

    public boolean hasUnhandledIndex(long index) {
        return this.remaining.contains(index);
    }

    public void removeIndex(long index) {
        if (this.remaining.remove(index)) {
            this.setDirty();
        }
    }

    public LongSet getAll() {
        return this.all;
    }
}
