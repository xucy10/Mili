package fun.bm.mili.villager;

import org.bukkit.Material;

/**
 * Immutable snapshot of a block for villager activity checks.
 */
public record BlockSnapshot(Material type, boolean solid, boolean passable) {
}