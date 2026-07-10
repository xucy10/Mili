package org.leavesmc.leaves.event.bot;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;

public class BotDeathEvent extends BukkitEvent implements Cancellable {

    private final Player bot;
    private Component deathMessage;
    private final boolean sendDeathMessage;

    public BotDeathEvent(Player bot, Component deathMessage, boolean sendDeathMessage) {
        this.bot = bot;
        this.deathMessage = deathMessage;
        this.sendDeathMessage = sendDeathMessage;
    }

    public Player getBot() { return bot; }

    public Component deathMessage() { return deathMessage; }
    public boolean isSendDeathMessage() { return sendDeathMessage; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
