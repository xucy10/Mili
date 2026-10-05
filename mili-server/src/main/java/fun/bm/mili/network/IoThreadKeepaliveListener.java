package fun.bm.mili.network;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * 在玩家进入游戏时给其连接安装 IO 线程 keepalive handler。
 * 玩家离开时无需处理：channel 关闭后整个 pipeline 会随之回收。
 */
public class IoThreadKeepaliveListener implements Listener {

    private Plugin plugin;

    public void register(Plugin miliPlugin) {
        if (miliPlugin != null && miliPlugin.isEnabled()) {
            this.plugin = miliPlugin;
        } else {
            this.plugin = Bukkit.getPluginManager().getPlugin("Mili");
        }
        if (this.plugin == null) {
            Bukkit.getLogger().warning("[Mili Keepalive] 获取 Mili 插件实例失败，IO 线程 keepalive 已禁用");
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
    }

    public void unregister() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        this.plugin = null;
    }

    // 注意：PlayerJoinEvent 不可取消，这里不能加 ignoreCancelled，否则 Bukkit 注册时会直接抛异常
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        IoThreadKeepalive.install(event.getPlayer());
    }
}
