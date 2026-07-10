package org.leavesmc.leaves.event.bot;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;

public class BotLoadEvent extends BukkitEvent implements Cancellable {

    private final String botName;

    public BotLoadEvent(String botName) {
        this.botName = botName;
    }

    public String getBotName() { return botName; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
