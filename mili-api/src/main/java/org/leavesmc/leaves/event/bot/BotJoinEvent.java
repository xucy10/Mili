package org.leavesmc.leaves.event.bot;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;

public class BotJoinEvent extends BukkitEvent {

    private final Player bot;
    private Component joinMessage;

    public BotJoinEvent(Player bot, Component joinMessage) {
        this.bot = bot;
        this.joinMessage = joinMessage;
    }

    public Player getBot() { return bot; }

    public Component joinMessage() { return joinMessage; }
    public void joinMessage(Component msg) { this.joinMessage = msg; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
