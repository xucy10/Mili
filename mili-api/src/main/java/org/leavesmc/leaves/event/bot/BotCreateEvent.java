package org.leavesmc.leaves.event.bot;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.leavesmc.leaves.event.BukkitEvent;

public class BotCreateEvent extends BukkitEvent implements Cancellable {

    public enum CreateReason { COMMAND, PLUGIN, INTERNAL, UNKNOWN }

    private final String bot;
    private final String skin;
    private Location createLocation;
    private final CreateReason reason;
    private final CommandSender creator;

    public BotCreateEvent(String bot, String skin, Location createLocation, CreateReason reason, CommandSender creator) {
        this.bot = bot;
        this.skin = skin;
        this.createLocation = createLocation;
        this.reason = reason;
        this.creator = creator;
    }

    public String getBot() { return bot; }
    public String getSkin() { return skin; }
    public Location getCreateLocation() { return createLocation; }
    public void setCreateLocation(Location createLocation) { this.createLocation = createLocation; }
    public CreateReason getReason() { return reason; }
    public CommandSender getCreator() { return creator; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    private static final HandlerList HANDLERS = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
