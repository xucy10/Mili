package fun.bm.mili.portal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
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
        // Mili start - fix: catch Throwable for robustness during load
        } catch (Throwable e) {
        // Mili end
            Bukkit.getLogger().log(Level.WARNING, "[Mili Portal] Failed to load portal links", e);
        }
    }

    // Mili start - fix: atomic file save via temp file + rename
    public static synchronized void save() {
        if (dataFile == null) return;
        File tmpFile = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmpFile), StandardCharsets.UTF_8)) {
            GSON.toJson(registry, writer);
        } catch (Throwable e) {
            Bukkit.getLogger().log(Level.WARNING, "[Mili Portal] Failed to save portal links", e);
            return;
        }
        if (dataFile.exists() && !dataFile.delete()) {
            Bukkit.getLogger().warning("[Mili Portal] Could not delete old portal_links.json");
            tmpFile.delete();
            return;
        }
        if (!tmpFile.renameTo(dataFile)) {
            Bukkit.getLogger().warning("[Mili Portal] Could not rename temp file to portal_links.json");
            tmpFile.delete();
        }
    }
    // Mili end

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
        // Mili start - fix: null checks for world to prevent NPE
        if (!enabled) return;
        World srcWorld = source.getWorld();
        World destWorld = destination.getWorld();
        if (srcWorld == null || destWorld == null) {
            Bukkit.getLogger().warning("[Mili Portal] Cannot register pair: source or destination world is null");
            return;
        }
        String key = locationKey(source);
        PortalPair pair = new PortalPair(
                srcWorld.getName(), source.getBlockX(), source.getBlockY(), source.getBlockZ(),
                destWorld.getName(), destination.getBlockX(), destination.getBlockY(), destination.getBlockZ()
        );
        // Mili end
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

    public static Location findNearestPortal(World world, int expectedX, int expectedZ) {
        int radius = searchRadius;
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int bx = expectedX + dx;
                    int bz = expectedZ + dz;
                    for (int y = -64; y < 320; y++) {
                        Block block = world.getBlockAt(bx, y, bz);
                        if (block.getType() == Material.NETHER_PORTAL) {
                            return block.getLocation();
                        }
                    }
                }
            }
        }
        return null;
    }

    public static Location calculateDestination(Location source) {
        World world = source.getWorld();
        if (world == null) return null;
        String worldName = world.getName();

        boolean toNether = !worldName.contains("nether");
        boolean toOverworld = worldName.contains("nether");

        if (!toNether && !toOverworld) return null;

        World destWorld;
        if (toNether) {
            destWorld = Bukkit.getWorld(worldName + "_nether");
            if (destWorld == null) {
                for (World w : Bukkit.getWorlds()) {
                    if (w.getName().contains("nether")) { destWorld = w; break; }
                }
            }
        } else {
            String owName = worldName.replace("_nether", "");
            destWorld = Bukkit.getWorld(owName);
            if (destWorld == null) {
                for (World w : Bukkit.getWorlds()) {
                    if (!w.getName().contains("nether") && !w.getName().contains("the_end")) {
                        destWorld = w; break;
                    }
                }
            }
        }

        if (destWorld == null) return null;

        double destX, destZ;
        if (toNether) {
            destX = source.getX() / 8.0;
            destZ = source.getZ() / 8.0;
        } else {
            destX = source.getX() * 8.0;
            destZ = source.getZ() * 8.0;
        }

        Location expected = new Location(destWorld, destX, source.getY(), destZ);

        Location nearest = findNearestPortal(destWorld, (int) Math.floor(destX), (int) Math.floor(destZ));
        if (nearest != null) {
            return nearest;
        }

        return expected;
    }

    // Mili start - fix: null check for world to prevent NPE
    public static String locationKey(Location loc) {
        World w = loc.getWorld();
        if (w == null) throw new IllegalArgumentException("Location has no world");
        return w.getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
    // Mili end

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

        // Mili start - fix: null check for world to prevent NPE in matchesSource
        public boolean matchesSource(Location loc) {
            World w = loc.getWorld();
            if (w == null || !w.getName().equals(sourceWorld)) return false;
            // Mili end
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
