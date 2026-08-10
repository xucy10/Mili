package net.minecraft.world;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

public class Stopwatches extends SavedData {
    private static final Codec<Stopwatches> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.LONG)
        .fieldOf("stopwatches")
        .codec()
        .xmap(Stopwatches::unpack, Stopwatches::pack);
    public static final SavedDataType<Stopwatches> TYPE = new SavedDataType<>("stopwatches", Stopwatches::new, CODEC, DataFixTypes.SAVED_DATA_STOPWATCHES);
    private final Map<Identifier, Stopwatch> stopwatches = new Object2ObjectOpenHashMap<>();

    private Stopwatches() {
    }

    private static Stopwatches unpack(Map<Identifier, Long> packed) {
        Stopwatches stopwatches = new Stopwatches();
        long l = currentTime();
        packed.forEach((identifier, _long) -> stopwatches.stopwatches.put(identifier, new Stopwatch(l, _long)));
        return stopwatches;
    }

    private Map<Identifier, Long> pack() {
        long l = currentTime();
        Map<Identifier, Long> map = new TreeMap<>();
        this.stopwatches.forEach((identifier, stopwatch) -> map.put(identifier, stopwatch.elapsedMilliseconds(l)));
        return map;
    }

    public @Nullable Stopwatch get(Identifier id) {
        return this.stopwatches.get(id);
    }

    public boolean add(Identifier id, Stopwatch stopwatch) {
        if (this.stopwatches.putIfAbsent(id, stopwatch) == null) {
            this.setDirty();
            return true;
        } else {
            return false;
        }
    }

    public boolean update(Identifier id, UnaryOperator<Stopwatch> operator) {
        if (this.stopwatches.computeIfPresent(id, (identifier, stopwatch) -> operator.apply(stopwatch)) != null) {
            this.setDirty();
            return true;
        } else {
            return false;
        }
    }

    public boolean remove(Identifier id) {
        boolean flag = this.stopwatches.remove(id) != null;
        if (flag) {
            this.setDirty();
        }

        return flag;
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || !this.stopwatches.isEmpty();
    }

    public List<Identifier> ids() {
        return List.copyOf(this.stopwatches.keySet());
    }

    public static long currentTime() {
        return Util.getMillis();
    }
}
