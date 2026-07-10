package org.leavesmc.leaves.event.bot;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.leavesmc.leaves.event.BukkitEvent;

public class BotInventoryOpenEvent extends BukkitEvent implements Cancellable {

    private final Player bot;
    private final Player viewer;

    public BotInventoryOpenEvent(Player bot, Player viewer) {
        this.bot = bot;
        this.viewer = viewer;
    }

    public Player getBot() { return bot; }
    public Player getViewer() { return viewer; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
