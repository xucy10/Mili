package net.minecraft.world.level.chunk;

import net.minecraft.core.IdMap;
import net.minecraft.util.Mth;

public abstract class Strategy<T> {
    public static final Palette.Factory SINGLE_VALUE_PALETTE_FACTORY = SingleValuePalette::create; // Paper - Anti-Xray
    private static final Palette.Factory LINEAR_PALETTE_FACTORY = LinearPalette::create;
    private static final Palette.Factory HASHMAP_PALETTE_FACTORY = HashMapPalette::create;
    static final Configuration ZERO_BITS = new Configuration.Simple(SINGLE_VALUE_PALETTE_FACTORY, 0);
    static final Configuration ONE_BIT_LINEAR = new Configuration.Simple(LINEAR_PALETTE_FACTORY, 1);
    static final Configuration TWO_BITS_LINEAR = new Configuration.Simple(LINEAR_PALETTE_FACTORY, 2);
    static final Configuration THREE_BITS_LINEAR = new Configuration.Simple(LINEAR_PALETTE_FACTORY, 3);
    static final Configuration FOUR_BITS_LINEAR = new Configuration.Simple(LINEAR_PALETTE_FACTORY, 4);
    static final Configuration FIVE_BITS_HASHMAP = new Configuration.Simple(HASHMAP_PALETTE_FACTORY, 5);
    static final Configuration SIX_BITS_HASHMAP = new Configuration.Simple(HASHMAP_PALETTE_FACTORY, 6);
    static final Configuration SEVEN_BITS_HASHMAP = new Configuration.Simple(HASHMAP_PALETTE_FACTORY, 7);
    static final Configuration EIGHT_BITS_HASHMAP = new Configuration.Simple(HASHMAP_PALETTE_FACTORY, 8);
    private final IdMap<T> globalMap;
    private final GlobalPalette<T> globalPalette;
    protected final int globalPaletteBitsInMemory;
    private final int bitsPerAxis;
    private final int entryCount;

    Strategy(IdMap<T> globalMap, int bitsPerAxis) {
        this.globalMap = globalMap;
        this.globalPalette = new GlobalPalette<>(globalMap);
        this.globalPaletteBitsInMemory = minimumBitsRequiredForDistinctValues(globalMap.size());
        this.bitsPerAxis = bitsPerAxis;
        this.entryCount = 1 << bitsPerAxis * 3;
    }

    public static <T> Strategy<T> createForBlockStates(IdMap<T> globalMap) {
        return new Strategy<T>(globalMap, 4) {
            @Override
            public Configuration getConfigurationForBitCount(int bits) {
                return (Configuration)(switch (bits) {
                    case 0 -> Strategy.ZERO_BITS;
                    case 1, 2, 3, 4 -> Strategy.FOUR_BITS_LINEAR;
                    case 5 -> Strategy.FIVE_BITS_HASHMAP;
                    case 6 -> Strategy.SIX_BITS_HASHMAP;
                    case 7 -> Strategy.SEVEN_BITS_HASHMAP;
                    case 8 -> Strategy.EIGHT_BITS_HASHMAP;
                    default -> new Configuration.Global(this.globalPaletteBitsInMemory, bits);
                });
            }
        };
    }

    public static <T> Strategy<T> createForBiomes(IdMap<T> globalMap) {
        return new Strategy<T>(globalMap, 2) {
            @Override
            public Configuration getConfigurationForBitCount(int bits) {
                return (Configuration)(switch (bits) {
                    case 0 -> Strategy.ZERO_BITS;
                    case 1 -> Strategy.ONE_BIT_LINEAR;
                    case 2 -> Strategy.TWO_BITS_LINEAR;
                    case 3 -> Strategy.THREE_BITS_LINEAR;
                    default -> new Configuration.Global(this.globalPaletteBitsInMemory, bits);
                });
            }
        };
    }

    public int entryCount() {
        return this.entryCount;
    }

    public int getIndex(int x, int y, int z) {
        return (y << this.bitsPerAxis | z) << this.bitsPerAxis | x;
    }

    public IdMap<T> globalMap() {
        return this.globalMap;
    }

    public GlobalPalette<T> globalPalette() {
        return this.globalPalette;
    }

    protected abstract Configuration getConfigurationForBitCount(int bits);

    protected Configuration getConfigurationForPaletteSize(int size) {
        int i = minimumBitsRequiredForDistinctValues(size);
        return this.getConfigurationForBitCount(i);
    }

    private static int minimumBitsRequiredForDistinctValues(int size) {
        return Mth.ceillog2(size);
    }
}
