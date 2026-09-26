package me.earthme.luminol.functions.bars;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Compatibility shim for the legacy per-player status bar list.
 *
 * Mili's status bars (tpsbar/membar/regionbar) were refactored into the global
 * {@link GlobalServerBarManager} system, which is driven by the config modules
 * (TpsBarConfig/MembarConfig/RegionBarConfig) instead of per-player instances.
 * This class only exists to satisfy the remaining Luminol hooks in Player/ServerPlayer.
 */
public class TickableStatusBarList {
    private final Player player;

    public TickableStatusBarList(Player player) {
        this.player = player;
    }

    // Global bars are managed server-wide; nothing per-player to load/store
    public void load(ValueInput input) {}

    public void save(ValueOutput output) {}

    // Global bars tick themselves via GlobalServerBarManager; keep this a no-op
    public void tick() {}
}
