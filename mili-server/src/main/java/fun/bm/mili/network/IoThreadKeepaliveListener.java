package fun.bm.mili.network;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

/**
 * 在玩家进入游戏时给其连接安装 IO 线程 keepalive handler。
 * 玩家离开时无需处理：channel 关闭后整个 pipeline 会随之回收。
 */
public class IoThreadKeepaliveListener implements Listener {

    // Mili start - fix: Mili is a server core, not a registered Bukkit plugin, so
    // Bukkit.getPluginManager().getPlugin("Mili") always returns null — registering against it
    // would throw IllegalPluginAccessException and silently disable keepalive offloading for
    // everyone. MinecraftInternalPlugin is the built-in instance Leaves/Mili already uses for
    // Folia schedulers, and reports isEnabled() == true, which is all registerEvents checks.
    private static final MinecraftInternalPlugin OWNER = MinecraftInternalPlugin.INSTANCE;
    // Mili end

    public void register() {
        try {
            Bukkit.getPluginManager().registerEvents(this, OWNER);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Mili Keepalive] 事件注册失败，后续进服的玩家不会安装 IO keepalive："
                    + t.getMessage());
        }
    }

    public void unregister() {
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    // 注意：PlayerJoinEvent 不可取消，这里不能加 ignoreCancelled，否则 Bukkit 注册时会直接抛异常
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        IoThreadKeepalive.install(event.getPlayer());
    }
}
