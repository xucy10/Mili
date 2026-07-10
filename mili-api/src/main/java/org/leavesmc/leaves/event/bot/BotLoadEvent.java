package org.leavesmc.leaves.event.bot;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;

public class BotLoadEvent extends BukkitEvent implements Cancellable {

    private final String botName;
    private final java.util.UUID botUUID;

    public BotLoadEvent(String botName) {
        this(botName, null);
    }

    public BotLoadEvent(String botName, java.util.UUID botUUID) {
        this.botName = botName;
        this.botUUID = botUUID;
    }

    public String getBotName() { return botName; }
    public java.util.UUID getBotUUID() { return botUUID; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}