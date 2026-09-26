package fun.bm.mili.portal;

import fun.bm.mili.config.modules.fixes.PortalLinkFixConfig;
import me.earthme.luminol.api.entity.PreEntityPortalEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

public class PortalLinkListener implements Listener {
    // Mili start - fix: use passed-in Mili plugin instance instead of iterating all plugins
    private static org.bukkit.plugin.Plugin plugin;

    public void register(org.bukkit.plugin.Plugin miliPlugin) {
        if (miliPlugin != null && miliPlugin.isEnabled()) {
            plugin = miliPlugin;
        } else {
            // Mili start - fix: fallback with warning if Mili plugin instance unavailable
            Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
            for (Plugin p : plugins) {
                if (p.isEnabled()) { plugin = p; break; }
            }
            if (plugin == null && plugins.length > 0) {
                plugin = plugins[0];
            }
            Bukkit.getLogger().warning("[Mili Portal] Could not obtain Mili plugin instance, falling back to: " + (plugin != null ? plugin.getName() : "none"));
            // Mili end
        }
        if (plugin != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }
    // Mili end

    public void unregister() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        // Mili start - fix: clear static plugin reference on unregister
        plugin = null;
        // Mili end
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreEntityPortal(PreEntityPortalEvent event) {
        if (!PortalLinkFixConfig.enabled) return;

        Entity entity = event.getEntity();
        Location source = event.getPortalPos();
        World destWorld = event.getDestination();
        if (source == null || destWorld == null) return;

        PortalLinkManager.PortalPair existingPair = PortalLinkManager.findPair(source);
        if (existingPair != null) {
            Location dest = existingPair.getDestLocation();
            if (dest != null && dest.getWorld() != null) {
                event.setCancelled(true);
                entity.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
                return;
            }
        }

        if (PortalLinkFixConfig.searchRadius > 0) {
            Location calculated = PortalLinkManager.calculateDestination(source);
            if (calculated != null && calculated.getWorld() != null
                    && calculated.getWorld().equals(destWorld)) {
                Location nearest = PortalLinkManager.findNearestPortal(
                        destWorld, calculated.getBlockX(), calculated.getBlockZ());
                if (nearest != null) {
                    Location entityLoc = entity.getLocation();
                    boolean alreadyCorrect = entityLoc.getWorld() != null
                            && entityLoc.getWorld().equals(destWorld)
                            && Math.abs(entityLoc.getBlockX() - nearest.getBlockX()) <= 2
                            && Math.abs(entityLoc.getBlockZ() - nearest.getBlockZ()) <= 2;
                    if (!alreadyCorrect) {
                        event.setCancelled(true);
                        entity.teleportAsync(nearest, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
                        if (PortalLinkFixConfig.autoRecord) {
                            PortalLinkManager.registerPair(source, nearest);
                        }
                        return;
                    }
                }
            }
        }

        if (PortalLinkFixConfig.autoRecord && !event.isCancelled()) {
            World dw = event.getDestination();
            if (dw != null) {
                Location src = event.getPortalPos();
                boolean isNether = dw.getName().contains("nether");
                double factor = isNether ? 8.0 : 1.0 / 8.0;
                int destX = (int) Math.floor(src.getX() * factor);
                int destZ = (int) Math.floor(src.getZ() * factor);
                Location expected = new Location(dw, destX, src.getBlockY(), destZ);
                PortalLinkManager.registerPair(source, expected);
            }
        }
    }
}
