package org.leavesmc.leaves.event.bot;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;
import java.util.UUID;

public class BotActionExecuteEvent extends BukkitEvent implements Cancellable {

    private final Player bot;
    private final String actionName;
    private final UUID actionUuid;

    public enum Result { ALLOW, SOFT_CANCEL, HARD_CANCEL }

    private Result result = Result.ALLOW;

    public BotActionExecuteEvent(Player bot, String actionName, UUID actionUuid) {
        this.bot = bot;
        this.actionName = actionName;
        this.actionUuid = actionUuid;
    }

    public Player getBot() { return bot; }
    public String getActionName() { return actionName; }
    public UUID getActionUuid() { return actionUuid; }
    public Result getResult() { return result; }
    public void setResult(Result result) { this.result = result; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
