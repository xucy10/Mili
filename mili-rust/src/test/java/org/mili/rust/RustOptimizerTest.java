package org.mili.rust;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RustOptimizerTest {
    @Test
    void testDedupFallback() {
        assertEquals("abc", RustOptimizer.dedup("cbaabc"));
    }

    @Test
    void testHashFallback() {
        String hash = RustOptimizer.hash("hello");
        assertEquals(24, hash.length());
        assertEquals("hash:", hash.substring(0, 5));
    }

    @Test
    void testMergePacketCostFallback() {
        long cost = RustOptimizer.mergePacketCost(List.of(1L, 2L, 3L, 4L));
        assertEquals(19L, cost);
    }

    @Test
    void testPacketSizeFallback() {
        assertEquals(10L, RustOptimizer.packetSize("1 2 3 4"));
    }

    @Test
    void testScheduler() {
        String result = RustOptimizer.scheduler(4);
        assertEquals(true, result.startsWith("scheduler:"));
    }
}
