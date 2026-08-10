package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public final class RandomSupport {
    public static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    public static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final HashFunction MD5_128 = Hashing.md5();
    private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);

    @VisibleForTesting
    public static long mixStafford13(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public static RandomSupport.Seed128bit upgradeSeedTo128bitUnmixed(long seed) {
        long l = seed ^ 7640891576956012809L;
        long l1 = l + -7046029254386353131L;
        return new RandomSupport.Seed128bit(l, l1);
    }

    public static RandomSupport.Seed128bit upgradeSeedTo128bit(long seed) {
        return upgradeSeedTo128bitUnmixed(seed).mixed();
    }

    public static RandomSupport.Seed128bit seedFromHashOf(String string) {
        byte[] bytes = MD5_128.hashString(string, StandardCharsets.UTF_8).asBytes();
        long l = Longs.fromBytes(bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7]);
        long l1 = Longs.fromBytes(bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]);
        return new RandomSupport.Seed128bit(l, l1);
    }

    public static long generateUniqueSeed() {
        return SEED_UNIQUIFIER.updateAndGet(l -> l * 1181783497276652981L) ^ System.nanoTime();
    }

    public record Seed128bit(long seedLo, long seedHi) {
        public RandomSupport.Seed128bit xor(long seedLo, long seedHi) {
            return new RandomSupport.Seed128bit(this.seedLo ^ seedLo, this.seedHi ^ seedHi);
        }

        public RandomSupport.Seed128bit xor(RandomSupport.Seed128bit seed) {
            return this.xor(seed.seedLo, seed.seedHi);
        }

        public RandomSupport.Seed128bit mixed() {
            return new RandomSupport.Seed128bit(RandomSupport.mixStafford13(this.seedLo), RandomSupport.mixStafford13(this.seedHi));
        }
    }
}
