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
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

/**
 * 传送门链接事件监听器。
 * 修复 Folia Regionized 线程模型下的 World mismatch 问题。
 * 在源 Region 仅进行纯坐标计算，实际 Block 访问调度到目标 Region。
 */
public class PortalLinkListener implements Listener {

    private final Plugin plugin;

    public PortalLinkListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreEntityPortal(PreEntityPortalEvent event) {
        if (!PortalLinkFixConfig.enabled) {
            return;
        }

        Entity entity = event.getEntity();
        Location source = event.getPortalPos();
        World destWorld = event.getDestination();

        if (entity == null || source == null || destWorld == null) {
            return;
        }

        // 1. 优先使用已记录的 Portal 配对
        PortalLinkManager.PortalPair existingPair = PortalLinkManager.findPair(source);

        if (existingPair != null) {
            Location destination = existingPair.getDestLocation();

            if (destination != null && destination.getWorld() != null) {
                event.setCancelled(true);

                entity.teleportAsync(
                        destination,
                        PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                );

                return;
            }
        }

        // 2. 不允许搜索时直接返回
        if (PortalLinkFixConfig.searchRadius <= 0) {
            return;
        }

        // 3. 纯坐标计算：不访问目标世界 Block
        PortalLinkManager.PortalTarget target =
                PortalLinkManager.calculateTarget(source, destWorld);

        if (target == null) {
            return;
        }

        // 4. 取消原事件
        event.setCancelled(true);

        // 5. 调度到目标 World 对应 Region 执行 Portal 搜索
        Bukkit.getRegionScheduler().execute(
                plugin,
                target.world(),
                target.blockX() >> 4,
                target.blockZ() >> 4,
                () -> {
                    // 现在在目标 Region 内，可以安全访问 Block
                    Location nearest = PortalLinkManager.findNearestPortal(
                            target.world(),
                            target.blockX(),
                            target.blockZ()
                    );

                    Location destination = nearest != null
                            ? nearest
                            : target.location();

                    // 6. 自动记录实际目标
                    if (PortalLinkFixConfig.autoRecord) {
                        PortalLinkManager.registerPair(source, destination);
                    }

                    // 7. 执行传送
                    entity.teleportAsync(
                            destination,
                            PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                    );
                }
        );
    }
}
