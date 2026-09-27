package me.earthme.luminol.utils;

import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;

/**
 * Mili scheduler utilities.
 *
 * Small helper to reason about which component owns the region tick threads.
 */
public final class MiliSchedulerUtil {

    private MiliSchedulerUtil() {}

    /**
     * Returns {@code true} if the region tick threads are owned by the Mili
     * balancer scheduler ({@code MILI_BALANCER} scheduler type). Returns
     * {@code false} if the scheduler is not initialised yet (in which case
     * the legacy RegionBalancer pool behaviour is the safe default).
     */
    public static boolean isTickSchedulerOwnedByBalancer() {
        final TickRegionScheduler scheduler = TickRegions.getScheduler();
        return scheduler != null && scheduler.isMiliBalancer();
    }
}
