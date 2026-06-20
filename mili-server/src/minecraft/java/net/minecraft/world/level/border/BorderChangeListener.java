package net.minecraft.world.level.border;

public interface BorderChangeListener {
    void onSetSize(WorldBorder border, double size);

    void onLerpSize(WorldBorder border, double oldSize, double newSize, long time, long startTime);

    void onSetCenter(WorldBorder border, double x, double z);

    void onSetWarningTime(WorldBorder border, int warningTime);

    void onSetWarningBlocks(WorldBorder border, int warningBlocks);

    void onSetDamagePerBlock(WorldBorder border, double damagePerBlock);

    void onSetSafeZone(WorldBorder border, double safeZone);
}
