package org.leavesmc.leaves.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BukkitEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() { return HANDLERS; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
}
