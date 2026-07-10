package org.leavesmc.leaves.event.bot;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;

public class BotConfigModifyEvent extends BukkitEvent implements Cancellable {

    private final String botName;
    private final String key;
    private final Object oldValue;
    private final Object newValue;

    public BotConfigModifyEvent(String botName, String key, Object oldValue, Object newValue) {
        this.botName = botName;
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getBotName() { return botName; }
    public String getKey() { return key; }
    public Object getOldValue() { return oldValue; }
    public Object getNewValue() { return newValue; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
