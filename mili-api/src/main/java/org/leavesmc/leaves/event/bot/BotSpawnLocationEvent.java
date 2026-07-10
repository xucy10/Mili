package org.leavesmc.leaves.event.bot;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;
import java.util.UUID;

public class BotSpawnLocationEvent extends BukkitEvent implements Cancellable {

    private final Player bot;
    private final org.bukkit.Location spawnLocation;

    public BotSpawnLocationEvent(Player bot, org.bukkit.Location spawnLocation) {
        this.bot = bot;
        this.spawnLocation = spawnLocation;
    }

    public Player getBot() { return bot; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
