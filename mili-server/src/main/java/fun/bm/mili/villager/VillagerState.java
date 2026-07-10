package fun.bm.mili.villager;

/**
 * Plain snapshot of the villager properties the activity policy needs.
 */
public record VillagerState(
        String name,
        boolean swimming,
        boolean sleeping,
        boolean hasVehicle,
        boolean professionNone,
        int experience,
        int blockX,
        int blockY,
        int blockZ) {
}