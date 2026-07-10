package org.leavesmc.leaves.event.bot;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;

public class BotRemoveEvent extends BukkitEvent implements Cancellable {

    public enum RemoveReason { INTERNAL, DEATH, PLUGIN, COMMAND }

    private final Player bot;
    private final boolean save;
    private RemoveReason reason = RemoveReason.PLUGIN;
    private CommandSender remover = null;
    private boolean resume = false;
    private Component removeMessage = null;
    private boolean async = false;

    public BotRemoveEvent(Player bot, boolean save) {
        this.bot = bot;
        this.save = save;
    }

    public BotRemoveEvent(Player bot, RemoveReason reason, CommandSender remover, boolean save, boolean resume) {
        this(bot, save);
        this.reason = reason;
        this.remover = remover;
        this.resume = resume;
    }

    public BotRemoveEvent(Player bot, RemoveReason reason, CommandSender remover, boolean save, boolean resume, boolean async) {
        this(bot, reason, remover, save, resume);
        this.async = async;
    }

    public BotRemoveEvent(Player bot, CommandSender remover, RemoveReason reason, boolean save, boolean resume) {
        this(bot, reason, remover, save, resume);
    }

    public BotRemoveEvent(Player bot, RemoveReason reason, CommandSender remover, Component removeMessage, boolean save) {
        this(bot, save);
        this.reason = reason;
        this.remover = remover;
        this.removeMessage = removeMessage;
    }

    public Player getBot() { return bot; }
    public boolean shouldSave() { return save; }
    public boolean shouldResume() { return resume; }
    public RemoveReason getReason() { return reason; }
    public CommandSender getRemover() { return remover; }
    public Component removeMessage() { return removeMessage; }
    public boolean isAsync() { return async; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
