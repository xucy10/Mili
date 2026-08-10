package net.minecraft.world.entity;

public enum EntitySpawnReason {
    NATURAL,
    CHUNK_GENERATION,
    SPAWNER,
    STRUCTURE,
    BREEDING,
    MOB_SUMMONED,
    JOCKEY,
    EVENT,
    CONVERSION,
    REINFORCEMENT,
    TRIGGERED,
    BUCKET,
    SPAWN_ITEM_USE,
    COMMAND,
    DISPENSER,
    PATROL,
    TRIAL_SPAWNER,
    LOAD,
    DIMENSION_TRAVEL;

    public static boolean isSpawner(EntitySpawnReason reason) {
        return reason == SPAWNER || reason == TRIAL_SPAWNER;
    }

    public static boolean ignoresLightRequirements(EntitySpawnReason reason) {
        return reason == TRIAL_SPAWNER;
    }
}
