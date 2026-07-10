package org.leavesmc.leaves.event.bot;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;
import java.util.UUID;

public class BotActionScheduleEvent extends BukkitEvent implements Cancellable {

    private final Player bot;
    private final String actionName;
    private final UUID actionUuid;
    private final CommandSender sender;

    public BotActionScheduleEvent(Player bot, String actionName, UUID actionUuid, CommandSender sender) {
        this.bot = bot;
        this.actionName = actionName;
        this.actionUuid = actionUuid;
        this.sender = sender;
    }

    public Player getBot() { return bot; }
    public String getActionName() { return actionName; }
    public UUID getActionUuid() { return actionUuid; }
    public CommandSender getSender() { return sender; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
