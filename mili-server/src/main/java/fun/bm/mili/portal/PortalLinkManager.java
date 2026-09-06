package fun.bm.mili.portal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PortalLinkManager {
    private static volatile boolean enabled = false;
    private static volatile int searchRadius = 16;
    private static volatile boolean strictMatching = false;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, PortalPair> registry = new ConcurrentHashMap<>();
    private static File dataFile;

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }
    public static void setSearchRadius(int v) { searchRadius = v; }
    public static int getSearchRadius() { return searchRadius; }
    public static void setStrictMatching(boolean v) { strictMatching = v; }

    public static void load() {
        dataFile = new File(Bukkit.getWorldContainer(), "portal_links.json");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { /* ignore */ }
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            Map<String, PortalPair> loaded = GSON.fromJson(reader,
                    new TypeToken<Map<String, PortalPair>>() {}.getType());
            if (loaded != null) {
                registry.putAll(loaded);
            }
            Bukkit.getLogger().info("[Mili Portal] Loaded " + registry.size() + " portal pairs");
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[Mili Portal] Failed to load portal links", e);
        }
    }

    public static void save() {
        if (dataFile == null) return;
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
            GSON.toJson(registry, writer);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[Mili Portal] Failed to save portal links", e);
        }
    }

    public static PortalPair findPair(Location source) {
        if (!enabled) return null;
        String key = locationKey(source);
        PortalPair pair = registry.get(key);
        if (pair != null) return pair;

        for (PortalPair p : registry.values()) {
            if (p.matchesSource(source)) {
                return p;
            }
        }
        return null;
    }

    public static void registerPair(Location source, Location destination) {
        if (!enabled) return;
        String key = locationKey(source);
        PortalPair pair = new PortalPair(
                source.getWorld().getName(), source.getBlockX(), source.getBlockY(), source.getBlockZ(),
                destination.getWorld().getName(), destination.getBlockX(), destination.getBlockY(), destination.getBlockZ()
        );
        registry.put(key, pair);
        save();
    }

    public static boolean removePair(String key) {
        boolean removed = registry.remove(key) != null;
        if (removed) save();
        return removed;
    }

    public static Map<String, PortalPair> getAllPairs() {
        return Collections.unmodifiableMap(registry);
    }

    /**
     * 纯坐标计算：根据源位置和目标世界计算目标坐标。
     * 不访问任何世界的 Block 数据，可在任意 Region 安全调用。
     *
     * @param source    玩家当前位置（源世界）
     * @param destWorld 目标世界
     * @return 目标坐标；如果无法计算则返回 null
     */
    public static PortalTarget calculateTarget(Location source, World destWorld) {
        World sourceWorld = source.getWorld();

        if (sourceWorld == null || destWorld == null) {
            return null;
        }

        if (sourceWorld.equals(destWorld)) {
            return null;
        }

        boolean toNether = destWorld.getEnvironment() == World.Environment.NETHER;
        boolean toOverworld = destWorld.getEnvironment() == World.Environment.NORMAL;

        if (!toNether && !toOverworld) {
            return null;
        }

        double destX = toNether
                ? source.getX() / 8.0
                : source.getX() * 8.0;

        double destZ = toNether
                ? source.getZ() / 8.0
                : source.getZ() * 8.0;

        return new PortalTarget(
                destWorld,
                destX,
                source.getY(),
                destZ
        );
    }

    /**
     * 在目标世界中搜索最近的 Nether Portal。
     * 警告：此方法直接访问目标世界的 Block，因此必须只在目标世界的 Region 内调用。
     *
     * @param world      目标世界
     * @param expectedX  期望 X 坐标
     * @param expectedZ  期望 Z 坐标
     * @return 最近 Portal 的位置；未找到返回 null
     */
    public static Location findNearestPortal(World world, int expectedX, int expectedZ) {
        int radius = searchRadius;
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int bx = expectedX + dx;
                    int bz = expectedZ + dz;
                    for (int y = world.getMinHeight(); y <= world.getMaxHeight(); y++) {
                        Block block = world.getBlockAt(bx, y, bz);
                        if (block.getType() == org.bukkit.Material.NETHER_PORTAL) {
                            return block.getLocation();
                        }
                    }
                }
            }
        }
        return null;
    }

    public static String locationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /**
     * 传送目标坐标（纯数据，不含 Block 访问）
     */
    public record PortalTarget(
            World world,
            double x,
            double y,
            double z
    ) {
        public int blockX() {
            return (int) Math.floor(x);
        }

        public int blockY() {
            return (int) Math.floor(y);
        }

        public int blockZ() {
            return (int) Math.floor(z);
        }

        public Location location() {
            return new Location(world, x, y, z);
        }
    }

    public static class PortalPair {
        private String sourceWorld;
        private int sourceX, sourceY, sourceZ;
        private String destWorld;
        private int destX, destY, destZ;

        public PortalPair(String sourceWorld, int sourceX, int sourceY, int sourceZ,
                          String destWorld, int destX, int destY, int destZ) {
            this.sourceWorld = sourceWorld;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.sourceZ = sourceZ;
            this.destWorld = destWorld;
            this.destX = destX;
            this.destY = destY;
            this.destZ = destZ;
        }

        public boolean matchesSource(Location loc) {
            if (!loc.getWorld().getName().equals(sourceWorld)) return false;
            int dx = Math.abs(loc.getBlockX() - sourceX);
            int dz = Math.abs(loc.getBlockZ() - sourceZ);
            return dx <= 2 && dz <= 2 && Math.abs(loc.getBlockY() - sourceY) <= 4;
        }

        public Location getSourceLocation() {
            World w = Bukkit.getWorld(sourceWorld);
            return w != null ? new Location(w, sourceX, sourceY, sourceZ) : null;
        }

        public Location getDestLocation() {
            World w = Bukkit.getWorld(destWorld);
            return w != null ? new Location(w, destX, destY, destZ) : null;
        }

        public String getSourceWorld() { return sourceWorld; }
        public int getSourceX() { return sourceX; }
        public int getSourceY() { return sourceY; }
        public int getSourceZ() { return sourceZ; }
        public String getDestWorld() { return destWorld; }
        public int getDestX() { return destX; }
        public int getDestY() { return destY; }
        public int getDestZ() { return destZ; }
    }
}
