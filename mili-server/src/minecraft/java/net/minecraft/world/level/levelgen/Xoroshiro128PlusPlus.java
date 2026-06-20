package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import java.util.stream.LongStream;
import net.minecraft.util.Util;

public class Xoroshiro128PlusPlus {
    private long seedLo;
    private long seedHi;
    public static final Codec<Xoroshiro128PlusPlus> CODEC = Codec.LONG_STREAM
        .comapFlatMap(
            longStream -> Util.fixedSize(longStream, 2).map(longs -> new Xoroshiro128PlusPlus(longs[0], longs[1])),
            xoroshiro128PlusPlus -> LongStream.of(xoroshiro128PlusPlus.seedLo, xoroshiro128PlusPlus.seedHi)
        );

    public Xoroshiro128PlusPlus(RandomSupport.Seed128bit seed) {
        this(seed.seedLo(), seed.seedHi());
    }

    public Xoroshiro128PlusPlus(long seedLo, long seedHi) {
        this.seedLo = seedLo;
        this.seedHi = seedHi;
        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = -7046029254386353131L;
            this.seedHi = 7640891576956012809L;
        }
    }

    public long nextLong() {
        long l = this.seedLo;
        long l1 = this.seedHi;
        long l2 = Long.rotateLeft(l + l1, 17) + l;
        l1 ^= l;
        this.seedLo = Long.rotateLeft(l, 49) ^ l1 ^ l1 << 21;
        this.seedHi = Long.rotateLeft(l1, 28);
        return l2;
    }
}
